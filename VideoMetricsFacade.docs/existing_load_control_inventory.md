# 现有负载控制组件盘点

本文档现仅作为“实现前背景盘点”的历史资料，不再作为当前 Facade 行为的权威来源。

## 当前状态

- 当前仓库已经落地了专用实现：
  - `VideoMetricsFacadeImpl`
  - `VideoMetricsGlobalConcurrencyLimiter`
  - `VideoStatCache`
  - `VideoMetricsKeys`
- 因此本文中的“需要新写”结论仅代表盘点当时的仓库现状，不代表现在仍未实现。

## 仍然有效的背景结论

- 旧仓库里缺少通用 singleflight / 通用全局 limiter / 通用 cooldown 组件；
- 因此最终选择在 metrics 包内实现专用版本；
- UI 层现成的 `DebouncedActivationController` / `TabActivationGuard` 仍可作为接入侧参考，而不是 Facade 内部实现。

## 不再应被直接引用的结论

以下早期结论已经被当前实现覆盖，不能再作为“现状描述”使用：

- “没有全局并发限制器”
- “没有 `412` / `429` cooldown”
- “需要新写 Facade / Cache / Singleflight”

这些能力现在都已经在 `bili-api.metrics` 下落地。

## 当前建议

- 需要描述现行行为时，请优先引用：
  - `docs/load_control_dedup_concurrency.md`
  - `docs/video_metrics_facade_design.md`
  - `docs/scheduler_design.md`
  - `docs/verification.md`

## Code References

- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImpl.kt:28`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsGlobalConcurrencyLimiter.kt:8`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoStatCache.kt:5`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsKeys.kt:3`
- `app/src/main/kotlin/dev/aaa1115910/bv/viewmodel/common/DebouncedActivationController.kt:11`
- `app/src/main/kotlin/dev/aaa1115910/bv/screen/main/common/MainTabActivationSupport.kt:15`
