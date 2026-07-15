# DesignSystem Skill

## 用途

在所有 `:feature:*` 和 `:app:*` 模块中使用主题 token 时，遵循本 skill 的规范。禁止在模块内定义颜色常量或文字样式。

## 主题入口

```kotlin
// 所有 Composable 根节点必须包裹在 PosTheme 内
PosTheme(darkTheme = false) {
    // 内容
}
```

## 颜色使用规范

从 `MaterialTheme.colorScheme` 取 token，**禁止直接使用十六进制颜色**：

```kotlin
// ✅ 正确
Surface(color = MaterialTheme.colorScheme.surface) { ... }
Text(color = MaterialTheme.colorScheme.onSurface)

// ❌ 错误
Surface(color = Color(0xFFFF5C00)) { ... }
```

常用语义对应：

| 场景 | Token |
|------|-------|
| 主操作按钮 | `primary` |
| 品牌强调色 | `primary` (= SUNMI橙 #FF5C00) |
| 卡片背景 | `surface` |
| 卡片文字 | `onSurface` |
| 次级容器 | `primaryContainer` |
| 错误/退款 | `error` / `errorContainer` |

## 文字样式

```kotlin
Text(style = MaterialTheme.typography.titleMedium) // 菜单项名称
Text(style = MaterialTheme.typography.displaySmall) // 金额大数字
Text(style = MaterialTheme.typography.bodyMedium)   // 说明文字
Text(style = MaterialTheme.typography.labelSmall)   // 标签/角标
```

## 形状

```kotlin
Card(shape = MaterialTheme.shapes.medium)     // 菜单卡片 12dp
Card(shape = MaterialTheme.shapes.large)      // 底部弹窗 16dp
Button(shape = MaterialTheme.shapes.small)    // 按钮 8dp
```

## 动效规则

- **禁止动画**：金额数字更新、订单项增减、输入框——必须即时响应
- **允许动画**：
  - 按钮按压：`Indication` 默认涟漪即可
  - 桌台状态变色：`animateColorAsState(animationSpec = spring())`
  - KDS 新单入列：`AnimatedVisibility(enter = slideInVertically + fadeIn)`
  - KDS bump 完成：`AnimatedVisibility(exit = slideOutHorizontally + fadeOut)`
  - 页面切换：Navigation Compose 默认过渡或 `fadeThrough`

## 依赖引入

在模块 `build.gradle.kts` 中：

```kotlin
implementation(project(":core:designsystem"))
// BOM 已在 designsystem 中 api 导出，不需重复声明 compose-bom
```

## 禁止事项

- 禁止在 `:feature` / `:app` 模块的 `Color.kt` 定义任何颜色
- 禁止 `isSystemInDarkTheme()` 自动切换（POS 设备一般固定主题）
- 禁止 `DynamicColorScheme`（SUNMI 橙必须固定）
- 禁止在列表 item 上加 `animateItemPlacement` 以外的位移动画
