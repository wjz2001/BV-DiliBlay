# bili-api.metrics Docs (Implementation-Synced)

这份目录下的 Markdown 文档用于解释并“固化” `dev.aaa1115910.biliapi.metrics` 当前已经落地的行为：Canonical 映射、L1 内存缓存、Facade 加载与预取调度、去重/并发限制/cooldown，以及 UI 接入要点。当前 `CanonicalStat` 额外承载 `isVipVideo` / `isPaidVideo` / `isVerticalVideo` 三个稿件级布尔属性，其中 `isVipVideo` 与 `isPaidVideo` 按互斥语义收口。

**重要原则：**
- 文档以“当前实现 + 可执行测试”为准。
- 当文档与代码冲突时：**以测试与代码为准**，并尽快修正文档（见下文“维护与冗余控制”）。

---

## 快速阅读路径（建议按这个顺序）

1. **先确认能跑通验证**：<!--citation:1-->
2. **看 Facade 对外形态与配置边界**：<!--citation:2-->
3. **理解 load/prefetch 的核心行为（去重/并发/调度/cooldown/SWR）**：  
   - <!--citation:3-->  
   - <!--citation:4-->（若已合并进上一份，则此文件应仅保留跳转说明）
4. **理解缓存层真实语义（TTL / HIT / STALE / invalidate / EXACT 保护）**：<!--citation:5-->
5. **理解 Canonical 映射（rawView 哨兵规避 / 数值解析 / precision）**：<!--citation:6-->
6. **对外“模型契约”与枚举语义**：<!--citation:7-->
7. **UI 如何接入与失败/降级策略**：<!--citation:8-->
8. **数据源盘点（当前用哪条链路）**：<!--citation:9-->

---

## 文档索引（按“权威程度”分层）

### A. 权威行为规范（Normative）
这些文件用于描述“现在就是这么跑”的行为，应尽量与测试一一对应：

- <!--citation:3-->  
  主题：`load()` / `prefetch()` 主路径、singleflight、key 体系、并发 limiter、cooldown、降级策略。
- <!--citation:5-->  
  主题：`VideoStatCache` 的 L1 内存缓存语义（TTL / HIT/STALE / 双 key / invalidate / EXACT 保护）。
- <!--citation:6-->  
  主题：`CanonicalStatMapper` 的字段映射与解析规则（rawView、字符串/BigDecimal、precision 聚合，以及 `isVipVideo` 在 Mapper 阶段固定为 `null` 的边界）。
- <!--citation:2-->  
  主题：Facade API、边界、配置项与运行时元信息的解释，以及播放权限补齐 `isVipVideo`、再收口 `isPaidVideo` 的职责边界。
- <!--citation:1-->  
  主题：哪些结论由哪些测试证明；如何运行验证命令。

### B. 契约/语义汇总（Contract / Semantics）
- <!--citation:7-->  
  主题：`CanonicalStat` / `StatEnvelope` 的字段语义与枚举值含义；`isVipVideo` / `isPaidVideo` 互斥语义；rawView 硬约束；时间字段语义边界。  
  注：若未来对外序列化/跨语言传输，应另行定义版本化 schema；当前仓库内部以 enum 表达为主。

### C. 接入建议（Integration）
- <!--citation:8-->  
  主题：UI 列表回填、可见优先、滚动预取、失败/降级推荐策略。

### D. 数据源事实（Inventory / Facts）
- <!--citation:9-->  
  主题：当前 Canonical/Facade 使用的数据源链路与字段事实；未启用的备选来源盘点。

### E. 历史资料（Archived / Non-authoritative）
- <!--citation:10-->  
  仅作为背景记录，不应作为当前行为的权威来源；如需描述现状，请以 `load_control_dedup_concurrency.md` 为准。

---

## 维护与冗余控制（非常重要）

为避免“文档互相抄来抄去导致漂移”，请遵守以下规则：

### 1. 单一事实只允许有一个权威落点（Single Source of Truth）
- **去重/并发/cooldown/SWR/prefetch 调度**：以 `load_control_dedup_concurrency.md` 为唯一权威说明。  
  - `scheduler_design.md` 若存在，应只保留“实现细节补充”或“跳转到权威章节”，避免重复描述同一规则。
- **缓存 TTL/HIT/STALE/invalidate/EXACT 保护**：以 `cache_design.md` 为唯一权威说明。
- **Mapper 解析/precision/rawView 规则**：以 `canonical_mapper.md` 为唯一权威说明。
- **可执行证据与跑法**：以 `verification.md` 为唯一权威说明。

其他文档如需引用这些规则：
- **只写结论 + 链接到权威文档具体章节**（不要复制整段规则）。

### 2. 任何“硬约束”必须能落到测试或代码引用
当你在文档里写了“必须 / 严禁 / 硬约束 / 当前实现固定为”：
- 要么在 `verification.md` 里能找到对应测试；
- 要么在文档下方的 Code References 里能定位到关键实现行。

### 3. 改代码时，按这个顺序更新（避免漏）
1. 先改实现与测试（确保行为被测试锁住）。
2. 更新对应的“权威文档”那一份（不要同时改多份）。
3. 若存在其它引用处，只改链接/一句话总结，不复制规则正文。

### 4. 文档风格约定（减少歧义）
- 写“现状”用 **当前实现**、**当前固定**、**已落地**。
- 写“未来”必须明确标注 **（未实现）**，并避免混入现状文档的核心段落。
- 时间/窗口类语义请写清：`ttlMs/expireAt`（fresh） vs `staleMaxAgeMs/maxDegradeAgeMs`（Facade 内部策略窗口）。

---

## 本地验证（推荐入口）

优先按 <!--citation:1--> 执行。典型命令示例：

```powershell
.\gradlew.bat :bili-api:test --tests "dev.aaa1115910.biliapi.metrics.*" --no-daemon
```

- 说明：
  - 使用包名通配符可避免“测试类改名导致漏跑”。
  - 若需要更快的定点回归，可再按文件里列出的具体测试类运行。

## 变更记录
当发生“行为改变”的 PR（例如：key 规则变化、cooldown 范围变化、prefetch 调度参数变化、rawView 处理变化等），建议在对应权威文档顶部新增一条简短变更记录（日期 + 结论 + 指向 PR/commit），方便回溯。

## 术语速查（快速对齐）
- **fresh**：`now < expireAt`，缓存窗口内命中（HIT）。
- **stale**：缓存存在但已过 TTL（STALE），可能仍用于快速返回或失败降级。
- **SWR**：stale-while-revalidate；先返回 stale，再后台刷新。
- **singleflight**：同 key 并发请求合并为一次真实网络请求，其它请求 join 同一个 in-flight。
- **cooldown**：对 `HTTP_412/HTTP_429` 等错误在一定时间内跳过网络，直接降级返回 stale/empty。
