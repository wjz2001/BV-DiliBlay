# 视频统计与详情数据源盘点

变更记录：
- `2026-06-02`：播放权限补齐不再是默认链路；只有 `includePlaybackAccessFlags = true` 时才会额外访问 Web 播放接口。

本文档同步当前代码里与视频统计补齐直接相关的数据源事实，并明确当前 Canonical / Facade 使用的是哪一条链路。

## 当前结论

- Canonical / Facade 当前统计与详情主数据源：`SRC-WEB-DETAIL`
- 具体调用：`BiliHttpApi.getVideoDetail(av = ..., bv = ..., sessData = ...)`
- 当前不会做 Web `view` / gRPC `View.view` 互备
- 默认不会调用 Web 播放接口
- 只有 `includePlaybackAccessFlags = true` 时，访问状态补齐才会额外调用 Web 播放接口；当前只覆盖 UGC 付费、PGC 付费，不覆盖 PUGV/课程

## 当前使用链路

```text
VideoMetricsFacadeImpl
  -> BiliHttpApi.getVideoDetail(av = aid, bv = bvid, sessData = ...)
  -> CanonicalStatMapper.fromWebDetail(detail)
  -> [optional] BiliHttpApi.getVideoPlayUrl(...) 或 BiliHttpApi.getPgcVideoPlayUrlV2(...)
  -> [optional] VideoAccessClassifier.resolveAccessFlags(...)
  -> VideoStatCache.put(...)
```

## 关键字段事实

### Web detail / `VideoInfo.stat`

Canonical 当前映射字段如下：

| Canonical 字段 | 当前代码读取位置 | 说明 |
| --- | --- | --- |
| `view` | `view.stat.rawView` | 使用 `Long` 原值，禁止读取 `view.stat.view`。 |
| `danmaku` | `view.stat.danmaku` | `Int -> Long`。 |
| `reply` | `view.stat.reply` | `Int -> Long`。 |
| `favorite` | `view.stat.favorite` | `Int -> Long`。 |
| `coin` | `view.stat.coin` | `Int -> Long`。 |
| `share` | `view.stat.share` | `Int -> Long`。 |
| `like` | `view.stat.like` | `Int -> Long`。 |
| `durationSec` | `view.duration` | 秒级整数。 |

### 访问状态补齐字段

Mapper 阶段只从 Web detail 计算原始付费标记；Facade 只有在显式开启播放权限补齐时，才会根据播放接口补齐 VIP 与已购状态。

| 字段 | 当前代码读取位置 | 说明 |
| --- | --- | --- |
| 原始付费标记 | `view.isChargeableSeason` / `view.rights.pay` / `view.rights.ugcPay` / `view.rights.arcPay` | 由 `VideoAccessClassifier.rawPaidVideo(...)` 计算。 |
| `isVipVideo` | `supportFormats.needVip` | Web UGC 与 Web PGC 都从播放接口 `supportFormats` 推导。 |
| UGC 已购状态 | `PlayUrlData.hasPaid` | 来源为 `BiliHttpApi.getVideoPlayUrl(...)`。 |
| PGC 已购状态 | `PlayUrlV2Data.videoInfo.hasPaid` / `payInfo.payPackPaid` | 来源为 `BiliHttpApi.getPgcVideoPlayUrlV2(...)`。 |

最终 `isPaidVideo` 的收口规则由 `VideoAccessClassifier.resolveAccessFlags(...)` 统一处理：

- `isVipVideo == true` 时为 `false`
- `hasPaid == true` 时为 `false`
- 其他情况保留原始付费标记

### rawView 哨兵问题

`VideoStat` 当前实现中：

- `_view` 实际保存为 `Long`
- `rawView` 对外暴露真实 `Long`
- `view` getter 若超过 `Int.MAX_VALUE` 会返回 `Int.MIN_VALUE`

因此：

- 业务展示层如继续用旧 getter，需要自行处理哨兵；
- Canonical 层已经明确规避该问题，必须继续使用 `rawView`。

## 其他来源现状

### Web `GET /x/web-interface/view`

- 代码仓库仍存在该接口调用；
- 但当前 Facade / Canonical 并不使用它作为统计补齐来源。

### gRPC `View.view`

- 仓库仍有相关 proto 与 repository 使用；
- 但当前 Facade / Canonical 也不使用它作为统计补齐来源。

### App 播放接口

- `PlaybackAccessSource` 已预留 `APP_UGC` / `APP_PGC`；
- 当前 `VideoMetricsFacadeImpl` 未接入 App 播放接口；
- 因此 App 侧字段不会参与 `isVipVideo` / `isPaidVideo` 的当前收口。

## Code References

- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/CanonicalStatMapper.kt:22`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/CanonicalStatMapper.kt:40`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoAccessClassifier.kt:6`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImpl.kt:516`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/http/entity/video/VideoInfo.kt:239`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/http/entity/video/VideoInfo.kt:259`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImpl.kt:32`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/http/entity/video/PlayUrlResponse.kt:104`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/CanonicalStatMapperTest.kt:12`
