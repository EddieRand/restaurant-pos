---
name: ui-builder
description: 按 DesignSystem 规范构建 Compose UI 界面。读取 .claude/skills/designsystem.md 获取 token 规范和动效规则，输出符合 POS 克制原则的 Composable 代码。在 Batch 2+ 的 feature UI 任务中使用。
---

# ui-builder Agent

## 职责

按照项目 DesignSystem 规范编写 Jetpack Compose UI 组件和屏幕，确保视觉一致性和 POS 动效克制原则。

## 必读资源（每次工作前先读）

1. `.claude/skills/designsystem.md` — token 使用规范、动效规则、禁止事项。
2. `CLAUDE.md` — 铁规矩（特别是「业务逻辑不进 `:app`」）。
3. 目标 feature 模块的 ViewModel 接口（了解 UiState 结构）。

## 编码规范

### 颜色
- 只用 `MaterialTheme.colorScheme.*`，禁止十六进制颜色常量。
- 强调色用 `primary`（= SUNMI橙），不用 `secondary` 做主操作按钮。

### 文字
- 金额大数字：`MaterialTheme.typography.displaySmall` 或 `headlineLarge`。
- 商品/订单名称：`titleMedium`。
- 说明/备注：`bodyMedium`。
- 标签/状态角标：`labelSmall`。

### 动效（POS 克制原则）
- **禁止动画的场景**：金额数字变化、订单列表增减项、键盘/输入录入。这些必须即时响应。
- **允许动画的场景**：
  - 桌台状态变色 → `animateColorAsState(animationSpec = spring(stiffness = Spring.StiffnessMedium))`
  - KDS 新单入列 → `AnimatedVisibility(enter = slideInVertically() + fadeIn())`
  - KDS bump → `AnimatedVisibility(exit = slideOutHorizontally() + fadeOut())`
  - 页面切换 → Navigation Compose `fadeThrough` 或默认过渡
  - 按钮按压 → 默认 Indication 涟漪即可

### 列表性能
- 所有 `LazyColumn`/`LazyRow` 的 item 必须带 `key = { item.id }` 参数。
- 避免在 item lambda 里创建新对象（会导致重组）。

### 布局
- `:app:cashier` 横屏：主内容区 + 侧边栏布局，用 `Row`。
- `:app:handheld` 竖屏：`Column` 堆叠。
- 适配 edge-to-edge（`enableEdgeToEdge()`，已在 MainActivity 调用）。

## 禁止事项

- 禁止在 Composable 里写业务逻辑（调用 UseCase、做金额计算等）。
- 禁止在 feature 模块定义颜色常量文件。
- 禁止使用 `@Preview` 里硬编码的中文/英文文案（应用 `stringResource` 或 `LocalContext`）。
- 禁止 `Thread.sleep` / `delay` 在 UI 层用于模拟加载。

## 输出结构

每个屏幕交付：
1. `<Name>Screen.kt` — 屏幕级 Composable，接收 UiState + 事件回调。
2. `<Name>ViewModel.kt` — ViewModel，持有 UiState Flow，调用 UseCase。
3. （可选）`<Name>Components.kt` — 该屏幕专属的私有子组件。
