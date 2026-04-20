# VideoStatCache Design

本文档同步 `VideoStatCache` 当前实现，描述已落地的 L1 内存缓存行为。

## 范围

- 只实现内存缓存。
- 只缓存 `StatEnvelope` / `CanonicalStat`。
- 支持 `aid` / `bvid` 两类读取入口。
- 提供 `getFreshOrNull`、`getStaleOk`、`put`、`invalidate`、`clear`。
- 允许 `APPROX` 不覆盖已有 `EXACT`。

## Key 体系

缓存内部实际维护两类 key：

- `Aid(value: Long)`
- `Bvid(value: String)`

行为如下：

- `bvid` 会先做 `trim + uppercase` 归一化。
- `put()` 总是写入 `Aid` 主项。
- 如果有 `bvid`，还会额外写入 `Bvid` 项，并记录 `bvidAliasToAid`。
- 因此 `aid` 与 `bvid` 最终会指向同一个 `CacheEntry`。

## 缓存性质（重要）

- 本缓存为 **L1 进程内内存缓存**：仅存在于当前应用进程的内存中。
- **不可持久化**：不会写入磁盘/DB/SharedPreferences。
- **不可跨进程共享**：不同进程间互不可见。
- **重启即失效**：进程重启后缓存会完全丢失，需要重新拉取并回填。

## Entry 结构

缓存项由三部分组成：

- `envelope: StatEnvelope`
- `cachedAt: Long`
- `ttlMs: Long`

并派生出：

- `expireAt = cachedAt + ttlMs`
- `isFresh(nowMs) = nowMs < expireAt`

## 写入行为

`put()` 当前会把传入快照改写为缓存语义：

- `source = CACHE`
- `cacheStatus = HIT`
- `ttlMs = 写入时 ttlMs`
- `expireAt = nowMs + ttlMs`

不会额外改写：

- `updatedAt`
- `nextRefreshAt`
- `refreshReason`

这意味着缓存层只回填 fresh TTL 相关字段，不负责重新定义刷新原因或调度时间。

## 读取行为

### `getFreshOrNull`

- 找到 entry 且 `nowMs < expireAt` 时返回；
- 返回时 `cacheStatus = HIT`；
- 过期则直接返回 `null`。

### `getStaleOk`

- 只要 entry 存在就返回；
- fresh 时回填 `cacheStatus = HIT`；
- 过期后回填 `cacheStatus = STALE`。

注意：`getStaleOk()` 本身不判断 `staleMaxAgeMs` / `maxDegradeAgeMs`。这些窗口由 Facade 在缓存之上再做裁剪。

## 典型时序例子（TTL=1000ms）

- t0=10_000：`put(envelope, ttlMs=1000, nowMs=t0)` -> `expireAt=11_000`
- t0+500=10_500：`getFreshOrNull(nowMs=10_500)` -> 命中，返回 `HIT`
- t0+1500=11_500：`getFreshOrNull(nowMs=11_500)` -> miss；`getStaleOk(nowMs=11_500)` -> 返回 `STALE`

## 精度保护

当前有一条明确保护规则：

- 已有缓存为 `CanonicalPrecision.EXACT`
- 新写入为 `CanonicalPrecision.APPROX`

则保留旧 entry，不允许精度倒退。

除此之外没有字段级 merge；仍然是整条快照替换。

## 失效行为

`invalidate(aid, bvid)` 当前会尽量双向清理：

- 已知 `aid` 时，删除 `Aid(aid)`，并联动删除缓存里的 `bvid` 项；
- 只给 `bvid` 时，会先尝试通过别名索引找到 `aid`，再删除两边入口。

## 与 Facade 的边界

- `VideoStatCache` 只管理 TTL 与 `HIT` / `STALE` 判定。
- `staleMaxAgeMs` / `maxDegradeAgeMs` 不在缓存层实现。
- `nextRefreshAt` 不由缓存层生成。
- 缓存层不会触发后台刷新。

## Code References

- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoStatCache.kt:5`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoStatCache.kt:26`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoStatCache.kt:37`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoStatCache.kt:51`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoStatCache.kt:153`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/VideoStatCacheTest.kt:16`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/VideoStatCacheTest.kt:38`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/VideoStatCacheTest.kt:115`
