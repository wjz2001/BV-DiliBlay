# Load Control: Dedup + Concurrency

本文档同步 `VideoMetricsFacadeImpl` 当前已实现的负载控制行为，重点覆盖去重、缓存、SWR、并发限制、分批与 cooldown。

## 范围

- Facade API：`load()` / `prefetch()` / `invalidate()`
- 共享缓存：`VideoStatCache`
- 共享限流：`VideoMetricsGlobalConcurrencyLimiter`
- key 工具：`VideoMetricsKeys`
- 数据源：固定 `SRC-WEB-DETAIL`

## `MANUAL_REFRESH` 的真实行为（当前实现）

当 `request.refreshReason == MANUAL_REFRESH` 时：

- `load()` **会绕过 fresh cache 短路返回**（即不走 `getFreshOrNull` 的直接返回路径）。
- 但 `load()` **不会自动禁止 stale immediate return**：
  - 若 `allowStale=true` 且存在满足 `staleMaxAgeMs` 的 `STALE` 缓存，当前实现仍可能先返回 stale，并在后台触发刷新。
- 因此若调用方希望“手动刷新必须等待网络结果”，需要同时设置：
  - `refreshReason = MANUAL_REFRESH`
  - `allowStale = false`

## fresh / stale / 降级主路径

### fresh hit

`load()` 先查 `VideoStatCache.getFreshOrNull(...)`：

- 命中 fresh cache 直接返回；
- 不触发网络；
- 不进入 in-flight 注册；
- 不进入全局 limiter。

这是当前实现的硬行为，不是优化建议。

### stale hit（`allowStale = true`）

`load()` 会先取 `getStaleOk(...)`，再额外判断：

- 缓存项状态必须已是 `STALE`
- 且 `startedAt <= updatedAt + staleMaxAgeMs`

满足时会：

- 立即返回 stale snapshot；
- `runtime.degraded = false`；
- 异步触发 `triggerRefreshIfNeeded(request)`；
- 后台刷新继续复用 in-flight 与全局 limiter。

### stale / 网络失败降级

真正发网前，`fetchAndCache()` 还会再读一次可降级 stale：

- 只要 `nowMs <= updatedAt + maxDegradeAgeMs` 就可作为失败兜底；
- 若失败时没有可降级 stale，则返回 empty envelope。

## singleflight 合并逻辑

### 合并 key

当前 `load()` / `triggerRefreshIfNeeded()` / `fetchAndCache()` 使用 `resolveLoadKeys(request)` 生成合并 key 集合：

- 有 `aid` 时：`statKey(aid)`
- 有 `bvid` 时：
  - 若 `aliasIndex` 已解析到 `aid`，再加入对应 `statKey(aid)`
  - 一定加入 `reqBvidKey(normalizedBvid)`

因此当前 in-flight 合并并不是只按一个 key：

- 同 aid 请求会在 `statKey` 上合并；
- 只带 bvid 的请求会在 `reqBvidKey` 上合并；
- 当 alias 已建立后，aid / bvid 请求还能继续向同一个 `statKey` 收敛。

### 合并语义

- `singleFlightMutex` 只保护 map 注册与回收；
- 真正网络 I/O 在锁外执行；
- caller 取消不会直接取消共享的 detached 任务；
- 任务完成后通过 `removeInFlight()` 清理所有映射到该 `Deferred` 的 key。

## Key 体系与 `cid` 归因

所有 `"video_metrics:"` 前缀规则都应以 `VideoMetricsKeys` 为准：

| key | 当前实现 | `cid` 是否参与 |
| --- | --- | --- |
| `statKey(aid)` | `video_metrics:stat:v1:src-web-detail:aid:<aid>` | 否 |
| `contextKey(request)` | `video_metrics:req:aid:<aid>:cid:<cid>` 或 `video_metrics:req:bvid:<bvid>:cid:<cid>` | 是 |
| `reqBvidKey(bvid)` | `video_metrics:req:bvid:<bvid>` | 否 |
| `aliasKey(bvid)` | `video_metrics:alias:bvid:<bvid>` | 否 |
| `deferredPrefetchKey(request)` | `video_metrics:deferred:aid:<aid>:cid:<cid>` 或 `...:bvid:<bvid>:cid:<cid>` | 是 |

结论：

- `statKey` 是稿件级，不含 `cid`。
- `contextKey` 是调用上下文级，可带 `cid`。
- `deferredPrefetchKey` 是延迟预取队列去重键，也可带 `cid`。

## 全局 limiter 语义

`VideoMetricsGlobalConcurrencyLimiter` 当前实现的是“双信号量”模型：

- 总并发默认 `5`
- deferred 并发默认 `4`
- 预留 `1` 个 `VISIBLE` 槽位

占用规则：

- `VISIBLE`：只占总并发配额
- `PREFETCH` / `BACKGROUND`：同时占 deferred 配额与总并发配额

这意味着：

- 延迟任务不能把全部网络槽位占满；
- 新来的 `VISIBLE` 请求可以绕过 deferred 饱和；
- 已经开始执行的 deferred 请求不会被抢占取消。

## `prefetch()` 当前实现

### immediate 阶段

- `acceptedRequests = requests.take(maxPrefetchRequests)`
- 只有前 `firstScreenCount` 个 `VISIBLE` 请求会进入 immediate
- 超出的 `VISIBLE` 会被降级为 `PREFETCH` 并进入 deferred
- 其他 `PREFETCH` / `BACKGROUND` 直接进入 deferred

immediate 当前通过 `runLimited(...)` 并发调用 `load(request)`。

注意这里的实现现状是：

- immediate **确实使用了** `runLimited(...)`
- 但真正的网络并发上限仍由每个 `load()` 内部的 global limiter 决定

### deferred 阶段

延迟预取采用：

- `deferredPrefetchKey(request)` 去重合并 pending
- 每次新调度都会取消前一个尚未触发的 debounce job
- `delay(deferredStartDelayMs)` 后执行 drain
- `drainDeferredPrefetches()` 按 `deferredBatchSize` 分批
- 每批之间再等待 `interBatchDelayMs`

deferred drain 的实现现状同样是：

- **每一批也使用 `runLimited(...)`**
- 但网络并发配额仍以 global limiter 为最终约束

因此如果旧文档写“deferred drain 不走 `runLimited`”，那已经不符合当前代码。

## cooldown 行为

当前 cooldown 是 Facade 内部已落地能力，不再是“未来设计”：

- `Throwable -> failureCode` 会把 `412` / `429` 映射为 `HTTP_412` / `HTTP_429`
- `recordCooldownIfNeeded()` 只对这两类 failureCode 记录 cooldown
- `currentCooldown()` 命中后，后续请求会直接跳过网络，返回 stale 或 empty 降级结果

当前 cooldown 的粒度是：

- **按请求作用域 + failureCode 隔离**
- 作用域优先使用稿件级 `statKey(aid)`
- 仅有 `bvid` 且尚未解析到 `aid` 时，使用 `reqBvidKey(normalizedBvid)`
- 当同一次请求同时带 `aid` 与 `bvid` 时，会同时记录相关 scope，使后续 `aid` / `bvid` 入口能共享同稿件 cooldown

## 可执行证据

- rawView：`CanonicalStatMapperTest.from web view keeps raw long view instead of overflow sentinel`
- BigDecimal：`CanonicalStatMapperTest.parse count marks BigDecimal precision only when truncated`
- same-key dedup：`VideoMetricsFacadeImplTest.load deduplicates same in-flight key`
- aid/bvid alias dedup：`VideoMetricsFacadeImplTest.load deduplicates aid and bvid in-flight aliases`
- global concurrency：`VideoMetricsFacadeImplTest.prefetch respects global semaphore concurrency`
- visible bypass：`VideoMetricsFacadeImplTest.visible load bypasses deferred queue saturation`
- stale immediate return：`VideoMetricsFacadeImplTest.load returns stale immediately when allowStale is true`
- cooldown 隔离：`VideoMetricsFacadeImplTest.http 429 cooldown only blocks same aid scope`
- cooldown alias 共享：`VideoMetricsFacadeImplTest.http 429 cooldown is shared by aid and bvid aliases of same稿件`

## Code References

- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsKeys.kt:3`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImpl.kt:80`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImpl.kt:130`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImpl.kt:172`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImpl.kt:251`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImpl.kt:654`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImpl.kt:687`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsGlobalConcurrencyLimiter.kt:21`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImplTest.kt:28`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImplTest.kt:290`

# Load Control: Dedup + Concurrency + Prefetch Scheduler

本文档同步 `prefetch()` 当前批调度实现，不再保留早期“可能方案”表述。

## 当前默认参数

`VideoMetricsPrefetchOptions` 当前默认值为：

- `firstScreenCount = 15`
- `deferredBatchSize = 6`
- `deferredStartDelayMs = 500L`
- `interBatchDelayMs = 250L`

## immediate / deferred 切分规则

`prefetch(requests, options)` 先执行：

- `acceptedRequests = requests.take(maxPrefetchRequests)`

然后按顺序扫描每个请求：

- `VISIBLE` 且 `visibleBudget > 0`：
  - 进入 immediate
  - 强制 `allowStale = true`
- `VISIBLE` 但 `visibleBudget == 0`：
  - 降级为 `PREFETCH`
  - 进入 deferred
- `PREFETCH` / `BACKGROUND`：
  - 进入 deferred
  - 同样强制 `allowStale = true`

因此“前 `15` 个立即执行”并不完全准确，当前实现更精确的说法是：

- **前 `firstScreenCount` 个 `VISIBLE` 请求立即执行**

## immediate 执行方式

immediate 使用：

- `runLimited(items = immediate, maxParallelism = min(immediate.size, globalLimiter.maxConcurrency()))`

但这只是外层协程分块；
真正网络并发上限仍由每个 `load()` 内部的 `VideoMetricsGlobalConcurrencyLimiter` 决定。

## deferred 队列与 debounce

当前 deferred 调度是“单 pending 队列 + 可重启 debounce”：

1. 用 `deferredPrefetchKey(request)` 合并新请求
2. 记录 `added` / `merged` / `pendingBefore` / `pendingAfter`
3. 若已有尚未触发的 `deferredPrefetchJob`，则先取消
4. 新建一个延迟 `deferredStartDelayMs` 的 job
5. 延迟结束后统一 drain 当前 pending 队列

注意：

- 已经开始执行的 `load()` 不会被取消；
- 只会取消“尚未开始发网”的 deferred debounce job。

## drain 行为

`drainDeferredPrefetches(...)` 当前会：

- 取出 pending 队列
- 按 `sequence` 保持进入顺序
- 按 `deferredBatchSize` 分批
- 每批执行一次 `runLimited(...)`
- 非最后一批之间等待 `interBatchDelayMs`

因此当前实现同时具备：

- debounce 合并
- 分批 drain
- 批间延迟

## 与 limiter 的关系

调度层不会绕过 limiter：

- immediate 的每个 `load()` 最终仍经过 global limiter
- deferred drain 的每个 `load()` 也同样经过 global limiter
- `VISIBLE` 能绕过 deferred 饱和，依靠的是 limiter 的双配额，而不是调度层主动抢占

## Code References

- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacade.kt:55`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImpl.kt:172`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImpl.kt:226`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImpl.kt:365`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImpl.kt:427`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImplTest.kt:221`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImplTest.kt:348`


