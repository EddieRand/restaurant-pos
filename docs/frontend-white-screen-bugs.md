# 前端白屏 Bug 经验手册

> 每次遇到白屏或渲染崩溃，先查这里；修完后把新经验追加进来。

---

## 根因分类

### 1. Mock 层缺少端点处理（最高频）

**现象**：页面在 `VITE_MOCK_AUTH=true` 模式下打开某个 Tab 就白屏，连接真实服务器时正常。

**原因**：`mock.ts` 末尾有兜底 `return mockResponse({ ok: true })`。新增 API 端点后，若忘记在 mock 中加处理，该请求返回 `{ ok: true }` 对象，然后组件把它当数组/特定结构使用时抛 TypeError。

**案例**：`/admin/reports/payment-methods`、`/admin/reports/tax`、`/admin/reports/modifiers` 三个端点未在 mock 中注册，组件拿到 `{ ok: true }` 后调 `.reduce()` / 访问 `.lines` 抛异常，整个 React 树白屏。

**规则**：
- **每新增一个 API 端点，必须同步在 `mock.ts` 里加对应的 mock handler**，且 mock 数据要符合真实类型（数组就返回数组，带字段的对象必须包含所有必需字段）。
- 可以在 mock.ts 最末尾的兜底行之前加一行 `console.warn('[mock] unhandled:', url)` 帮助发现遗漏。

---

### 2. 组件对 API 响应的数据类型缺乏防御

**现象**：服务器返回 `null`、`undefined`、或形状不对的对象，组件在 render 阶段抛异常。

**原因**：直接访问深层属性或对非数组调数组方法。

**案例**：
```tsx
// ❌ 危险：data 可能是 null 或缺少 lines 字段
{data.lines.map(...)}

// ✅ 安全
{(data?.lines ?? []).map(...)}
```

**规则**：
- 数组操作加空值兜底：`(data ?? []).map(...)` / `(data?.items ?? []).reduce(...)`
- 嵌套对象访问用可选链：`data?.lines ?? []`
- 渲染前先校验数据形状：`Array.isArray(data) && data.map(...)`
- `useEffect` 里的 `.then(setData)` 加 `.catch` 防止 unhandled rejection 静默吞掉错误

---

### 3. 缺少 ErrorBoundary（放大器）

**现象**：任何 render 阶段的异常（哪怕只是一个子 Tab）都会白屏整个页面。

**原因**：React 的默认行为：render 时抛出的异常会向上冒泡，直到遇到 ErrorBoundary 或根节点，无 ErrorBoundary 时整个 React 树卸载。

**解决**：
- 在 `App.tsx` 的每个 `<Route element=...>` 都用 `<ErrorBoundary label="PageName">` 包裹 → 页面级隔离
- 在有多个 Tab 的页面，在 Tab 内容渲染处再包一层 `<ErrorBoundary label={activeTab}>` → Tab 级隔离
- `ErrorBoundary` 位置：`src/components/ErrorBoundary.tsx`

**ErrorBoundary 使用模板**：
```tsx
// App.tsx — 页面级（已配置）
<Route path="reports" element={<ErrorBoundary label="Reports"><ReportPage /></ErrorBoundary>} />

// ReportPage.tsx — Tab 级（已配置）
<ErrorBoundary label={activeTab}>
  {activeTab === 'payments' && <PaymentsTab />}
  ...
</ErrorBoundary>
```

---

## 开发前检查清单

新增功能 / API 端点时，按以下顺序检查：

- [ ] **`mock.ts`**：新 API 端点是否加了 mock handler？返回的数据结构是否和真实类型一致？
- [ ] **数据访问**：组件是否用 `?? []` / `?.field` 防御 null/undefined？
- [ ] **ErrorBoundary**：新页面是否在 `App.tsx` 的 Route 中包了 `<ErrorBoundary>`？有 Tab 结构的是否在 Tab 内容处也包了？
- [ ] **类型匹配**：`setData` 的初始值类型与 API 返回类型是否一致（`useState<T[]>([])` 对应数组类型，`useState<T | null>(null)` 对应可能为空的对象）？

---

## 历史修复记录

| 日期 | 页面 | 问题 | 修复 |
|------|------|------|------|
| 2026-06-10 | ReportPage — 收款方式/税费/修饰符 Tab | mock 缺少 3 个端点 handler，兜底返回 `{ ok:true }` 导致 `.reduce()`/`.lines` 抛 TypeError，无 ErrorBoundary 导致整页白屏 | 补全 mock handlers；加 `Array.isArray` 防御；在 App.tsx 所有 Route + ReportPage/UsersPage 的 Tab 处加 ErrorBoundary |
| 2026-06-10 | ReportPage — 收款方式 Tab（第二次） | mock handler 虽然补了，但 `useMemo` 里直接调 `data.reduce()` 没有防御，`setData` 接到非数组时仍然抛异常。ErrorBoundary 拦住了白屏但用户看到"加载失败" | 在 `.then()` 处归一化：`.then(d => setData(Array.isArray(d) ? d : []))`；`useMemo` 内部先 `const rows = Array.isArray(data) ? data : []` 再操作 |
| 2026-06-10 | SettingsPage（支付方式管理）/ OrdersPage / DashboardPage | `paymentMethodApi.list().then(setPaymentMethods)` 直接赋值未归一化。当 `VITE_API_BASE_URL` 指向的服务实际是前端静态服务器（无 `/admin/payment-methods` 路由）时，axios 拿到 SPA fallback 的 `index.html`（HTML 字符串，状态码 200，不会走 `.catch`），`setPaymentMethods` 收到字符串，渲染时 `paymentMethods.map is not a function`，SettingsPage 整页"加载失败" | 三处 `.then()` 全部改为 `.then(d => setPaymentMethods(Array.isArray(d) ? d : []))`；同时排查 dev 环境 `VITE_API_BASE_URL`（`.env.development`）是否真的指向 Ktor server（8080 端口）而不是前端自身 |
