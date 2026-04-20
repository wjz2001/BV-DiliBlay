# Canonical Metrics Contract

本文档以当前代码实现为准，定义 `CanonicalStat` / `StatEnvelope` 的稳定契约，并明确“契约层字符串、实现层枚举”的边界。

## 契约级别声明

- 本文档描述的是 **仓库内 Canonical 模型的稳定语义**（internal contract），以当前实现与测试为准。
- 若未来需要对外提供 JSON（或跨语言）序列化接口，本文建议将枚举序列化为稳定字符串；但 **当前仓库并未实现/发布独立的对外 JSON Schema 与版本化契约**。

## 适用范围

- Canonical 主值主要承载规范化后的数值；当前实现额外承载少量稿件级布尔属性，不承载 UI 展示文案。
- **建议**：跨语言/跨进程传输时，将枚举序列化为稳定字符串值（例如 `"API"` / `"CACHE"`）。
  - **现状**：仓库内实现使用 Kotlin `enum class`；对外序列化格式（如需）应另行定义并版本化。
- 当前唯一默认映射来源仍是 `SRC-WEB-DETAIL`，但契约允许缓存与降级状态写入同一结构。
- 本文档只描述代码已实现的行为；未实现能力不会以“设计目标”口径出现。

## 字段契约

| 字段 | Kotlin 实现类型 | 契约序列化类型 | 可空 | 当前实现语义 |
| --- | --- | --- | --- | --- |
| `view` | `Long?` | `number \| null` | 是 | 播放数，Canonical 映射必须读取 `VideoStat.rawView`。 |
| `danmaku` | `Long?` | `number \| null` | 是 | 弹幕数。 |
| `reply` | `Long?` | `number \| null` | 是 | 评论数。 |
| `favorite` | `Long?` | `number \| null` | 是 | 收藏数。 |
| `coin` | `Long?` | `number \| null` | 是 | 投币数。 |
| `share` | `Long?` | `number \| null` | 是 | 分享数。 |
| `like` | `Long?` | `number \| null` | 是 | 点赞数。 |
| `durationSec` | `Int?` | `number \| null` | 是 | 时长，单位秒。 |
| `isVipVideo` | `Boolean?` | `boolean \| null` | 是 | 稿件级是否大会员视频；当前优先由播放权限中的 `supportFormats.needVip` 推导，拿不到播放权限时为 `null`。 |
| `isPaidVideo` | `Boolean?` | `boolean \| null` | 是 | 稿件级是否非 VIP 的付费视频；先由 `is_chargeable_season`、`rights.pay`、`rights.ugc_pay`、`rights.arc_pay` 计算原始付费标记，再按 `isVipVideo == true -> false` 收口为互斥语义。 |
| `isVerticalVideo` | `Boolean?` | `boolean \| null` | 是 | 稿件级是否竖屏视频；当前由 `View.dimension.width < View.dimension.height` 推导，不按 `cid` 区分分P。 |
| `source` | `CanonicalSource` | `string` | 否 | 实现层为枚举，契约层输出稳定字符串。 |
| `updatedAt` | `Long` | `number` | 否 | 当前快照生成时间。 |
| `precision` | `CanonicalPrecision` | `string` | 否 | 数值精度标记。 |
| `cacheStatus` | `CanonicalCacheStatus` | `string` | 否 | fresh/stale/刷新结果状态。 |
| `ttlMs` | `Long?` | `number \| null` | 是 | 仅表示 fresh TTL。 |
| `expireAt` | `Long?` | `number \| null` | 是 | 仅表示 fresh 过期时刻。 |
| `nextRefreshAt` | `Long?` | `number \| null` | 是 | 仅表示建议预刷新时刻。 |
| `refreshReason` | `CanonicalRefreshReason` | `string` | 否 | 本次快照生成/刷新的直接原因。 |
| `fieldSources` | `Map<String, CanonicalSource>?` | `object \| null` | 是 | 当前实现通常为 `null`。 |

## 枚举与序列化边界

当前实现已经将以下元信息建模为 Kotlin `enum class`：

- `CanonicalSource`
- `CanonicalPrecision`
- `CanonicalCacheStatus`
- `CanonicalRefreshReason`

因此文档必须区分两层含义：

- **契约层**：为了 JSON / 调试友好，可序列化为稳定字符串值；
- **实现层**：仓库当前代码直接使用枚举，而不是 `String` 字段。

这意味着“string-only 实现”已经过时；正确表述应为“契约 string，仓库实现 enum”。

## 当前枚举值

### `source`

| 值 | 当前含义 |
| --- | --- |
| `API` | Facade 成功完成一次网络拉取，并通过 `asFreshNetworkEnvelope` 回填 fresh 元信息后的结果。 |
| `DETAIL_SUPPLEMENT` | Mapper 直接输出的原始 detail 补齐结果；尚未经过 Facade fresh 元信息回填时使用。 |
| `CACHE` | 从 `VideoStatCache` 读出的结果。 |
| `MIXED` | 当前代码保留该枚举，但现实现未主动产出。 |
| `UNKNOWN` | 空降级或无法判断来源时使用。 |

### `precision`

| 值 | 当前含义 |
| --- | --- |
| `EXACT` | 所有已提供非空统计值都来自结构化整数，或 `BigDecimal`/字符串解析后没有发生截断。 |
| `APPROX` | 任一统计值来自 `万` / `亿` 文案，或 `BigDecimal` 在转整数时发生了截断。 |
| `UNKNOWN` | 所有统计值都不可得或不可解析。 |

### `cacheStatus`

| 值 | 当前含义 |
| --- | --- |
| `MISS` | Mapper 初始输出或空降级输出。 |
| `HIT` | fresh cache 命中，或 `getStaleOk()` 读取到仍处于 fresh 窗口的缓存。 |
| `STALE` | 缓存存在但已过 fresh TTL。 |
| `REFRESHED` | Facade 完成一次成功网络刷新后写回的 fresh 结果。 |
| `UNKNOWN` | 当前实现保留该枚举，主要用于测试输入。 |

### `refreshReason`

| 值 | 当前含义 |
| --- | --- |
| `INITIAL_LOAD` | `VideoMetricsRequest` 默认刷新原因。 |
| `CACHE_MISS` | 可由调用方显式传入。 |
| `CACHE_EXPIRED` | 可由调用方显式传入。 |
| `MANUAL_REFRESH` | `load()` 会绕过 fresh cache 命中短路。 |
| `DETAIL_SUPPLEMENT` | Mapper 默认值。 |
| `RETRY` | 可由调用方显式传入。 |
| `UNKNOWN` | 保留值。 |

## 播放数与溢出哨兵规则

- Canonical 播放数必须读取 `VideoStat.rawView: Long`。
- 严禁读取 `VideoStat.view: Int` 作为 Canonical `view` 输入。
- 原因是 `VideoStat.view` 在 `_view > Int.MAX_VALUE` 时会返回 `Int.MIN_VALUE` 哨兵。
- 该规则已由 `CanonicalStatMapperTest` 覆盖，属于当前实现的硬约束，而不是建议。

## 稿件级布尔属性规则

- `isVipVideo` 当前为稿件级属性，优先来自播放权限：
  - 通过播放接口返回的 `supportFormats.needVip`
  - 只要 `supportFormats` 非空且任一格式 `needVip == true`，则为 `true`
  - 若 `supportFormats` 非空且全部为 `false`，则为 `false`
  - 若当前未拿到播放权限，或播放接口未提供 `supportFormats`，则为 `null`
- `isPaidVideo` 当前为稿件级属性，但语义已收口为“非 VIP 的付费视频”：
  - 先计算原始付费标记：
  - `View.is_chargeable_season == true`
  - 或 `View.rights.pay == 1`
  - 或 `View.rights.ugc_pay == 1`
  - 或 `View.rights.arc_pay == 1`
  - 再按 `isVipVideo == true -> isPaidVideo = false` 处理，保证 `isVipVideo` 与 `isPaidVideo` 互斥
- `isVerticalVideo` 当前为稿件级属性，直接使用 `View.dimension` 判断宽高。
- `isVerticalVideo` 不读取 `pages`，也不按 `cid` 判断当前分P方向。
- 空降级快照中这三个字段为 `null`，表示未知。

## 数值解析规则

### 结构化数值

- `Byte` / `Short` / `Int` / `Long`：非负时直接转为 `Long`，`approximate = false`。
- `BigDecimal`：
  - 先 `setScale(0, RoundingMode.DOWN)`；
  - 若原值与截断后的整数值完全相等，则 `approximate = false`；
  - 若发生截断，则 `approximate = true`；
  - 若结果超出 `Long` 或原值为负，则返回 `null`。

这意味着当前实现下，`BigDecimal("123")` 是 `EXACT`，`BigDecimal("1.2")` 才是 `APPROX`。

### 文案字符串

- 会先移除空白、`,` / `，`、全角小数点，并剥离 `播放` / `观看` / `弹幕` 尾缀。
- 支持：
  - 纯整数字符串，如 `1234`
  - `万` / `亿` 单位，如 `2.399万`、`1.2亿`
- 不支持“无单位十进制字符串”，例如 `2.3`、`3.0`，当前实现会返回 `null`。
- 对 `万` / `亿` 解析：
  - 先用 `BigDecimal` 乘以单位；
  - 再执行 `RoundingMode.DOWN` 截断；
  - 只要带单位成功解析，当前实现都会记为 `approximate = true`。

## 时间字段语义

### Mapper 输出

`CanonicalStatMapper.buildCanonicalStat(...)` 当前固定输出：

- `ttlMs = null`
- `expireAt = null`
- `nextRefreshAt = null`

也就是说，Mapper 本身只负责字段规范化，不定义 fresh 语义。

### Facade fresh 回填

`VideoMetricsFacadeImpl.asFreshNetworkEnvelope(...)` 会在网络成功后统一回填：

- `source = API`
- `cacheStatus = REFRESHED`
- `updatedAt = nowMs`
- `ttlMs = config.cacheTtlMs`
- `expireAt = nowMs + config.cacheTtlMs`
- `nextRefreshAt = nowMs + config.nextRefreshOffsetMs`

因此当前代码中的 fresh 语义是：

- `ttlMs` / `expireAt` 只表示 fresh 窗口；
- `nextRefreshAt` 是建议预刷新时刻；
- `staleMaxAgeMs` / `maxDegradeAgeMs` 仅属于 Facade 内部策略，不写回 Canonical snapshot。

## 代码真相小结

- 契约层可以表现为字符串，但仓库当前实现不是 string-only，而是 enum-backed。
- `ttlMs` / `expireAt` 的语义严格限定为 fresh，不承载 stale / degrade 窗口。
- `BigDecimal` 的 approximate 规则以“是否发生截断”为准。
- `view` 的正确输入源是 `rawView`，不是 `Int` getter。
- `isVipVideo` / `isPaidVideo` / `isVerticalVideo` 是稿件级属性，不表达当前分P状态。

## Code References

- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/CanonicalStat.kt:3`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/CanonicalStat.kt:35`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/CanonicalStatMapper.kt:44`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/CanonicalStatMapper.kt:52`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoAccessClassifier.kt:3`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/http/entity/video/VideoInfo.kt:239`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/http/entity/video/VideoInfo.kt:259`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/CanonicalStatMapper.kt:128`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImpl.kt:510`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/http/entity/video/PlayUrlResponse.kt:346`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/CanonicalStatMapperTest.kt:12`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/CanonicalStatMapperTest.kt:80`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/CanonicalStatMapperTest.kt:97`
