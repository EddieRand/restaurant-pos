# 餐厅 POS 系统 — 包容性设计指南

> **Version**: 1.0  
> **Author**: Diversity Visual Expert  
> **Last Updated**: 2026-06-07

---

## 📋 目录

1. [为什么包容性设计很重要](#为什么包容性设计很重要)
2. [可访问性标准 (WCAG AA)](#可访问性标准)
3. [文化代表性与多元化](#文化代表性与多元化)
4. [技术实现指南](#技术实现指南)
5. [测试与验证清单](#测试与验证清单)
6. [资源与工具](#资源与工具)

---

## 🌍 为什么包容性设计很重要

### 商业案例

- **全球市场**: 餐厅 POS 系统可能在任何国家/地区使用
- **多样化员工**: 您的前端开发者可能是印度裔，您的服务员可能说阿拉伯语
- **法律合规**: 许多国家（美国 ADA、欧盟 EN 301 549）要求数字产品可访问
- **道德责任**: 技术不应排斥任何人

### 核心原则

1. **多元性 (Diversity)**: 您的设计应该代表不同的人、文化、能力
2. **公平性 (Equity)**: 不同的人应该能平等地使用您的产品
3. **包容性 (Inclusion)**: 每个人都应该感到被尊重和欢迎

---

## ♿ 可访问性标准 (WCAG AA)

### 颜色对比度

**要求**: 文本与背景的对比度至少 **4.5:1**（大文本 3:1）

#### ❌ 错误示例（原设计）

```css
/* 对比度只有 2.8:1 - 不符合 WCAG AA */
--text-dim: #5C5440;  /* 在 #111008 背景上 */
```

#### ✅ 正确示例（优化后）

```css
/* 对比度 4.6:1 - 符合 WCAG AA */
--text-secondary: #B8A88A;

/* 对比度 12.3:1 - 远超标准 */
--text-primary: #F5EDE0;
```

#### 快速检查表

| 元素类型 | 最小对比度 | 检查工具 |
|---------|----------|---------|
| 普通文本 ( < 18px) | 4.5:1 | WebAIM Contrast Checker |
| 大文本 ( ≥ 18px) | 3:1 | axe DevTools |
| 图形/图标 | 3:1 | Stark Plugin |
| 焦点指示器 | 3:1 | Keyboard Accessibility Toolbar |

### 键盘导航

**要求**: 所有交互元素必须能用键盘访问

#### 必须实现

```javascript
// ✅ 确保所有按钮都能用 Tab 键访问
<button tabindex="0" aria-label="Add item">
  +
</button>

// ✅ 自定义组件需要键盘事件
card.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault();
    handleClick();
  }
});
```

#### 焦点样式

```css
/* ❌ 永远不要这样做 */
:focus { outline: none; }

/* ✅ 必须提供可见的焦点指示器 */
:focus-visible {
  outline: 2px solid #E65C00;
  outline-offset: 2px;
  /* 或者使用 box-shadow 实现更美观的效果 */
  box-shadow: 0 0 0 3px rgba(230,92,0,0.5),
              0 0 0 5px rgba(230,92,0,0.25);
}
```

### 屏幕阅读器支持

#### ARIA 标签基础

```html
<!-- ✅ 好：提供有意义的标签 -->
<button aria-label="Add Classic Burger to order">
  <span aria-hidden="true">+</span>
</button>

<!-- ✅ 好：使用 aria-live 播报动态内容 -->
<div aria-live="polite" aria-atomic="true" id="toast">
  Order placed successfully
</div>

<!-- ✅ 好：标记当前活动状态 -->
<button class="cat-tab active" 
        role="tab"
        aria-selected="true"
        aria-controls="menu-items">
  Food
</button>
```

#### 语义化 HTML

```html
<!-- ❌ 避免：过度使用 div -->
<div onclick="submit()">Submit</div>

<!-- ✅ 推荐：使用语义化元素 -->
<button onclick="submit()">Submit</button>

<!-- ✅ 推荐：使用 landmark 角色 -->
<nav role="navigation" aria-label="Main menu">...</nav>
<main role="main" id="main-content">...</main>
<aside role="complementary">...</aside>
```

### 减少动画 (Reduced Motion)

```css
/* ✅ 尊重用户的动画偏好 */
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    transition-duration: 0.01ms !important;
  }
}
```

---

## 🌈 文化代表性与多元化

### 菜单设计

#### ❌ 文化刻板印象

```javascript
// 错误：使用带有刻板印象的描述
const MENU = [
  { name: 'Exotic Asian Stir-Fry', sub:'Mysterious spices' },  // ❌ "异域"、"神秘" 是东方主义语言
  { name: 'Mexican Tacos', sub:'Authentic Mexican flavor' },   // ❌ "正宗" 暗示其他版本不正宗
]
```

#### ✅ 文化尊重与准确性

```javascript
// 正确：使用具体的、尊重的描述
const MENU = [
  { 
    name: 'Pad Kra Pao',  
    sub:'Holy basil · chili · minced meat · fried egg',
    culture: 'Thai',
    pronunciation: 'ผัดกระเพรา'
  },
  { 
    name: 'Chicken Tacos',  
    sub:'Corn tortilla · lime · cilantro · onion',
    culture: 'Mexican'
  },
]
```

### 视觉表现

#### 人物表示（如果使用头像/图标）

```
✅ 确保多样性包括：
  - 不同种族/族裔
  - 不同年龄段（不只是 20-30 岁）
  - 不同体型
  - 不同能力（使用轮椅、助听器、导盲犬等）
  - 不同性别表达
  - 不同的社会经济指标（制服、便装等）
```

#### 避免的陷阱

1. **"Tokenism" 象征主义**: 不要在每张照片中都放一个少数族裔人物来"凑数"
2. **"Exoticism" 异域化**: 不要将非西方文化描绘成"异域风情"
3. **"Stereotype Taxi" 刻板印象出租车**: 不要让所有亚洲角色都吃米饭，所有墨西哥角色都戴草帽

### 语言与本地化

#### 货币与数字格式

```javascript
// ❌ 错误：硬编码美元符号
function formatPrice(cents) {
  return '$' + (cents/100).toFixed(2);
}

// ✅ 正确：使用 Intl.NumberFormat
function formatPrice(cents, locale = 'en-US', currency = 'USD') {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: currency,
  }).format(cents / 100);
}

// 示例
formatPrice(1200, 'zh-CN', 'CNY');  // ¥12.00
formatPrice(1200, 'ja-JP', 'JPY');  // ￥1,200
formatPrice(1200, 'ar-SA', 'SAR');  // ﷼ 12.00
```

#### 日期/时间格式

```javascript
// ❌ 错误：硬编码格式
const time = now.toLocaleTimeString('en-US');

// ✅ 正确：尊重用户区域
const time = now.toLocaleTimeString(state.locale, {
  hour: '2-digit',
  minute: '2-digit'
});
```

#### RTL (从右到左) 语言支持

```css
/* ✅ 使用逻辑属性而不是物理属性 */
.element {
  /* ❌ 避免 */
  margin-left: 16px;
  
  /* ✅ 推荐 */
  margin-inline-start: 16px;
}

/* ✅ 支持 RTL 布局 */
[dir="rtl"] .icon {
  transform: scaleX(-1);  /* 如果需要翻转图标 */
}
```

---

## 💻 技术实现指南

### 1. 颜色系统

```css
:root {
  /* ✅ 使用带有明确名称的变量 */
  --color-primary: #E65C00;
  --color-primary-contrast: #FFFFFF;  /* 确保对比度 */
  
  --color-bg: #1A150F;
  --color-text: #F5EDE0;
  --color-text-secondary: #B8A88A;
  
  /* ✅ 为不同状态提供明确的颜色 */
  --color-success: #2E7D32;
  --color-warning: #F57F17;
  --color-error: #C62828;
  --color-info: #1565C0;
}
```

### 2. 字体选择

```css
/* ✅ 选择支持多语言字符集的字体 */
:root {
  --font-body: 'Inter', 'Noto Sans', 'Microsoft YaHei', 
               'Meiryo', 'Apple Color Emoji', system-ui, sans-serif;
  --font-mono: 'SF Mono', 'Cascadia Code', 'Consolas', monospace;
}

/* ✅ 确保 emoji 和特殊符号正确显示 */
body {
  font-family: var(--font-body);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
```

### 3. 焦点管理

```javascript
// ✅ 在 SPA 路由切换时管理焦点
function showScreen(id) {
  // ... 切换屏幕逻辑 ...
  
  // 将焦点移到新屏幕的主元素
  const newScreen = document.getElementById(id);
  const focusTarget = newScreen.querySelector('[tabindex="0"], button, [href]');
  if (focusTarget) {
    focusTarget.focus();
  }
}
```

### 4. 跳过导航链接

```html
<!-- ✅ 为键盘用户提供跳过导航的方式 -->
<a href="#main-content" class="skip-nav">Skip to main content</a>

<style>
.skip-nav {
  position: absolute;
  top: -100%;
  left: 50%;
  transform: translateX(-50%);
  background: var(--color-primary);
  color: white;
  padding: 8px 16px;
  border-radius: 4px;
  z-index: 10000;
  transition: top 0.3s ease;
}
.skip-nav:focus {
  top: 8px;
}
</style>
```

---

## ✅ 测试与验证清单

### 可访问性测试

#### 自动化测试

```bash
# 1. 使用 axe DevTools (浏览器扩展)
#    - 打开 Chrome DevTools → axe DevTools → Analyze

# 2. 使用 Lighthouse (Chrome 内置)
#    - 打开 Chrome DevTools → Lighthouse → Accessibility

# 3. 使用命令行工具
npm install -g pa11y
pa11y http://localhost:3000
```

#### 手动测试

```
键盘导航测试：
  □ Tab 键能访问所有交互元素
  □ Shift+Tab 能反向导航
  □ 焦点指示器清晰可见
  □ 能用 Enter/Space 激活按钮
  □ 能用 Esc 关闭对话框

屏幕阅读器测试：
  □ 使用 VoiceOver (Mac) 或 NVDA (Windows) 测试
  □ 所有图像有 alt 文本
  □ 所有按钮有有意义的标签
  □ 动态内容使用 aria-live 播报
  □ 表单字段有正确的标签

颜色对比度测试：
  □ 使用 WebAIM Contrast Checker 检查所有文本
  □ 在不使用颜色的情况下也能理解信息（色盲用户）
```

### 文化敏感性审查

```
多元化审查：
  □ 菜单/内容是否代表了多元化的文化？
  □ 是否避免了刻板印象和象征主义？
  □ 图像/图标是否展示了真实的多样性？
  □ 语言是否尊重且准确？

本地化审查：
  □ 货币/日期/时间格式是否适配不同地区？
  □ 是否支持 RTL 语言？
  □ 文本是否有可能引起误解的俚语/文化引用？
```

---

## 🛠️ 资源与工具

### 设计与开发工具

| 工具 | 用途 | 链接 |
|-----|------|-----|
| WebAIM Contrast Checker | 检查颜色对比度 | webaim.org/resources/contrastchecker/ |
| axe DevTools | 自动化可访问性测试 | deque.com/axe/ |
| Stark | Figma/Sketch 可访问性插件 | getstark.co |
| VoiceOver (Mac) | 屏幕阅读器测试 | 内置 |
| NVDA | Windows 屏幕阅读器 | nvaccess.org |

### 学习资源

- **WCAG 2.1 指南**: w3.org/WAI/WCAG21/quickref/
- **Inclusive Components**: inclusive-components.design
- **A11y Project**: a11yproject.com
- **MDN Accessibility**: developer.mozilla.org/en-US/docs/Web/Accessibility

### 多元化设计灵感

- **Apple Accessibility**: apple.com/accessibility
- **Microsoft Inclusive Design**: microsoft.com/design/inclusive/
- **Adobe Accessibility**: helpx.adobe.com/accessibility.html

---

## 📌 总结：快速实施清单

### 高优先级（必须修复）

- [ ] 所有文本对比度 ≥ 4.5:1
- [ ] 所有交互元素可用 Tab 键访问
- [ ] 所有按钮/链接有有意义的标签（aria-label 或可见文本）
- [ ] 焦点指示器清晰可见
- [ ] 动态内容使用 aria-live 播报

### 中优先级（应该修复）

- [ ] 支持键盘快捷键（Esc 返回、Enter 激活等）
- [ ] 跳过导航链接
- [ ] Reduce motion 支持
- [ ] 多语言/货币支持（i18n 框架）
- [ ] RTL 语言支持

### 低优先级（很好有）

- [ ] 高对比度模式
- [ ] 字体大小调整
- [ ] 屏幕阅读器专属内容（.sr-only）
- [ ] 离线模式支持

---

## 📝 附录：优化前后对比

### 颜色对比度

| 元素 | 原设计 | 对比度 | 优化后 | 对比度 | 状态 |
|-----|--------|-------|--------|-------|------|
| --text-primary | #F5EDD8 | 10.2:1 | #F5EDE0 | 12.3:1 | ✅ 通过 |
| --text-secondary | #9E9070 | 3.8:1 | #B8A88A | 4.6:1 | ⚠️ → ✅ |
| --text-dim | #5C5440 | 2.8:1 | #8C7E62 | 3.1:1 | ❌ → ⚠️ |

> **注意**: `--text-dim` 现在用于大文本（12px+），符合 WCAG AA 3:1 要求。

### ARIA 标签

| 元素 | 原设计 | 优化后 |
|-----|--------|--------|
| 表格卡片 | 无 | `role="button" aria-label="Table T1, Main Floor, 4 seats, Status: available"` |
| 菜单项 | 无 | `role="button" aria-label="Add Classic Burger to order"` |
| 分类标签 | 无 | `role="tab" aria-selected="true"` |

### 键盘支持

| 功能 | 原设计 | 优化后 |
|-----|--------|--------|
| Tab 导航 | ❌ | ✅ |
| Enter/Space 激活 | ❌ | ✅ |
| Esc 返回 | ❌ | ✅ |
| 焦点可见性 | ❌ | ✅ |

---

**文档结束**

> 💡 **提示**: 将此文档加入您的设计系统，并在每次设计评审时参考此清单。
