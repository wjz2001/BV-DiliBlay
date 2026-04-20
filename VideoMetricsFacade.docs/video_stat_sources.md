# 视频统计与详情数据源盘点

本文档同步当前代码里与视频统计补齐直接相关的数据源事实，并明确当前 Canonical / Facade 使用的是哪一条链路。

## 当前结论

- Canonical / Facade 当前固定数据源：`SRC-WEB-DETAIL`
- 具体调用：`BiliHttpApi.getVideoDetail(av = ..., bv = ..., sessData = ...)`
- 当前不会做 Web `view` / gRPC `View.view` 互备

## 当前使用链路

```text
VideoMetricsFacadeImpl
  -> BiliHttpApi.getVideoDetail(av = aid, bv = bvid, sessData = ...)
  -> CanonicalStatMapper.fromWebDetail(detail)
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

## Code References

- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/CanonicalStatMapper.kt:22`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/CanonicalStatMapper.kt:40`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/http/entity/video/VideoInfo.kt:239`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/http/entity/video/VideoInfo.kt:259`
- `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/metrics/VideoMetricsFacadeImpl.kt:32`
- `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/metrics/CanonicalStatMapperTest.kt:12`
