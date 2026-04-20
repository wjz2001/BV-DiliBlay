# UI Integration Notes

本文档保留 UI 接入建议，但表述以当前 Facade 行为为前提，不再引用过时的调度假设。

## 推荐接入点

- 历史记录页
- 收藏页
- 需要“列表先展示、统计后回填”的视频卡片列表

## 当前接法建议

### 1. 列表先走原链路

- 列表接口先渲染基础卡片信息
- 统计字段允许异步回填
- 不要等待 `VideoMetricsFacade.load()` 成功才显示首屏

### 2. 可见项优先交给 `prefetch()`

- 当前可见卡片可转为 `VideoMetricsRequest(priority = VISIBLE)`
- 统一调用 `videoMetricsFacade.prefetch(...)`
- Facade 会立即处理前 `firstScreenCount` 个 `VISIBLE`

### 3. 非首屏项交给 deferred

- 其余 `PREFETCH` / `BACKGROUND` 请求会进入 deferred 队列
- 队列当前支持 debounce 合并与分批 drain
- 若用户快速滚动，多次 `prefetch()` 会合并成较少的 deferred 网络批次

### 4. 回填粒度

推荐只按 `aid` / `bvid` 回填受影响卡片的统计字段，例如：

- `playString`
- `danmakuString`

不建议为了统计补齐整体重建整页状态。

## UI 需要知道的运行时事实

- `statKey` 不含 `cid`，因此同稿件不同 `cid` 默认共享统计快照
- `contextKey` 可含 `cid`，用于运行时归因
- fresh cache 命中不会触网
- `allowStale = true` 时，stale 结果会先返回，刷新在后台进行

## 验证重点

- 首屏是否先显示，再逐步回填统计
- 同一稿件重复进入可见区时是否优先命中缓存
- 快速滚动时 deferred 队列是否发生 debounce 合并
- `VISIBLE` 请求是否能绕过 deferred 饱和

## Code References

- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacade.kt:3`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacade.kt:55`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsKeys.kt:16`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImpl.kt:86`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImpl.kt:116`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImplTest.kt:221`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImplTest.kt:348`

## 推荐的 UI 失败策略（degraded / failureCode）

UI 展示建议优先使用 `VideoMetricsEnvelope` 的运行时元信息：

- `runtime.degraded`：是否发生降级（使用 stale 或 empty）
- `runtime.failureCode`：失败类型（NETWORK/TIMEOUT/HTTP_429/HTTP_412 等）
- `snapshot.cacheStatus`：HIT/STALE/REFRESHED/MISS
- `snapshot.precision`：EXACT/APPROX/UNKNOWN

### 1. degraded=false（正常路径）
- 正常渲染统计数值。
- 若 `precision=APPROX` 可选择显示“约”（取决于产品文案策略）。

### 2. degraded=true 且 snapshot.cacheStatus=STALE（使用旧值兜底）
- 继续展示旧统计值（避免列表跳变为 `--`）。
- 可选：在 debug 构建或埋点中记录 `failureCode`，线上 UI 通常不必显式提示。
- 可选：若业务需要强调“数据可能旧”，可在不打扰的方式提示（例如小灰点/tooltip），但避免弹 toast。

### 3. degraded=true 且 snapshot.cacheStatus=MISS（空降级）
- 展示占位（`--` / skeleton），但不要阻塞首屏渲染。
- 避免对同一 item 在短时间内疯狂重试；Facade 已实现 cooldown 与全局 limiter，UI 层应配合做去抖/节流。

### 4. HTTP_429 / HTTP_412（限流/风控）
- 建议不主动触发立即重试（Facade 内部有 cooldown 窗口）。
- 若用户显式下拉刷新，可提示“稍后重试”一类的轻提示（可选）。
