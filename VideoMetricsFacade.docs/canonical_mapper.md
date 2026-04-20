# Canonical Mapper

本文档同步 `CanonicalStatMapper` 当前实现，只描述已经落地的 Web detail -> Canonical 映射行为。

## 来源与入口

- 来源 ID：`SRC-WEB-DETAIL`
- 输入模型：
  - `dev.aaa1115910.biliapi.http.entity.video.VideoDetail`
  - `dev.aaa1115910.biliapi.http.entity.video.VideoInfo`
- 映射器：`dev.aaa1115910.biliapi.metrics.CanonicalStatMapper`

`fromWebDetail(...)` 只是把 `detail.view` 转交给 `fromWebView(...)`；真正映射逻辑都在 `fromWebView(...)` 与 `buildCanonicalStat(...)`。

## 字段映射

| 来源字段 | Canonical 字段 | 当前实现 |
| --- | --- | --- |
| `view.stat.rawView` | `CanonicalStat.view` | 使用 `Long` 原值，禁止走 `view.stat.view` 的 `Int.MIN_VALUE` 哨兵 getter。 |
| `view.stat.danmaku` | `CanonicalStat.danmaku` | 非负 `Int -> Long`。 |
| `view.stat.reply` | `CanonicalStat.reply` | 非负 `Int -> Long`。 |
| `view.stat.favorite` | `CanonicalStat.favorite` | 非负 `Int -> Long`。 |
| `view.stat.coin` | `CanonicalStat.coin` | 非负 `Int -> Long`。 |
| `view.stat.share` | `CanonicalStat.share` | 非负 `Int -> Long`。 |
| `view.stat.like` | `CanonicalStat.like` | 非负 `Int -> Long`。 |
| `view.duration` | `CanonicalStat.durationSec` | `Int` 输入：非负则保留；负数则置 `null`（由 `parseDurationSec` 统一约束）。 |
| 固定 `null` | `CanonicalStat.isVipVideo` | Mapper 只消费 Web detail，不直接拉播放权限，因此此阶段固定为 `null`。 |
| `view.isChargeableSeason` / `view.rights.pay` / `view.rights.ugcPay` / `view.rights.arcPay` | `CanonicalStat.isPaidVideo` | 此阶段写入原始付费标记；是否需要按 VIP 互斥收口，由 Facade 在补齐播放权限后统一处理。 |
| `view.dimension.width` / `view.dimension.height` | `CanonicalStat.isVerticalVideo` | 稿件级方向判断，`width < height` 时为 `true`，不按 `cid` 读取分P。 |
| 固定 `CanonicalSource.DETAIL_SUPPLEMENT` | `CanonicalStat.source` | Mapper 阶段尚未回填 `API` / `CACHE`。 |
| `updatedAt` 参数 | `CanonicalStat.updatedAt` | 默认 `System.currentTimeMillis()`。 |
| `cacheStatus` 参数 | `CanonicalStat.cacheStatus` | 默认 `MISS`。 |
| 固定 `null` | `CanonicalStat.ttlMs` | Mapper 不负责 fresh 语义。 |
| 固定 `null` | `CanonicalStat.expireAt` | Mapper 不负责 fresh 语义。 |
| 固定 `null` | `CanonicalStat.nextRefreshAt` | Mapper 不负责调度。 |
| `refreshReason` 参数 | `CanonicalStat.refreshReason` | 默认 `DETAIL_SUPPLEMENT`。 |
| 固定 `null` | `CanonicalStat.fieldSources` | 当前单来源映射不拆字段级来源。 |
| 固定 `SRC-WEB-DETAIL` | `StatEnvelope.sourceId` | 稳定来源标识。 |
| `view.aid` / `view.bvid` / `view.cid` | `StatEnvelope` 标识字段 | 原样透传。 |

注：duration 的解析统一走 `parseDurationSec(...)`，以保证不同来源（未来可能出现 `Long`/`String`）时行为一致。

注：`isVipVideo`、`isPaidVideo` 与 `isVerticalVideo` 是稿件级布尔属性；空降级快照中由 Facade 填为 `null`，表示未知。

## 精度规则

### 结构化数值

- `Byte` / `Short` / `Int` / `Long`：只要非负，均视为精确值。
- `BigDecimal`：
  - 先截断到整数；
  - 只有发生截断时才记为近似值；
  - 因此 `123` 是 `EXACT`，`1.2` 才是 `APPROX`。

### 文案字符串

当前实现支持：

- `1234`
- `2.399万`
- `1.2亿`
- `2.399万播放`
- `1,234`

当前实现不支持：

- 无单位小数字符串，例如 `2.3`、`3.0`

对于带 `万` / `亿` 的字符串：

- 先乘以单位；
- 再按 `RoundingMode.DOWN` 截断；
- 只要成功走了单位换算，当前实现都会记为 `APPROX`。

## `precision` 聚合逻辑

`buildCanonicalStat(...)` 的聚合规则是：

- 任一 count 字段 `approximate = true` -> `CanonicalPrecision.APPROX`
- 否则只要至少存在一个非空统计值或时长 -> `CanonicalPrecision.EXACT`
- 否则 -> `CanonicalPrecision.UNKNOWN`

## 播放数硬约束

当前代码明确把播放数输入写死为 `view.stat.rawView`，并由测试验证：

- 原始 `VideoStat.view` getter 可以返回 `Int.MIN_VALUE`
- Canonical 输出仍必须保留正确的 `Long` 播放数

因此任何文档如果仍写“Canonical 读取 `VideoStat.view`”都已经过时。

## 当前边界

- Mapper 只做字段规范化，不做缓存、不做 limiter、不做 cooldown。
- Mapper 不回填 fresh TTL；fresh 元信息由 Facade 成功拉网后统一回填。
- 当前 `fieldSources` 恒为 `null`，因为没有字段级多来源合并。
- Mapper 不直接判断 VIP；`isVipVideo` 的最佳努力补齐发生在 Facade 侧。
- `isVerticalVideo` 不表达当前 `cid` 对应分P方向，只表达 Web detail `View.dimension` 的稿件级方向。

## Code References

- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/CanonicalStatMapper.kt:22`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/CanonicalStatMapper.kt:44`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/CanonicalStatMapper.kt:52`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/CanonicalStatMapper.kt:96`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/CanonicalStatMapper.kt:128`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoAccessClassifier.kt:3`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/http/entity/video/VideoInfo.kt:259`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/CanonicalStatMapperTest.kt:80`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/CanonicalStatMapperTest.kt:97`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/CanonicalStatMapperTest.kt:110`
