# REVIEW_LOG — 全项目审查/验证记录

执行计划：`~/.claude/plans/tranquil-waddling-bonbon.md`（P0 基线 → P1 全端主流程 → P1.5 取餐号审计 → P2 合规 → P3 测试缺口 → P4 Web → 最终门）

## 发现记录

| ID | 严重级 | 位置 | 问题 | 违反规则/类别 | 修复批次 | 验证命令 | 状态 |
|----|--------|------|------|--------------|---------|---------|------|
| F-001 | P0 | gradle/libs.versions.toml:69 | `hilt-work-compiler` 错用 `androidx.hilt:hilt-compiler` 配 Dagger 版本号 2.52（该坐标不存在，应为 1.2.0/`hilt-work` 版本引用），导致 `:app:cashier` 编译失败、全量测试无法运行 | 构建破坏 | P0 | `./gradlew test` | 已修复，验证中 |
| F-002 | P0 | web/admin/src/main.tsx:8 | 顶层 await（mock adapter 动态导入）在 build target es2020/chrome87 下不被 esbuild 支持，`npm run build` 失败；dev 模式不受影响所以一直没暴露 | 构建破坏 | P0 | `npm run build` | 已修复（改 async bootstrap），验证中 |

| F-003 | P0 | feature/auth/AuthViewModel.kt:30-31 + 各 app DI | `AuthViewModel` 注入 `TimeclockRepository?`/`TimeclockConfig?` 但全仓无任何 `@Provides`（Dagger 可空注入也需显式绑定），导致 `:app:cashier`/`:app:handheld` Hilt 编译失败；`HttpTimeclockApi` 实现存在但从未接线 | 半成品接线 | P0 | `./gradlew test` | 已修复（NetworkModule 提供 TimeclockRepository，cashier/handheld AppModule 提供 TimeclockConfig），验证中 |
| F-004 | P0 | app/pickup-display + server SyncRoutes.kt `/pull` | **订单无下行同步通路**：`/sync/pull` 只返回 menuItems+kitchenTickets；叫号屏/收银端永远收不到其他终端的订单（连 Kiosk 单都到不了收银台结账）| 架构缺口 | P1.5（用户已批准 S2 线上格式加性变更） | `:core:sync:test` `:server:test` | **已修复**（commit b6aa9fdd：`/pull` 增 orders+orderItems 下行（按订单 updatedAt 水位线，items 随单下发）；新增 `OrderSyncPuller`+`HttpOrderPullPort`+`OrderRepository.applyRemote`（LWW、不回写 outbox）；cashier/kds/pickup-display 三端接入；7 个 puller 单测 + server 路由测试全绿） |
| F-005 | P1 | app/pickup-display/di/AppModule.kt | 新 app 依赖 `:feature:tables` 把 `TablesViewModel` 带进 Hilt 图，缺 `RegionConfig`/`TransferTableUseCase` 绑定 → 编译失败 | 半成品接线（本次审查引入的新模块） | P0 | `./gradlew :app:pickup-display:compileDebugKotlin` | 已修复，验证中 |

| F-006 | P1 | core/domain/.../AllocatePickupCodeUseCase.kt:26 | **回绕后永远返回 1**：用 `MAX(pickupCode)` 判断，99 回绕后当天最大值恒为 99，之后每单都拿到 "1"（99→1→1→1...同号大量重复）。现有测试只测了"刚到 99 回绕"没测"回绕之后再分配" | 逻辑缺陷 | P1.5 | `:core:domain:test` | **已修复**（commit c1716640：MAX→当天计数取模，`countPickupCodesSince`；补2个回绕回归测试 + 3个 PlaceOrder 取餐号边界测试，全绿） |
| F-007 | P2 | core/config/.../TipConfigTest.kt:4-6 | 测试用 `kotlin.test` 断言但模块无该依赖 → `compileTestKotlin` 失败（基线红的一部分） | 构建破坏 | P0 | `:core:config:test` | 已修复（改 org.junit.Assert，零新依赖），验证中 |
| F-008 | P2 | core/hardware/.../HardwareBatch4Test.kt FakeOrderRepo | 上批 `OrderRepository` 接口新增 4 方法时漏更新该 fake → 编译失败（接口变更未全仓搜索实现方） | 构建破坏 | P0 | `:core:hardware:test` | 已修复，验证中 |
| N-001 | 备注 | AllocatePickupCodeUseCase | 多设备同店同时分配可能撞号（各设备本地分配 + last-write-wins 不去重）；99 循环本身允许跨时段重号（行业惯例可接受）。是否需要 server 端集中分配 → 待与用户确认（S3） | 设计权衡 | — | — | 待确认 |
| N-002 | 备注 | server OrderItemsTable | server 表无 `selectedModifiers` 列 → 订单下行后修饰项快照丢失（push 时即未存）。影响：跨端结账小票上看不到修饰项明细（金额不受影响，lineTotal 已含调价并存于订单合计）。需补列+push/pull 两侧扩展 → 后续批次 | 已知限制 | — | — | 遗留 |
| N-003 | 备注 | core/database RoomConflictResolver | 字段级合并逻辑（含 pickupCode/fulfillmentStatus 回退保护）无直接单测——类依赖 org.json（Android），core:database 只有 androidTest 源集 → 列入 P3 设备测试 | 测试缺口 | P3 | `scripts/regression.sh android-db` | 待补 |

## P2 合规审计结果（2026-06-12）

- **规矩2（金额 Long 分）**：扫描 0 违规 ✅
- **规矩6/UI规范（颜色 token）**：17 处违规 → 全部修复（批B，commit 已入库）。新增 designsystem token：`Receipt*` 5 个（小票预览）、`TrendUpRed`/`TrendDownGreen`（红涨绿跌）；顺手修正 RevenueTrendScreen 把 Material 橙 `0xFFFF9800` 误标为"SUNMI 橙"的问题（已改用真正的 `SunmiOrange`）。复扫归零 ✅
- **规矩8（i18n）**：85 处需修复的硬编码文案 → 三批全部修复（A1 settings 54处、A2 checkout/menu/report 19处含 11 处硬编码中文、A3 app/pad 12处+补 values-zh）。复扫剩 5 处豁免：`"- - - -"`、`"→"`、`"#"`、`"%.2f"`、`"%.1f%%"` —— 纯装饰符号/数字格式，语言中性 ✅
- **规矩3（业务逻辑只在 core/feature）**：app/pad/screens 含较厚的 UI 状态逻辑但无金额计算/状态机（购物车合计在 ViewModel 调 domain）→ 可接受，未列违规
- 阶段门：`./gradlew test` BUILD SUCCESSFUL ✅

| F-009 | P0 | core/database/.../Migrations.kt + DatabaseModule.kt | **迁移链 16→17 断裂**：无 MIGRATION_16_17，17.json 从未存在；v16 及更早设备升级即崩溃。考证结论：16.json 与 18.json 除 17→18 所建三表外 SQL 完全一致 → v17 为无 SQL 变更的版本提升 | 数据安全 | P3（用户已批准补建） | MigrationTest `migrate16To20_throughRepairedLink` | **已修复**（补 no-op MIGRATION_16_17 + 接入 addMigrations + 断链回归测试，commit 123273e3） |
| F-010 | P1 | core/database/build.gradle.kts | androidTest 依赖不全（缺 test-core/runner/coroutines-test/room-testing）→ 唯一的 OrderDaoTest **从未编译通过**，"已有 DAO 测试"是假象 | 测试基建 | P3 | `:core:database:compileDebugAndroidTestKotlin` | **已修复**（补 4 个依赖，androidTest 编译绿） |

| F-011 | P0 | core/database/.../Migrations.kt（多处） | **迁移 DDL 与实体 schema 系统性漂移**：14 张表的建表迁移带实体未声明的 DEFAULT 子句；11 处索引名与 Room 生成名不一致或实体未声明（含 `CREATE UNIQUE INDEX` 变体）；任何真实升级路径跑完后 Room 的 onValidateSchema 都会抛 "Migration didn't properly handle" → **升级即崩溃**。全链 1→20 迁移测试首跑即暴露 | 数据安全 | P3 | MigrationTest 13例设备测试 | **已修复**（全部 DDL 对齐实体：去 DEFAULT、索引改 Room 名、删多余索引；13/13 设备测试绿）。注：coupons.code 的唯一约束随多余索引移除——若业务需要唯一性应在实体上声明 `@Index(unique=true)` 并升 schema 版本，已留待后续 |

## P3 测试缺口补齐进展（2026-06-12）

- **MigrationTest 新建**（3例）：19→20 数据保全+新列默认值；16→20 断链回归（F-009）；1→20 全链 schema 校验 —— 待设备运行
- **OrderDaoTest +3例**：countPickupCodesSince 截止过滤 / setPickupCode / observeReadyForPickup 过滤排序 —— 待设备运行
- **KioskViewModelTest 新建**（3例，JVM 已绿）：app:kiosk 脱离零测试名单
- **PickupDisplayViewModelTest 新建**（2例，JVM 已绿）：feature:tables 脱离零测试名单
- P1(a) JVM 集成层覆盖图：收银（CoreFlowTest 下单→结账 + AuthTest 登录）✅ / KDS（KdsTest 开票→bump→READY）✅ / Kiosk（KioskViewModelTest + CoreFlow 自提取号）✅ / 叫号屏（PickupDisplayViewModelTest + OrderSyncPullerTest 下行到达）✅ / server（SyncRoutesTest+SyncEndToEndTest）✅

## P4 Web admin 验证结果（2026-06-12）

- `npm run build`（tsc strict + vite）：**绿**（修复 F-002 后）
- i18n 盘点：9 locale，叶子 key ~1410/个，结构完整性由 TS 类型系统强制（缺 key 编译即报错）。`[TODO:]` 占位翻译：zh-CN/en-US 0 条；zh-TW 270、ms-MY 270、ja-JP/ko-KR/th-TH/vi-VN/ar-SA 各 272 条 → **翻译工作量遗留**（按范围约定只盘点不翻译）
- eslint：`npm run lint` 脚本存在但无 eslintrc 配置文件，执行必败 → 建议后续批次补最小配置（typescript-eslint + react-hooks，需新增 devDependencies，待用户确认 S5）
- vitest：未引入（项目无前端测试框架）→ 建议后续最高价值目标：i18n key 对齐测试、金额格式化函数测试

## P1.5 取餐号 7 镜像点审计结果（2026-06-12）

| 镜像点 | pickupCode | fulfillmentStatus |
|--------|-----------|-------------------|
| ① core/model Order.kt | PASS（String?=null） | PASS（enum 默认 NOT_READY） |
| ② OrderEntity+OrderDao | PASS | PASS（含 observeReadyForPickup 查询） |
| ③ MIGRATION_19_20 | PASS（TEXT 可空） | PASS（TEXT NOT NULL DEFAULT 'NOT_READY'） |
| ④ RoomOrderRepository push payload | PASS | PASS |
| ⑤ RoomConflictResolver 合并 | PASS（缺字段回退 existing） | PASS（非法值回退 existing） |
| ⑥ server OrdersTable | PASS（varchar16 可空） | PASS（varchar32 默认） |
| ⑦ SyncPushProcessor + pull 返回 | PASS（containsKey 区分缺省/置空） | PASS；pull 侧 F-004 修复后 PASS |

边界条件测试：回绕（99→1→2 持续推进，F-006 修复）✅；双回绕 ✅；堂食不分配 ✅；自提分配 ✅；重复下单保号 ✅；跨日重置=设备本地日历日（文档化，多设备时区不一致风险见 N-001）；READY 状态经下行到达 ✅（OrderSyncPullerTest）。

| F-012 | P0 | 全部 6 个 app AndroidManifest.xml | Android 9+ 默认禁明文 HTTP，所有端连本地/内网 server 的请求被系统静默拦截 → **任何同步都不可能发生**（开发与内网部署场景） | 半成品接线 | P1 | 设备冒烟 push 200 OK | **已修复**（usesCleartextTraffic=true ×6，commit 7da4ea5b） |
| F-013 | P0 | core/sync/SyncEngine.kt | SyncEngine 只在启动和网络重连时 flush，会话中产生的 outbox 记录**永不推送**（下单后干等） | 逻辑缺陷 | P1 | SyncEngineTest 周期冲账测试 + 设备冒烟 | **已修复**（周期 flush 默认 5s，commit 7da4ea5b） |
| F-014 | P0 | core/network/HttpRemoteSyncPort.kt | push 请求体缺 `id` 字段，server 反序列化 MissingFieldException → 全部 push 500，重试耗尽后死信 | 同步线上格式 | P1 | HttpRemoteSyncPortTest + server log 200 OK | **已修复**（DTO 补 id，commit 7da4ea5b） |
| F-015 | P1 | core/domain/PlaceOrderUseCase.kt + OrderStateMachine + FireKitchenTicketsUseCase + OrderScreen | **追加点菜流程断裂**：已下单（PLACED/READY）订单加菜后 Place Order 被状态机拒绝且错误静默吞掉（OrderScreen 无 errorMessage 展示）→ 按钮按下无任何反应。连带发现：①重下单时合计会被覆盖成仅新增项金额；②厨票按 status==PLACED 取项会给旧菜重复开票 | 流程断点（P1 实测发现） | P1 | CoreFlowTest 4 例 + KdsTest 去重 1 例 + OrderStateMachineTest 4 例；设备实测 T2 加菜→结账走通 | **已修复**（commit 85a600c2：状态机 onAdditionalItemsFired；合计按全部非退款项重算；厨票排除已开票项；OrderScreen 补错误展示） |
| F-016 | P0 | core/database/RoomOrderRepository.saveItems | ORDER_ITEM push payload 只带 `{id,orderId,status}` —— 菜名/单价/数量/税率等全部丢失，server 落库空值 → 跨端（KDS/叫号屏/admin）看到的订单项无名称、价格为 0 | 同步镜像点缺口（P1.5 审计只覆盖了 Order 两个新字段，未覆盖 item 字段） | P1 | 设备实测：T3 单 server 端 items 含完整名称快照/单价，subtotal 570 分与收银端对账一致 | **已修复**（payload 补全 13 字段对齐 SyncPushProcessor，commit 85a600c2） |
| N-004 | 备注 | core/sync/SyncEngine | push 失败重试耗尽后死信（FAILED）永不复活；且 401（JWT 过期）不触发重新登录。影响：修复部署前积压的记录永久丢失（本次 T1 全部 14 条 + T2 部分记录即此情况）。建议后续：401 触发 re-auth + 死信人工/定时重放机制 | 设计权衡 | — | — | 遗留（P1 改进建议） |

| F-017 | P0 | app/{handheld,kds,kiosk,pad,pickup-display}/AndroidManifest.xml | 五端 manifest 缺 INTERNET + ACCESS_NETWORK_STATE。AndroidNetworkMonitor 启动即 registerNetworkCallback，无权限 → SecurityException 崩溃 → **这五端在真机上根本起不来**（仅 cashier 声明过权限） | 半成品接线 | P1 | 设备冒烟逐端启动 | **已修复**（补两项权限 ×5，commit 52e4c1cd） |
| F-018 | P0 | core/sync/{Menu,Permission,PadConfig}SyncPuller.kt | 三个下行拉取器仅 start+重连时 pull，无周期轮询 → server admin 改动（含菜单）会话中永不下发。表现：kiosk 启动后菜单永远空（首拉早于 seed 即落空，之后不再试） | 逻辑缺陷 | P1 | MenuSyncPullerTest 周期轮询测试 + 设备：kiosk 12 件菜单下发 | **已修复**（统一加 30s 周期轮询，commit ddb65794） |
| F-019 | P1 | feature/order/OrderViewModel.kt | setOrderType 只改 UI state 不落库，下单也不写 type → 外卖/外送队列、报表均按 Order.type 过滤，选了 Takeaway/Delivery 的单永远进不了队列、分类也错 | 数据未持久化 | P1 | 编译 + 逻辑走查 | **已修复**（选择即持久化 + observeOrderItems 回填，commit 76b9e5d3） |
| F-020 | P0 | server SyncPushProcessor processOrder/processOrderItem/processKitchenTicket | 部分推送的缺省字段用硬默认值（DINE_IN/OPEN/0/空串）而非回退已存行 → KDS bump 只推 {id,fulfillmentStatus}、状态更新只推 {id,status} 时，会把订单 status/totals/tableId、订单项菜名/单价、厨票 orderItemIds 全部清成默认值 | 数据安全（部分更新破坏） | P1 | SyncEndToEndTest 部分更新保留字段测试 | **已修复**（所有缺省字段回退 existing，nullable 用 containsKey 区分缺/置空，commit b46163bc） |
| F-021 | P1 | feature/kds/KdsViewModel.kt | KDS 仅在 ticketRepo 事件时构建卡片并读 items；订单项经独立 OrderSyncPuller 晚于厨票到达时卡片不重建 → 厨票无菜品行（厨房看不到要做什么） | 同步时序竞态 | P1 | 设备：厨票正确显示菜品行 | **已修复**（combine ticketRepo+orderRepo 双流，commit 3a8aefe8） |
| F-022 | P1 | feature/kds/KdsScreen.kt | 菜名硬查 `menuItemNameSnapshot["en"]`，但 server/Admin 菜单 key 是 BCP-47 全标签 `en-US`/`zh-CN` → 永远落空回退成 menuItemId（厨房看到 `item-cola`）。本质是名称 Map 跨数据源 key 风格不一致（设备 seeder 用 `en`，server 用 `en-US`） | i18n 缺陷 | P1 | LocalizedNameTest 6 例（含 server-key 回归）+ 设备显示 `1× Cola` | **已修复**（新增 :core:model `localizedName(locale)` 容错扩展，KDS 改用 + 注入 locale；core:model 脱离零测试名单，commit 3a8aefe8） |
| N-005 | 备注 | 名称 Map key 风格不统一 | 设备 DataSeeder 用裸语言 key（`en`/`zh`），server seed/Admin 用 BCP-47（`en-US`/`zh-CN`）。F-022 已用 localizedName 容错兜住，但 OrderScreen/MenuScreen 各有一份私有 localeName 副本（逻辑相同），建议后续统一引用 :core:model 的共享实现并考虑规范化 seeder key | 技术债 | — | — | 遗留（清理建议） |

| F-023 | P0 | app/handheld + core/sync + core/network + server | **员工用户无任何同步通路**：SyncEntityType 无 USER，server 无用户下发端点，客户端也不上推；只有 cashier 跑 DataSeeder。handheld users 表恒空 → 任何 PIN 登录失败，手持端主流程从登录即死锁 | 架构缺口 | P1（用户批准方案B） | UserSyncPullerTest + /sync/users 路由测试 + 设备登录 | **已修复**（commit：新增用户下行同步 GET /sync/users + UserSyncPuller，handheld JWT 后启动；设备验证 PIN 1234 登录成功进桌台页） |
| F-024 | P0 | core/model + Room v21 + core/sync + server | **桌台/客户不下发非种子终端**：tables/customers 仅 cashier 本地种 + server 有，但无下行通路（tables 客户端会 push 但 server no-op 丢弃且不下发；customers 完全不同步）。handheld 登录后桌台页全空 → 手持端主流程卡死。安全下行需 LWW（每端都改本地桌台状态） | 架构缺口（同 F-023 类） | P1（用户批准升 v21 做正确版） | TableSyncPullerTest/CustomerSyncPullerTest + server /sync/tables + TABLE push LWW 测试 + MigrationTest 20→21 + 设备 | **已修复**（commit dfca0635：Room v21 给三表加 updatedAt；Table/Customer 双向 LWW 同步；server 处理 TABLE/CUSTOMER push + GET /sync/tables、/sync/customers；cashier+handheld 接入。**设备验证**：cashier v20→21 迁移无崩溃；fresh handheld 登录后下行同步 8 桌台 3 分区 + 6 客户，桌台页正常渲染） |
| N-006 | 备注 | Reservation 客户端/服务端 schema 不兼容 | 客户端简单模型（guestName/guestCount/scheduledAt-epoch/状态 CONFIRMED·SEATED·NO_SHOW·CANCELLED）vs 服务端富模型（customerName/partySize/date+time 字符串/状态 PENDING…/含 shiftId·confirmationCode 等多字段）。双向同步映射有损（epoch↔date+time、状态枚举不一一对应）。v21 已给客户端 reservations 加 updatedAt（无害、迁移已测），但**同步通路未接**——需先统一模型/设计映射层。建议后续专项 | 设计权衡（S3） | — | — | 遗留 |

> **F-023/F-024 系统性观察**：server→非种子终端的「下行同步」整体不完整。当前仅 menu/permission/order/kitchenTicket/padConfig + 现已补的 user 会下发；**tables（F-024）、reservations、customers 等 server 管理的实体均无下行通路**。cashier 靠本地 DataSeeder 兜住，但 handheld/kds/pad 等不种子的终端拿不到。每补一个都是一次同步线上格式加性变更（S2）。建议成体系决策要下发哪些实体，而非逐个临时加。

## P1 全端主流程验证进展（2026-06-13）

**:app:cashier 台式收银端（最重点）— 主流程全部走通 ✅**
- PIN 登录（2222）→ 开台（设客数→Seat）→ 点单（含修饰项弹窗，cart 金额含调价 $16.00 正确）→ 下单（桌台→ORDERED，厨票生成）→ 结账页（小计/合计正确）→ CASH 支付 → 桌台→DIRTY ✅（T1、T3 两单全程）
- **追加点菜**（实测发现 F-015，修复后）：已下单桌台加菜→Place Order→自动跳结账→合计含全部项 ✅（T2：2×Cola=$5.00）
- **同步链路**（修复 F-012/013/014/016 后）：下单/结账动作 5s 内 push 200 OK；server `/sync/pull` 返回订单+完整 items；金额对账：T3 server subtotal=570 分 == 收银端 $5.70 ✅
- 历史死信不恢复（N-004）：T1/T2 修复前积压的 outbox 记录维持 FAILED，属已知限制

**:app:kiosk 自助点餐端 ✅**：菜单下发（修 F-018 后）→ 点 Cola → 下单 → 确认页显示取餐号 1 + 倒计时返回 ✅
**:app:kds 厨显端 ✅**：厨票出现（修 F-017 启动崩溃后）→ 菜品行正确显示 `1× Cola`（修 F-021 时序 + F-022 locale key 后）→ bump → 订单 fulfillmentStatus=READY_FOR_PICKUP ✅
**:app:pickup-display 叫号屏 ✅**：bump 后经 server 下行，叫号屏显示取餐号「1」（Ready for Pickup）✅
**完整取餐链路端到端打通**：Kiosk 下单(取号1) → KDS 厨票 → bump → READY → server 中转 → 叫号屏显示「1」。截图存 `.tmp/smoke/`

**:app:handheld 手持端 ✅**：修 F-017 启动崩溃 + F-023 用户下行 + F-024 桌台/客户下行后 → fresh 设备登录（PIN 1234）→ 桌台页正常显示 8 桌（main-hall/patio/private-room 三分区，均 AVAILABLE，全部经 server 下行同步）✅。截图 `.tmp/smoke/handheld-floor-synced.png`。cashier 侧 /sync/tables 拉取已鉴权工作（跨端桌台状态通路打通）
待验证：:app:pad 桌边端主流程 + web admin 对账（pad 无登录、纯桌边点餐）

## 最终门结果（2026-06-13）

- **全量 JVM 测试 `./gradlew test`**：BUILD SUCCESSFUL（所有模块，含 F-015/016/019/020/021/022/023/024 新增用例）✅
- **Web 构建 `npm run build`**：✅（tsc strict + vite，10.27s；仅 chunk>500kB 告警，非错误）
- **设备 android-db `:core:database:connectedDebugAndroidTest`**：✅（迁移 20→21 数据保全+客户回填、全链 1→21 schema 校验、OrderDao/桌台 updatedAt 断言全绿）
- **git 历史**：每批一 commit，按 fix/feat/docs/test 分类入库

**P1 全端主流程覆盖小结**：
| 端 | 状态 | 说明 |
|----|------|------|
| :app:cashier（最重点） | ✅ 完整 | 登录→开台→点单（含修饰项）→下单→结账（现金）→桌台流转→报表；追加点菜（F-015）；同步上行下行 |
| :app:kds | ✅ 完整 | 厨票出现（修 F-017）→菜名正确（F-021/022）→bump→订单 READY |
| :app:kiosk | ✅ 完整 | 菜单下发（F-018）→点餐→下单→取餐号确认页 |
| :app:pickup-display | ✅ 完整 | bump 后经 server 下行显示取餐号 |
| :app:handheld | ✅ 完整 | 启动（F-017）→用户下行登录（F-023）→桌台下行（F-024）→桌台页正常 |
| :app:pad | ⚠️ 未实机冒烟 | 纯桌边点餐、无登录；核心流程依赖已验证的 menu/padConfig 下行 + order 上行（绑定 boundTableId 取自同步的 PadConfig，不需全桌台列表）。建议后续补一次实机冒烟 |

**遗留（已记录，非阻塞）**：N-001 取餐号多设备撞号、N-002 订单项修饰项快照、N-004 死信不复活/401 不重认证、N-005 名称 Map key 风格不统一、N-006 reservation 客户端/服务端 schema 不兼容（同步通路未接）。

## 基线记录

- git 基线 commit：`01ee2b87`（审查前未提交的取餐号功能全部入库）
- JVM 测试基线：首跑 FAILED（F-001）；修复后重跑中
- Web 构建基线：首跑 FAILED（F-002）；修复后重跑中
- 设备探测：`adb devices` 无设备；SDK 未安装 emulator 包与 system-images → **S4 阻塞**（影响 P1 实机冒烟、P3 迁移/DAO 测试），待用户决定：连真机 / 安装模拟器 / 降级方案
