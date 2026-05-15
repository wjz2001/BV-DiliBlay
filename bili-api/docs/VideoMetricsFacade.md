# VideoMetricsFacade

`VideoMetricsFacade` 用于统一提供视频卡片、详情页等轻量展示场景所需的核心指标，避免 UI 层分别请求详情接口后再各自解析播放数、时长、付费/VIP/竖屏等状态。

---

## 目标

当前 facade 的职责主要包括：

1. 对外暴露统一的轻量指标读取接口
2. 统一 `aid` / `bvid` 的请求去重与缓存
3. 在远端请求失败时，优先返回可接受的 stale 数据
4. 为首屏可见视频优先取数，并对预取请求做延迟分批调度
5. 补齐详情页原始字段无法直接表达的播放权限信息

---

## 核心类型

### `VideoMetricsRequest`

请求入参，至少需要提供 `aid` 或 `bvid` 之一。

| 字段 | 类型 | 含义 | 备注 |
| ---- | ---- | ---- | ---- |
| `aid` | `Long?` | 稿件 aid | 与 `bvid` 二选一即可 |
| `bvid` | `String?` | 稿件 bvid | 与 `aid` 二选一即可 |
| `cid` | `Long?` | 分 P cid | 非必要 |
| `refreshReason` | `CanonicalRefreshReason` | 刷新原因 | `MANUAL_REFRESH` 时会跳过 fresh cache |
| `allowStale` | `Boolean` | 是否允许返回 stale cache | 默认为 `true` |
| `priority` | `VideoMetricsPriority` | 请求优先级 | `VISIBLE` / `PREFETCH` / `BACKGROUND` |
| `timeoutMs` | `Long?` | 单次加载超时 | 默认 `1500ms` |

### `VideoMetricsEnvelope`

facade 的统一返回对象。

| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| `identity` | `VideoMetricsIdentity` | 当前命中的视频身份信息 |
| `snapshot` | `CanonicalMetricsSnapshot` | 轻量指标快照 |
| `runtime` | `VideoMetricsRuntimeMeta` | 本次加载的运行时元信息 |

### `VideoMetricsRuntimeMeta`

用于补充说明这次结果是如何得到的。

| 字段 | 含义 | 备注 |
| ---- | ---- | ---- |
| `sourceId` | 数据来源标识 | 当前 Web 详情映射来源为 `SRC-WEB-DETAIL` |
| `contextKey` | 当前请求 key | 由 `aid` / `bvid` / `cid` 归一生成 |
| `statKey` | 快照 key | 用于缓存与结果跟踪 |
| `aliasKey` | 别名 key | 常用于 `bvid` 归一与别名命中 |
| `inFlightShared` | 是否复用了进行中的请求 | `true` 表示 single-flight 命中 |
| `degraded` | 是否发生降级 | 例如网络失败后返回 stale / empty |
| `batchGroup` | 请求批次分组 | `INTERACTIVE` 或 `DEFERRED` |
| `latencyMs` | 本次加载耗时 | 毫秒 |
| `failureCode` | 失败分类 | 仅降级时可能存在 |
| `failureMessage` | 失败信息 | 仅降级时可能存在 |

### `VideoMetricsPrefetchOptions`

控制预取行为。

| 字段 | 含义 | 默认值 |
| ---- | ---- | ---- |
| `firstScreenCount` | 首屏优先处理数量 | `15` |
| `deferredBatchSize` | 延后批次大小 | `6` |
| `deferredStartDelayMs` | 延后批次启动延时 | `500ms` |
| `interBatchDelayMs` | 批次间延时 | `250ms` |

### `VideoMetricsFacadeConfig`

控制缓存、降级与预取上限。

| 字段 | 含义 | 默认值 |
| ---- | ---- | ---- |
| `cacheTtlMs` | fresh cache TTL | `120000ms` |
| `staleMaxAgeMs` | stale 可接受最大年龄 | `600000ms` |
| `maxDegradeAgeMs` | 降级可接受最大年龄 | `1800000ms` |
| `nextRefreshOffsetMs` | 下次建议刷新偏移 | `90000ms` |
| `maxPrefetchRequests` | 单次最多接受的预取请求数 | `60` |
| `rateLimitCooldownMs` | 限流冷却时间 | `30000ms` |

---

## 输出字段说明

当前 facade 主要输出以下轻量指标：

| 字段 | 含义 |
| ---- | ---- |
| `view` | 播放数 |
| `danmaku` | 弹幕数 |
| `reply` | 评论数 |
| `favorite` | 收藏数 |
| `coin` | 投币数 |
| `share` | 分享数 |
| `like` | 点赞数 |
| `durationSec` | 时长，单位秒 |
| `isVipVideo` | 是否需要会员能力 |
| `isPaidVideo` | 是否为付费/未解锁内容 |
| `isVerticalVideo` | 是否为竖屏视频 |

---

## 判定规则

### `isVerticalVideo`

当前直接由详情接口中的宽高决定：

- `dimension.width < dimension.height` 时视为竖屏视频

### `isPaidVideo`

该字段不是单纯读取详情页单一字段，而是面向 UI 警告/角标场景做的统一解释结果。

原始付费信号主要来自详情页中的以下信息：

- `rights.pay`
- `rights.ugcPay`
- `rights.arcPay`
- `isChargeableSeason`

这些字段只说明“该视频具有付费属性”或“存在付费解锁要求”，但不足以区分以下场景：

1. 视频本身是付费视频，但用户已经购买
2. 视频未完全解锁，但服务端允许试看
3. Web 详情未直接标出付费状态，但播放接口已表现出 preview/未解锁特征

因此 facade 在详情基础上还会结合播放权限信息继续解析：

- `hasPaid = true`：已购买，不应再显示付费警告
- `isPreview != 0`：未完全解锁，但允许试看，仍应视为付费/未解锁内容
- `accept_description` 含“试看”：可作为 preview 补充信号
- 返回单文件 `durl`/试看流时：也可作为 preview 补充信号

当前业务约定如下：

- 已购买的付费视频，不加付费警告
- 未购买但可试看，仍判定为 `isPaidVideo = true`
- 未购买且不可试看，判定为 `isPaidVideo = true`

### `isVipVideo`

该字段主要依据播放接口里的会员需求信号解析，例如：

- `support_formats[].need_vip`

当前业务约定如下：

- 已有会员能力时，不应再显示 VIP 警告
- 无会员但该视频或目标清晰度要求会员能力时，应判定为 VIP 相关内容
- 若仅为清晰度需要会员，而基础清晰度可播，客户端可按 UI 场景决定是否提示“会员专享”或“高画质需会员”

---

## 缓存与降级

### fresh cache

当缓存仍在 TTL 内时，`load()` 会直接返回 fresh 数据，不走远端请求。

### stale cache

当缓存已过 TTL 但仍在 `staleMaxAgeMs` 内：

- 若 `allowStale = true`，优先返回 stale 数据
- 同时异步触发刷新

### degrade

当远端请求失败时，facade 会优先尝试降级：

1. 若存在可接受的 stale 数据，则返回 stale 数据，并标记 `runtime.degraded = true`
2. 若没有任何可用缓存，则返回 empty envelope，并标记 `runtime.degraded = true`

常见失败分类会写入：

- `failureCode`
- `failureMessage`

空结果并不等于接口成功，只表示本次 facade 选择了“对 UI 可消费”的降级返回。

---

## 并发与去重

### single-flight

同一视频在同一时刻只会实际发起一次远端加载。后续相同 key 的请求会复用进行中的 `Deferred`，并在返回结果中体现：

- `runtime.inFlightShared = true`

### `aid` / `bvid` 别名去重

facade 会对 `aid` 与标准化后的 `bvid` 建立别名关系，避免：

- 一个请求用 `aid`
- 另一个请求用 `bvid`

却对同一视频重复发请求。

---

## 预取策略

`prefetch()` 会优先处理首屏可见视频，再延后处理剩余请求。

规则如下：

1. `VISIBLE` 请求优先进入首批
2. 超出 `firstScreenCount` 的可见请求，会被转入延后批次
3. 延后批次按 `deferredBatchSize` 分批
4. 每批之间按 `interBatchDelayMs` 间隔执行
5. 全局并发由 `VideoMetricsGlobalConcurrencyLimiter` 控制

当前默认最大总并发为 5，并为前台交互保留优先处理空间。

---

## 典型返回解释

### 详情页字段齐全，远端正常

- `snapshot.source = DETAIL_SUPPLEMENT`
- `runtime.degraded = false`

### 命中 fresh cache

- `snapshot.source = CACHE`
- `snapshot.cacheStatus = HIT`
- 不会触发远端请求

### 返回 stale cache

- `snapshot.cacheStatus = STALE`
- 可能是直接允许 stale 返回
- 也可能是远端失败后的降级结果

### 返回 empty envelope

常见于：

- 首次加载无缓存
- 远端请求失败
- 且当前没有任何可接受的 stale 数据

此时通常表现为：

- 统计字段为空
- `runtime.degraded = true`

---

## 测试覆盖的关键行为

`VideoMetricsFacadeImplTest` 当前覆盖了以下核心行为：

1. 同 key in-flight 去重
2. `aid` / `bvid` alias in-flight 去重
3. 远端失败时回退 stale cache
4. 无缓存且远端失败时返回 empty envelope
5. fresh cache 命中时不触发远端请求
6. 预取时先处理首屏请求，再处理 deferred 请求
7. 预取过程中遵守全局并发限制

---

## 备注

当前 facade 的实现重点是“为 UI 提供稳定、统一、可降级的轻量指标”，而不是完整替代详情接口或播放接口。

当业务需要更精细的付费 / 会员 / 试看判定时，应继续补充播放权限侧字段解释，并保持 facade 的统一出口不变。
