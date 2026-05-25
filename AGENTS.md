# WjzFocus 焦点协议

## 违规即失败（硬性 Gate，必须遵守）
AI 在本项目**任何代码/补丁/示例**里，只要出现 **Compose 原生焦点 API**（见下方“禁用清单”任意一项），就视为**输出违规**，必须立刻停止当前方案并**重写**，直到完全不包含禁用 API 为止；不得以“临时”“兜底”“先跑起来”为理由继续使用。

## 唯一允许的焦点方案（按优先级）
1. 优先用：`Modifier.wjzFocus(...)`
2. `wjzFocus` 不够用：改用 `wjzFocusRouter { ... }`
3. `wjzFocusRouter` 不够用：改用 `wjzFocusRequestRouter { ... }`
4. 仍不够用：只能使用 `kotlin/dev/aaa1115910/bv/wjzfocus/` 目录下的**底层 API**实现/扩展  
5. **任何情况下都禁止**回退到 Compose 原生焦点 API

## 禁用清单（出现任意一个即违规）
- `Modifier.focusable(...)`
- `Modifier.focusRequester(...)`
- `Modifier.onFocusChanged { ... }`
- `Modifier.focusTarget()`
- `Modifier.focusProperties { ... }`
- `LocalFocusManager` / `FocusManager` / `moveFocus(...)` / `clearFocus(...)`
- 任何 `requestFocus()` / `captureFocus()` 之类调用
- 在业务/UI 代码里新增或使用 `androidx.compose.ui.focus.*`（`FocusDirection` 仅作为 WjzFocus DSL 方向枚举例外）
