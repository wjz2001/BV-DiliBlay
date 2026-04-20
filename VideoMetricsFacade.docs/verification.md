# Verification

本文档记录当前实现对应的可执行验证入口，所有结论均以现有测试为准。

## 覆盖范围

- `CanonicalStatMapper`
- `VideoStatCache`
- `VideoMetricsFacadeImpl`
- `VideoMetricsGlobalConcurrencyLimiter`

## 推荐测试入口

```powershell
.\gradlew.bat :bili-api:test --tests dev.aaa1115910.biliapi.metrics.CanonicalStatMapperTest --tests dev.aaa1115910.biliapi.metrics.VideoStatCacheTest --tests dev.aaa1115910.biliapi.metrics.VideoMetricsFacadeImplTest --no-daemon
```

## 关键验证项

### 1. rawView 溢出哨兵

测试：

- `CanonicalStatMapperTest.from web view keeps raw long view instead of overflow sentinel`

确认：

- `videoInfo.stat.view` 会变成 `Int.MIN_VALUE`
- Canonical `envelope.stat.view` 仍保持真实 `Long`

### 2. BigDecimal approximate 规则

测试：

- `CanonicalStatMapperTest.parse count marks BigDecimal precision only when truncated`

确认：

- `BigDecimal("123")` -> `approximate = false`
- `BigDecimal("1.2")` -> `approximate = true`

### 3. same-key / alias 去重

测试：

- `VideoMetricsFacadeImplTest.load deduplicates same in-flight key`
- `VideoMetricsFacadeImplTest.load deduplicates aid and bvid in-flight aliases`

确认：

- 同一稿件并发请求只发一次真实网络请求
- aid/bvid 可以向同一个 in-flight 收敛

### 4. fresh hit 不触网、不进 limiter

测试：

- `VideoMetricsFacadeImplTest.load returns fresh cache without remote fetch or limiter permit`

确认：

- `remoteFetcher` 调用数为 `0`
- limiter 进入数为 `0`

### 5. stale immediate return

测试：

- `VideoMetricsFacadeImplTest.load returns stale immediately when allowStale is true`

确认：

- 首次返回 `cacheStatus = STALE`
- `runtime.degraded = false`
- 后台刷新仍会实际触发一次 `remoteFetcher`

### 6. global concurrency / visible bypass

测试：

- `VideoMetricsFacadeImplTest.prefetch respects global semaphore concurrency`
- `VideoMetricsFacadeImplTest.visible load bypasses deferred queue saturation`

确认：

- 默认总网络并发上限为 `5`
- deferred 默认最多占用 `4`
- `VISIBLE` 请求可绕过 deferred 饱和

### 7. cooldown

测试：

- `VideoMetricsFacadeImplTest.http 429 cooldown only blocks same aid scope`
- `VideoMetricsFacadeImplTest.http 429 cooldown is shared by aid and bvid aliases of same稿件`

确认：

- 第一次 `429` 后，同一稿件 scope 会进入 cooldown
- 同一 `aid` 再次请求会直接返回 cooldown 降级结果，不再次触发 `remoteFetcher`
- 其他 `aid` 不受该 cooldown 影响
- 同一稿件的 `aid` / `bvid` 入口可以共享 cooldown

## Code References

- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/CanonicalStatMapperTest.kt:12`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/CanonicalStatMapperTest.kt:80`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/VideoStatCacheTest.kt:16`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImplTest.kt:28`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImplTest.kt:185`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImplTest.kt:290`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImplTest.kt:326`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImplTest.kt:348`

## 备用运行方式（避免测试类重命名导致漏测）

当测试类名调整时，推荐用包名通配符运行该模块下的全部 metrics 测试：

```
powershell
.\gradlew.bat :bili-api:test --tests "dev.aaa1115910.biliapi.metrics.*" --no-daemon
```

建议在 CI 中至少运行一次上述通配符命令，确保 metrics 包内新增/重命名的测试不会漏跑。


