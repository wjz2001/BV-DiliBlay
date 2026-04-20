# Video Metrics Facade Design

本文档不再描述“拟议方案”，而是同步当前仓库已经落地的 Facade 形态。

## 当前目标与边界

- 统一对外输出 `VideoMetricsEnvelope`
- `snapshot` 直接复用 `CanonicalMetricsSnapshot`（即 `CanonicalStat`）
- `snapshot` 除统计值外，当前也包含 `isVipVideo` / `isPaidVideo` / `isVerticalVideo` 三个稿件级布尔属性
- `runtime` 提供 `contextKey` / `statKey` / `aliasKey` / batch / degraded 等运行时信息
- 数据源固定为 `SRC-WEB-DETAIL`
- 不做 Web / gRPC 多源互备

## 已落地 Public API

当前仓库内正式 API 位于 `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacade.kt`：

- `VideoMetricsRequest`
- `VideoMetricsPriority`
- `VideoMetricsIdentity`
- `VideoMetricsRuntimeMeta`
- `VideoMetricsBatchGroup`
- `VideoMetricsEnvelope`
- `VideoMetricsPrefetchOptions`
- `VideoMetricsFacadeConfig`
- `VideoMetricsFacade`

和早期设计稿相比，现状差异包括：

- 没有单独定义 `VideoMetricsSource` / `VideoMetricsPrecision` / `VideoMetricsCacheStatus` / `VideoMetricsRefreshReason`；
- 当前直接复用 Canonical 枚举；
- `snapshot` 不是另一套 DTO，而是 `CanonicalMetricsSnapshot`。

## 请求与 key 语义

### `VideoMetricsRequest`

- 只要 `aid` 或 `bvid` 至少一个非空即可。
- `cid` 是可选上下文字段，不参与 `statKey`。
- `allowStale` 默认 `true`。
- `priority` 默认 `VISIBLE`。
- `refreshReason` 默认 `INITIAL_LOAD`。

### key 体系

必须以 `VideoMetricsKeys` 为准：

- `statKey(aid)`：稿件级 key，不含 `cid`
- `contextKey(request)`：上下文级 key，可含 `cid`
- `reqBvidKey(normalizedBvid)`：仅带 `bvid` 请求的合并辅助 key
- `aliasKey(normalizedBvid)`：`bvid -> aid` 的运行时别名 key
- `deferredPrefetchKey(request)`：延迟预取队列去重 key，可含 `cid`

## 运行时元信息

`VideoMetricsRuntimeMeta` 当前字段含义：

| 字段 | 当前实现语义 |
| --- | --- |
| `sourceId` | 当前固定为 `SRC-WEB-DETAIL` |
| `contextKey` | 调用上下文 key，可含 `cid` |
| `statKey` | 稿件级 key，不含 `cid` |
| `aliasKey` | 当请求携带 `bvid` 时回填对应 alias key |
| `inFlightShared` | 当前请求是否复用到已有 in-flight |
| `degraded` | 是否走 stale/empty 降级 |
| `batchGroup` | `VISIBLE -> INTERACTIVE`；`PREFETCH/BACKGROUND -> DEFERRED` |
| `latencyMs` | 当前调用耗时 |
| `failureCode` / `failureMessage` | 失败与 cooldown 观测信息 |

## 缓存与 fresh 语义

当前实现配置位于 `VideoMetricsFacadeConfig`：

- `cacheTtlMs = 120_000L`
- `staleMaxAgeMs = 600_000L`
- `maxDegradeAgeMs = 1_800_000L`
- `nextRefreshOffsetMs = 90_000L`
- `maxPrefetchRequests = 60`
- `rateLimitCooldownMs = 30_000L`

语义必须区分：

- `ttlMs` / `expireAt`：snapshot 中的 fresh 窗口
- `staleMaxAgeMs`：允许 stale immediate return 的内部窗口
- `maxDegradeAgeMs`：失败兜底窗口
- `nextRefreshAt`：建议预刷新时间

## 当前实现的加载行为

### `load()`

1. 尝试 fresh cache 命中
2. 若 `allowStale = true`，尝试 stale immediate return + 异步刷新
3. 否则进入 singleflight
4. 真正发网前先检查 cooldown
5. 发网成功后回填 fresh 元信息并写入缓存
6. 发网失败时返回 stale 或 empty 降级

### cooldown 作用域

当前 `HTTP_412` / `HTTP_429` cooldown 已按请求作用域隔离：

- 优先使用稿件级 `statKey(aid)`，避免不同稿件互相影响；
- 仅有 `bvid` 且未解析到 `aid` 时，使用 `reqBvidKey(normalizedBvid)`；
- 同一次请求同时带 `aid` 与 `bvid` 时，会同时记录相关 scope，保证后续同稿件 `aid` / `bvid` 入口共享 cooldown。

### `prefetch()`

- 只立即执行前 `firstScreenCount` 个 `VISIBLE`
- 超出的 `VISIBLE` 自动降级为 `PREFETCH`
- deferred 队列使用 debounce 合并
- drain 阶段按 `deferredBatchSize` 分批
- 每批之间可插入 `interBatchDelayMs`

## rawView 与 BigDecimal 规则

- Canonical `view` 必须来自 `VideoStat.rawView`
- `BigDecimal` 是否记为 `APPROX`，以“是否发生截断”为准
- 无单位十进制字符串不会被接受为合法统计值

## 稿件级属性规则

- `isVipVideo` 由 Facade 在拿到 Web detail 后，最佳努力补一次播放权限并从 `supportFormats.needVip` 推导。
- `isPaidVideo` 先沿用 Web detail 的原始付费标记，再按 `isVipVideo == true -> false` 收口成“非 VIP 的付费视频”。
- `isVerticalVideo` 由 Web detail 的 `View.dimension.width < View.dimension.height` 推导。
- 这三个字段都跟随稿件级 `statKey(aid)` 缓存，不表达当前 `cid` 分P状态。

## Code References

- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacade.kt:3`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacade.kt:49`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacade.kt:62`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsKeys.kt:3`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImpl.kt:80`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImpl.kt:172`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImpl.kt:510`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImpl.kt:528`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImpl.kt:562`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImplTest.kt:321`
