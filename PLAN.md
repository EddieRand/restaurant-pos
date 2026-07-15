# PLAN.md — 餐饮 POS 任务进度

## Batch 0 · 骨架

- [x] 创建项目目录 + git init + batch/0-skeleton 分支
- [x] 生成 CLAUDE.md + PLAN.md
- [x] 建全部模块骨架（settings.gradle.kts 包含所有模块）
- [x] `:core:designsystem` 落地 M3 Expressive 主题（SUNMI 橙 #FF5C00）
- [ ] 建 `.claude/skills/designsystem` skill
- [x] `:app:cashier` 空白横屏壳套主题
- [ ] `./gradlew build` 通过

**验收**：`./gradlew build` 通过；起套了橙色主题的空白横屏页。

---

## Batch 1 · 竖切（主机端，纯离线）

- [x] `:core:model` 核心实体（竖切先做 5 个：MenuItem / Order / OrderItem / Table / Payment；其余 Batch 2+ 补）
- [x] `:core:config` RegionConfig + TaxRate + 金额格式化器（给默认配置）
- [x] `:core:database` entities + DAO
- [x] `:core:hardware` Printer 接口 + MockPrinter
- [x] `:core:domain` PlaceOrderUseCase / SettlePaymentUseCase + 订单/桌台状态机
- [x] `:app:cashier` + `:feature:order/tables/checkout` 最小 UI

**验收**：DAO 测试通过；状态机单测通过；金额格式化单测通过；模拟器整条流跑通。

---

## Batch 2 · 点单 + 桌台

- [x] 堂食/外带/外卖流程（OrderType 选择 chip）
- [x] 规格加料、套餐（ModifierGroup SINGLE/MULTI，BottomSheet 选择器）
- [x] 改单/退项（voidItem → REFUNDED，删除线 UI）
- [x] 拆单/并单/转台（SplitOrderUseCase / TransferTableUseCase）
- [x] 并桌拆桌（MergeTablesUseCase / SplitTableUseCase）
- [x] 预订×堂食联动（CreateReservationUseCase / SeatReservationUseCase / ReservationsTab）
- [x] `:feature:order` 与 `:feature:tables` 各开 worktree 并行

**验收**：各操作状态机单测通过；金额计算（折扣/加价）单测通过。

---

## Batch 3 · 收银 + 支付

- [x] 拆账（按人/按项/平摊）
- [x] 混合支付
- [x] 项级/单级折扣
- [x] 服务费/小费
- [x] 退款/作废
- [x] 交班对账

**验收**：拆账与混合支付金额守恒单测通过；退款回写对账正确。

---

## Batch 4 · 打印 + 硬件

- [x] 厨房单路由（品类→工位）KitchenRouter
- [x] 小票/厨房单模板 PrintReceiptUseCase / PrintKitchenTicketUseCase
- [x] SUNMI 打印 SDK 真机集成（SunmiPrinter AIDL 实现 + 运行时硬件检测；MockPrinter 并存）⚠️ 需人工真机验证 + 官方 AAR
- [x] 钱箱 CashDrawerPort + MockCashDrawer ⚠️ 真机需人工验证
- [ ] 读卡器 ⚠️ 需人工真机验证（SDK 未到）

**验收**：Mock 路径单测通过；真机打印/钱箱「需人工真机验证」。

---

## Batch 5 · 同步

- [x] `:core:sync` 增量同步（SyncEngine/SyncOutbox/SyncRecord/SyncWriter）
- [x] 断网重连补传（AndroidNetworkMonitor + SyncEngine.start 监听isOnline）
- [x] 冲突处理（last-write-wins updatedAt；ConflictResolver接口；服务端权威字段由resolver实现）

**验收**：断网→操作→恢复→补传 集成测试通过。

---

## Batch 6 · 报表 + 配置化 + i18n

- [x] 日结 + 交班报表
- [x] `:core:config` 配置管理 UI（币种/税/语言/格式可切换）
- [x] 全端 i18n 校验
- [x] P1 报表补充：昨日对比条（ShiftReportScreen 底部 + ShiftReportUseCase.YesterdayComparison）
- [x] P1 报表补充：营收趋势图（RevenueTrendScreen + DailyReportUseCase + ReportViewModel）
- [x] P1 报表补充：高峰时段热力图（PeakHoursHeatmapScreen + 7×24 热力网格）
- [x] `daily_snapshots` 表 + DailySnapshotDao + RoomReportRepository（时序数据基础设施）

**验收**：报表与流水对账一致；切不同 RegionConfig 后金额与税计算单测全通过。

---

## Batch 7 · KDS（厨房显示系统）

- [x] `KitchenTicketRepository` + `RoomKitchenTicketRepository` + DB Migration v4→v5
- [x] `KitchenTicketStateMachine`（NEW→PREPARING→DONE；DONE→RECALLED→PREPARING）
- [x] `FireKitchenTicketsUseCase`（PlaceOrder 后按 KitchenRouter 分站创建 tickets）
- [x] `BumpTicketUseCase` / `RecallTicketUseCase`
- [x] `feature:kds` — KdsScreen（按工位分栏显示，倒计时，Bump/Recall 操作）
- [x] `app:kds` — Hilt 接线、横屏布局

**验收**：下单后厨房 ticket 自动出现；Bump 后消失；Recall 后重现；KDS 状态机单测通过。

---

## Batch 8 · 手持点单端

- [x] `app:handheld` — 复用 `:feature:order` + `:feature:tables`，精简导航
- [x] 手持端 Hilt 装配（OrderRepository、MenuItemRepository、ConfigRepository）
- [x] 竖屏适配（OrderScreen 单列布局）

**验收**：手持端可独立运行，完成选桌→点单→下单流程。

---

## Batch 9 · 用户权限

- [x] `:core:model` User / Role / Permission 模型
- [x] `UserRepository` + Room 持久化
- [x] PIN 登录 screen（`feature:auth`）
- [x] 权限守卫：void item / 退款 / 折扣 需提权角色
- [x] 当班员工绑定到 Order.operatorId / Payment.operatorId

**验收**：低权限角色无法执行退款；切换操作员后订单 operatorId 正确记录。

---

## Batch 10/11/14 · 权限弹窗 + 规格持久化 + 时区修复

- [x] 权限弹窗（提权操作时弹出 PIN 输入）
- [x] 规格 DB 持久化（ModifierGroup / Modifier Room entities）
- [x] 时区设置 UI + 持久化
- [x] 修复 Kiosk 端 DI 缺失问题

**验收**：权限弹窗正确弹出并验证 PIN；规格数据持久化后重启应用不丢失；时区切换后时间显示正确。

---

## Batch 12+13 · Kiosk 自助端 + HTTP 同步

- [x] `app:kiosk` — Kiosk 自助点餐端完整实现
- [x] Kiosk 端隐藏员工操作 UI
- [x] HTTP 同步协议（替代 WebSocket，更省电）
- [x] `:core:network` 模块 — Retrofit + OkHttp 网络层

**验收**：Kiosk 端可独立运行完成点餐流程；HTTP 同步在断网重连后正常工作。

---

## Batch 15/16/17 · 菜单管理 + 员工管理 + 时区设置

- [x] `feature:menu` — 菜单管理 UI（分类、菜品、规格的 CRUD）
- [x] `feature:settings` — 员工管理 UI（User CRUD + Role 分配）
- [x] `feature:settings` — 时区设置完善（时区选择 + 预览）
- [x] 菜单管理支持拖拽排序

**验收**：菜单管理可正常增删改菜品和分类；员工管理可正常增删改用户和角色；时区设置生效。

---

## Batch 18 · 预订取消功能

- [x] 预订取消 UI（ReservationsTab 长按取消）
- [x] CancelReservationUseCase（更新 Reservation.status）
- [x] 取消原因记录（可选）

**验收**：取消预订后状态正确更新；取消原因可选填并保存。

---

## Batch 19 · 外带/外卖队列视图

- [x] 外带订单队列视图（OrderType=TAKEOUT）
- [x] 外卖订单队列视图（OrderType=DELIVERY）
- [x] 队列状态筛选（待处理/制作中/已完成）
- [x] 快速标记完成按钮

**验收**：外带/外卖订单在队列中正确显示；状态筛选正常工作；快速标记完成更新订单状态。

---

## Batch 20/21 · 批量沽清 + 报表热销菜品

- [x] 批量沽清操作（MenuScreen 多选菜品 → 一键沽清）
- [x] 沽清状态持久化（MenuItem.soldOut）
- [x] 报表热销菜品排行（ReportScreen 热销 TOP N）
- [x] 热销菜品按金额/数量排序

**验收**：批量沽清正确更新多个菜品状态；热销菜品报表数据准确。

---

## Batch 22/23 · 同步状态指示器 + 交班收银结算

- [x] 同步状态指示器（TopBar 同步图标 + 最后同步时间）
- [x] 同步失败重试按钮
- [x] 交班收银结算完善（实收金额 vs 系统金额差异提示）
- [x] 交班小票打印

**验收**：同步状态实时更新；交班结算差异提示清晰；交班小票打印正确。

---

## Batch 24/25 · 小费支持 + 订单历史搜索

- [x] 小费支持完善（CheckoutScreen 小费输入 + 快速选择按钮）
- [x] 小费支持多种货币格式
- [x] 订单历史搜索（ReportScreen 按订单号/桌台/时间搜索）
- [x] 搜索结果分页加载

**验收**：小费正确计入订单总额；订单历史搜索准确并返回正确结果。

---

## Batch 26/27 · 订单备注 + 过敏原标签 + 开台人数

- [x] 订单级备注字段（Order.notes）
- [x] 过敏原标签（MenuItem.allergens + OrderItem 过敏原提示）
- [x] 开台人数输入（TableSession.guestCount）
- [x] 过敏原标签在 KDS 工单中显示

**验收**：订单备注正确保存并显示；过敏原标签正确显示并警告；开台人数正确记录。

---

## Batch 28+31 · 优惠券支持 + 手持端离线 UI

- [x] 优惠券模型（Coupon / CouponUsage）
- [x] 优惠券验证 + 折扣计算（CheckoutViewModel）
- [x] 手持端离线 UI（离线状态提示 + 离线操作队列）
- [x] 优惠券适用于整单或指定品类

**验收**：优惠券正确验证并折扣；手持端离线状态清晰提示；离线操作在恢复后同步。

---

## Batch 29 · 套餐/Combo 支持

- [x] 套餐模型（Combo / ComboItem）
- [x] 套餐选择 UI（BottomSheet 选择套餐内菜品）
- [x] 套餐价格计算（BasePrice + 所选菜品差价）
- [x] 套餐在 KDS 工单中正确显示

**验收**：套餐选择流程顺畅；价格计算准确；KDS 正确显示套餐内容。

---

## Batch 30 · Kiosk QR 码订单确认

- [x] Kiosk 端 QR 码生成（订单确认页显示 QR 码）
- [x] QR 码扫码确认订单
- [x] 30s 自动返回首页
- [x] QR 码内容包含订单号和确认链接

**验收**：QR 码正确生成并可被扫码；30s 倒计时正确返回；订单确认后状态更新。

---

## Batch 32 · 可配置小票模板

- [x] 小票模板配置 UI（SettingsScreen 模板编辑）
- [x] 模板字段选择（餐厅名/地址/电话/订单号/菜品/金额/二维码等）
- [x] 模板预览（实时预览小票效果）
- [x] 模板持久化（Room 存储模板配置）

**验收**：小票模板可自定义配置；实时预览效果准确；配置持久化后重启不丢失。

---

## Batch 32+ · 出票配置 UI + 打印机路由

- [x] 出票配置 UI（哪个工位出哪些菜品的小票）
- [x] 打印机路由配置（KitchenRouter 可视化配置）
- [x] 实时预览出票效果
- [x] 多打印机支持（不同工位连接不同打印机）

**验收**：出票配置界面直观易用；打印机路由正确工作；预览效果与实际出票一致。

---

## Batch 33 · 订单级备注字段

- [x] Order.notes 字段添加（DB Migration 11→12）
- [x] CheckoutScreen 备注输入
- [x] 备注在 KDS 工单中显示
- [x] 备注在小票中打印

**验收**：订单备注正确保存；KDS 和打印小票都显示备注。

---

## Batch 34 · 转台 UI

- [x] 转台 UI（长按已占桌台 → 选择目标桌台）
- [x] TransferTableUseCase 完善（转移订单和桌台状态）
- [x] 转台确认弹窗（显示原桌台和目标桌台）
- [x] 转台后原桌台自动变为 AVAILABLE

**验收**：转台操作流畅；订单正确转移到目标桌台；原桌台状态正确更新。

---

## Batch 35 · 增强交班报表

- [x] 交班报表增强（折扣/小费/税/客人指标）
- [x] 平均订单消费计算（总销售额 / 订单数）
- [x] 平均客人消费计算（总销售额 / 客人数）
- [x] 报表导出为 PDF

**验收**：交班报表数据完整准确；平均消费计算正确；PDF 导出成功。

---

## Batch 36 · KDS 实时计时器

- [x] KDS 实时 1s 计时器（ticket 创建后开始倒计时）
- [x] 可配置阈值（预警阈值和超时阈值，Settings 中配置）
- [x] OVERDUE 标记（超时 ticket 显示红色 OVERDUE 徽章）
- [x] 计时器在 KdsScreen 中实时更新

**验收**：计时器准确倒计时；阈值配置生效；OVERDUE 标记正确显示。

---

## Batch 37 · Ktor 服务端骨架

- [x] `:server` Ktor 模块骨架
- [x] `/sync/push` 端点（接收 Android 端同步数据）
- [x] JWT 认证（JwtConfig + Authentication 配置）
- [x] Exposed 表定义（同步数据表）
- [x] `:core:network` HTTP 客户端（与服务端通信）

**验收**：服务端启动成功；`/sync/push` 端点可接收数据；JWT 认证正常工作。

---

## Batch 38 · PAD 桌边点餐 + 配置 UI

- [x] `app:pad` — PAD 桌边点餐端（Ziosk/Jamezz 风格）
- [x] AYCE（All-You-Can-Eat）模式 + 标准点餐模式
- [x] PAD 配置 UI（Cashier Settings 中配置 PAD 功能）
- [x] PAD 与 KDS 实时通信（订单实时推送到厨房）

**验收**：PAD 端可独立运行完成桌边点餐；AYCE 模式正确计时；订单实时推送到 KDS。

---

## Batch 39 · WaiterCall 持久化 + 服务端 API

- [x] WaiterCall Room 持久化（WaiterCallEntity + DAO）
- [x] WaiterCall 服务端 API（`:server` AdminWaiterCallRoutes）
- [x] PAD 端 WaiterCall 按钮（呼叫服务员）
- [x] KDS 端 WaiterCall 提醒（厨房看到呼叫）

**验收**：WaiterCall 正确持久化；服务端 API 可查询和确认呼叫；PAD 和 KDS 端提醒正常工作。

---

## Batch 40 · CRM & 忠诚度系统

- [x] 客户模型（Customer / LoyaltyPointTransaction）
- [x] 忠诚度积分累计和兑换
- [x] CRM 客户管理 UI（客户列表 + 详情 + 积分历史）
- [x] 积分规则配置（消费多少元积 1 分）

**验收**：积分正确累计和兑换；CRM 界面可正常管理客户；积分规则配置生效。

---

## Batch 41+42 · 库存 & 预订服务端路由

- [x] 库存管理服务端路由（AdminInventoryRoutes — 盘点/入库/出库）
- [x] 预订服务端路由（AdminReservationRoutes — CRUD + 落座）
- [x] 库存与服务端同步（SyncEngine 支持 Inventory 表）
- [x] 预订与服务端同步（SyncEngine 支持 Reservation 表）

**验收**：库存管理服务端 API 正常工作；预订服务端 API 正常工作；同步引擎正确处理库存和预订数据。

---

## Batch 46 · 细粒度权限管控（RBAC 可配置化）★ 里程碑 M9

> 参见 PRD §4.1 完整设计。本 Batch 将现有 4 角色/7 权限的硬编码映射改为 DB 驱动的可配置 RBAC。

- [ ] `:core:model` — 新增 `PermissionKey` 枚举（27 个权限位，按 6 个功能域分组）、`Role` data class（id/displayName/isBuiltin/sortOrder）、`RolePermission` data class（roleId/permissionKey）；标记旧 `UserRole` / `Permission` 为 `@Deprecated`
- [ ] `:core:database` — 新增 `roles` / `role_permissions` Room 表 + DAO；DB Migration 把 `UserEntity.role` 从 `UserRole` 枚举改为 `String (roleId)`；迁移时写入四角色 27×4 默认权限矩阵；`User.toDomain()` 适配新字段
- [ ] `:core:domain` — `CheckPermissionUseCase` 改为注入 `RolePermissionDao` 查 DB（`SELECT EXISTS FROM role_permissions WHERE roleId=? AND permissionKey=?`）；新增 `RoleRepository` 接口 + `RoomRoleRepository`
- [ ] `:core:sync` — `role_permissions` 纳入 SyncEngine（服务端权威下行）；`SyncRoutes` 新增 `GET /sync/permissions?since=<watermark>` 端点；`PermissionSyncPuller`（类比 `MenuSyncPuller`）；水位线独立于菜单同步
- [ ] `:server` — PostgreSQL 新增 `roles` / `role_permissions` 表 + 种子数据（四角色默认矩阵）；新增 `AdminRoleRoutes`（`GET /admin/roles` 列表、`GET /admin/roles/:id/permissions` 查询、`PUT /admin/roles/:id/permissions` 整量替换）；新增 `RoleGuard` Ktor 插件/拦截器——从 JWT 提取 `roleId`，查 `role_permissions`，若无所需权限返回 `403 { "requiredPermission": "staff.manage" }`；所有 `/admin/*` 路由接入 `RoleGuard`（每个端点声明所需权限 key）；`AuthRoutes` 登录返回 JWT 时增加 `roleId` claim；`AdminUserRoutes` PATCH/POST 适配 `role` 字段为 `roleId`
- [ ] `web/admin` — 新增权限矩阵页面 `/staff/permissions`：折叠面板（6 个功能域分组）+ 角色列（4 内置角色）+ checkbox 矩阵；一键勾选/取消整组；「重置为默认」按钮；保存调用 `PUT /admin/roles/:id/permissions`；`staff.roles` 权限控制页面入口和保存按钮；Sidebar 新增「权限配置」菜单项（受 `staff.roles` 权限守卫）；`useAuth` 增加 `permissions: string[]` 字段（登录时返回）；`UsersPage` 创建/编辑用户时 `role` 下拉改为从 `GET /admin/roles` 动态加载
- [ ] `:feature:auth` — `PermissionGuardDialog` 统一所有提权操作 UI（退项/折扣/退款/作废/设置），替代 OrderScreen/CheckoutScreen 内联 AlertDialog；`PinLoginScreen` 保持现有 6 位 PIN 登录不变
- [ ] Android 端权限检查全覆盖 — 各 ViewModel 补充当前遗漏的检查点：
  - `OrderViewModel`：`modifyOrder` 加 `order.modify`、`transferTable` 加 `order.transfer`、`mergeTables` 加 `order.merge`、`splitOrder` 加 `order.split`
  - `CheckoutViewModel`：`processSplitPayment` 加 `payment.split`、`applyCoupon` 加 `payment.coupon`、`applyTip` 加 `payment.tip`
  - `MenuViewModel`：所有写操作加 `menu.edit`、`toggleSoldOut` 加 `menu.sold_out`、combo 操作加 `menu.combo`
  - `ReportViewModel`：日结加 `report.daily`、交班加 `report.shift`、导出加 `report.export`
  - `SettingsViewModel`：区域配置加 `settings.region`、打印机加 `settings.printer`、小票模板加 `settings.receipt`、税档加 `settings.tax`
  - `UserManagementViewModel`：员工管理加 `staff.manage`、角色管理加 `staff.roles`
- [ ] 单元测试：
  - `CheckPermissionUseCaseTest`：DB 有映射→Allowed / DB 无映射→Denied / 未登录→NotLoggedIn / 用户停用→Denied（覆盖全部 27 个权限 key）
  - `RoleGuardTest`：有权限→pass / 无权限→403 + requiredPermission / 无 JWT→401
  - `AdminRoleRoutesTest`：GET 角色列表 / GET 权限 / PUT 整量替换 / 非 staff.roles 角色调 PUT→403
  - `PermissionSyncRoutesTest`：增量拉取 / 水位线推进 / 空增量
  - `MigrationTest`：DB Migration 执行后四角色 27×4 默认权限矩阵完整性校验
  - `DefaultMatrixTest`：四角色默认权限矩阵与 PRD §4.1 表格逐格对齐
- [ ] 手册验收：
  - Web Admin 权限矩阵页面可正常勾选/取消每个角色的每个权限位
  - 修改经理角色的 `staff.manage` 从 ❌→✅ 保存后，经理可访问员工管理页
  - 移除服务员 `order.create` 后 POS 端服务员登录无法创建订单
  - 服务端 `/admin/users` 无 `staff.manage` 权限→403
  - 管理员无法移除自己的 `staff.roles` 权限（防锁死）
  - POS 端断网→改权限矩阵→联网→POS 自动拉取新权限生效
  - 旧四角色行为与改造前完全一致（向后兼容）

**验收**：权限矩阵可视化可配；POS 端全链路鉴权覆盖 6 个功能域；服务端 RoleGuard 覆盖全部 /admin/* 路由；四角色默认矩阵完整性单测全绿；向后兼容旧行为。

---

## Batch 44 · 服务端权威菜单下行同步（Pull-Sync）

- [x] 服务端 `GET /sync/pull?since=<watermark>` 端点（按 `updatedAt` 增量返回菜品）
- [x] `MenuPullPort` / `MenuSyncPuller` / `SyncWatermarkStore`（`:core:sync`）
- [x] `HttpMenuPullPort`（`:core:network`，OkHttp + JWT 鉴权）
- [x] `SharedPrefsSyncWatermarkStore`（持久化拉取水位线，服务端时钟权威）
- [x] `MenuItem.updatedAt` / `MenuItemEntity.updatedAt`（Room 迁移 v15→v16）
- [x] 网络恢复时自动拉取（复用 `NetworkMonitor` + `SyncEngine` 重连模式）
- [x] 五端 Application 接入 `MenuSyncPuller.start()`（cashier/handheld/kds/kiosk/pad）
- [x] 单元测试（`MenuSyncPullerTest` ×5 + `SyncRoutesTest` pull 用例 ×2）

**验收**：Web Admin 菜单改动可增量下行至所有 POS 设备；离线后联网自动补拉；水位线以服务端时间为准，避免设备间时钟漂移。

---

## Batch 45 · Web Admin 桌边 PAD 配置下行同步

- [x] Web Admin 新增「桌边 PAD」配置页（`PadConfigPage.tsx`）：AYCE 自助餐规则与套餐、待机屏宣传语与超时、可选语言与点餐行为开关
- [x] `RegionConfig.padConfig` 接入 Web Admin 类型系统（`PadConfig`/`AycePackage` 接口 + 默认值 + `normalizeRegionConfig` 合并逻辑）
- [x] 复用既有 `regionConfig` 设置存储与 `useRegionConfig` hook（无需新服务端路由）
- [x] 导航与路由接入（`/pad`，侧边栏图标 + i18n：zh-CN/en-US 全量翻译，其余 7 语言占位 TODO）
- [x] `PadConfigPullPort` / `PadConfigSyncPuller`（`:core:sync`，整体替换式拉取，无需水位线）
- [x] `HttpPadConfigPullPort`（`:core:network`，拉取 `GET /admin/settings/regionConfig`【含 `region-config` 旧 key 兜底】并解析出嵌套的 `padConfig` 字段）
- [x] `:app:pad` 接入 `PadConfigSyncPuller.start()`（启动 + 网络重连时自动拉取最新配置）
- [x] 单元测试（`PadConfigSyncPullerTest` ×4：应用配置/去重/失败保留/其余字段不受影响）

**验收**：商户在 Web Admin「桌边 PAD」页保存的 AYCE 规则、待机宣传语、语言开关等配置，PAD 设备启动或联网恢复后可自动拉取生效；与本地设备绑定（`boundTableId`/`tableDisplayName`）不冲突——这两项仍由设备本地配置。

> 说明：Web Admin 的 `useRegionConfig`/`getRegionConfig` 把整个 `RegionConfig`（含嵌套的 `padConfig`）作为单一 JSON blob 存在通用设置存储的 `regionConfig` key 下（旧 key `region-config` 兜底），并不存在独立的 `padConfig` 设置 key。因此 `HttpPadConfigPullPort` 拉取 `regionConfig` blob、解析出其中的 `padConfig` 字段再解码为 `PadConfig`，未引入新服务端表/路由，符合"最小新增面"的原则。设备绑定信息（每台 PAD 唯一）刻意排除在 Web 配置之外，避免远程覆盖本地绑定状态。

---

## 独立轨道 · Server (Ktor)

- [x] Batch 37: Ktor 骨架 + `/sync/push` + JWT 认证
- [x] Batch 39: WaiterCall API
- [x] Batch 41+42: Inventory & Reservations 路由
- [x] AuthRoutes（登录 + Token 刷新）
- [x] SyncRoutes（增量同步端点）
- [x] AdminMenuRoutes（菜单管理 API）
- [x] AdminMenuProfileRoutes（MenuProfile 时段系统 API）
- [x] AdminOrderRoutes（订单管理 API）
- [x] AdminReportRoutes（报表 API）
- [x] AdminUserRoutes（用户管理 API）
- [x] AdminSettingsRoutes（设置 API）
- [x] AdminQrOrderingRoutes（QR 点餐 API）
- [x] AdminCouponRoutes（优惠券 API）
- [x] AdminComboRoutes（套餐 API）
- [x] AdminCrmRoutes（CRM API）
- [x] AdminInventoryRoutes（库存 API）
- [x] AdminReservationRoutes（预订 API）
- [x] PublicOrderingRoutes（顾客点餐公开 API）
- [ ] AdminRoleRoutes（角色权限管理 API）← Batch 46
- [ ] RoleGuard（管理员路由鉴权中间件）← Batch 46
- [x] 单元测试（SyncRoutesTest，含 Batch 44 pull 用例 / AdminRoutesTest / PublicOrderingRoutesTest / AdminReservationRoutesTest，预订+等位+班次+特殊日期 21 例）
- [ ] 单元测试（AdminRoleRoutesTest + RoleGuardTest）← Batch 46

**验收**：所有服务端路由单测通过；增量同步端到端测试通过；JWT 认证安全。

---

## 独立轨道 · Web (Admin)

- [x] 基础架构（React + Vite + TypeScript + Tailwind CSS）
- [x] i18n（zh-CN + en-US，可切换）
- [x] 登录页（JWT Token 获取 + 持久化）
- [x] 仪表盘页（概览数据 + 快捷操作）
- [x] 菜单管理页（分类/菜品/规格 CRUD + 拖拽排序）
- [x] 订单管理页（订单列表 + 详情 + 状态筛选）
- [x] 报表页（日结/交班报表 + 图表展示）
- [x] 报表页·营收趋势 Tab（Recharts AreaChart + 7/15/30 天切换 + 环比增长）← P1
- [x] 报表页·高峰时段 Tab（7×24 热力图 + SUNMI 橙色梯度 + 点击详情）← P1
- [x] 仪表盘·本周 vs 上周对比卡片（净营收/订单数/客数周同比）← P1
- [x] 设置页（餐厅信息/打印机/税率/时区/小票模板）
- [x] 用户管理页（User CRUD + Role 分配）
- [ ] 权限配置页（权限矩阵 /staff/permissions）← Batch 46
- [x] 预订管理页（Reservation CRUD + 日历视图）
- [x] KDS 配置页（工位/打印机路由/出单配置）
- [x] API 客户端（axios 封装 + Token 拦截器）
- [x] 响应式布局（侧边栏 + 顶部导航）

**验收**：Web 管理端可正常登录和操作；i18n 切换流畅；API 调用正确。

---

## 独立轨道 · Market Pack（本期不做）

- [ ] 进某国时新建 `:market:<country>`，实现 SPI，接该国合规+本地支付

---

## 工作进行中（未提交更改）

### 已修改文件（其他并行会话产出，待后续 Batch 处理）
- `app/kiosk/build.gradle.kts` / `app/kiosk/.../di/AppModule.kt`
- `app/pad/build.gradle.kts` / `app/pad/.../di/AppModule.kt`
- `core/model/src/main/kotlin/com/restaurantpos/core/model/Order.kt`
- `feature/kds/src/main/kotlin/com/restaurantpos/feature/kds/KdsScreen.kt`
- `gradle.properties`
- `web/admin/src/api/mock.ts`
- `web/admin/src/i18n/locales/*.ts`（多语言全量）
- `web/admin/src/pages/ReservationsPage.tsx`
- `web/customer/index.html`
- `web/customer/src/main.ts`
- `web/customer/src/styles.css`

### 未跟踪文件（其他并行会话产出，待后续 Batch 处理）
- `.gradle-home/`
- `INCLUSIVE_DESIGN_GUIDE.md`（无障碍设计指南）
- `core/database/schemas/.../16.json`（Room schema 导出，由 Batch 44 迁移触发生成）
- `core/domain/src/main/kotlin/com/restaurantpos/core/domain/statemachine/OrderItemStateMachine.kt`（订单项状态机）
- `core/domain/src/main/kotlin/com/restaurantpos/core/domain/statemachine/PaymentStateMachine.kt`（支付状态机）
- `core/domain/src/test/kotlin/com/restaurantpos/core/domain/statemachine/OrderItemStateMachineTest.kt`（状态机测试）
- `core/domain/src/test/kotlin/com/restaurantpos/core/domain/statemachine/PaymentStateMachineTest.kt`（状态机测试）
- `docs/`
- `gradle-8.9-bin.zip`
- `pos-prototype-inclusive.html`（无障碍原型 HTML）

> Batch 43（SUNMI 打印集成）已提交（commit `feat(hardware): Batch 43`）。
> Batch 44（菜单下行 Pull-Sync）已提交（commit `feat(sync): Batch 44`）。
> 预订模块测试补全（AdminReservationRoutesTest，21 例全过）已提交（commit `test(server): add reservation/waitlist/shift route...`）。

---

## 竞品对标分析（参考文档）

> 详细 26 维对标矩阵、差距分优先级、分阶段补齐路线图见：
> **`docs/reporting-gap-analysis.md`**

### 对标范围

| 类别 | 产品 | 用途 |
|------|------|------|
| **主对标（6 家）** | Toast POS / Lightspeed K-Series / Square for Restaurants / TouchBistro / GloriaFood / Clover | 功能优先级判断的「行业标配」基线 |
| **参考竞品（3 家）** | Restaurant365（POS+会计/P&L） / CrunchTime!（供应链/配方成本） / Eat App（CRM/预订/营销） | 对应专项（财报/供应链/CRM）时单独参照，不进主矩阵 |

### 差距结论（摘要）

- 26 项海外竞品标配能力中，本项目覆盖 **4 项**（含部分覆盖 3 项），硬缺口 **19 项**。
- 补齐路线分 P1–P6，详见 `docs/reporting-gap-analysis.md` 第三～六节。
- P1–P5 合计约 **33 个工作日**；P6（高级分析）可选，约 20 个工作日。

---

## 模块总览

### Core 模块
- `:core:model` — 核心领域模型
- `:core:config` — 配置管理（RegionConfig / TaxRate）
- `:core:database` — Room 数据库
- `:core:domain` — Use Cases + 状态机
- `:core:hardware` — 硬件抽象层（Printer / CashDrawer / CardReader）
- `:core:sync` — 增量同步引擎
- `:core:designsystem` — M3 Expressive 主题
- `:core:network` — HTTP 网络层（Retrofit + OkHttp）

### Feature 模块
- `:feature:order` — 点单界面
- `:feature:tables` — 桌台管理
- `:feature:checkout` — 收银界面
- `:feature:kds` — 厨房显示系统
- `:feature:report` — 报表
- `:feature:settings` — 设置
- `:feature:auth` — 认证（PIN 登录）
- `:feature:menu` — 菜单管理

### App 模块
- `:app:cashier` — 收银主机端（横屏）
- `:app:handheld` — 手持点单端（竖屏）
- `:app:kds` — 厨房显示端（横屏）
- `:app:kiosk` — 自助点餐端
- `:app:pad` — 桌边点餐端（PAD）

### Server 模块
- `:server` — Ktor 后端（PostgreSQL + Exposed）

### Web 模块
- `web/admin` — React 管理后台
- `web/customer` — 顾客点餐前端（QR 码）

---

## 技术栈

### Android 端
- Kotlin + Coroutines + Flow
- Jetpack Compose + Material Design 3
- Hilt (DI)
- Room (数据库)
- Ktor Client (网络)
- State Machine (订单/支付/KDS 状态管理)

### 服务端
- Ktor (框架)
- Exposed (ORM)
- PostgreSQL (数据库)
- JWT (认证)

### Web 端
- React + TypeScript
- Vite (构建)
- Tailwind CSS (样式)
- React Router (路由)
- Axios (HTTP 客户端)
- i18next (国际化)

---

*最后更新：2026-06-07*
