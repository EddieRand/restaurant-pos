# 报表能力差距分析与补齐计划

> 日期: 2026-06-07 | 主对标: Toast POS / Lightspeed K-Series / Square for Restaurants / TouchBistro / GloriaFood / Clover | 参考竞品: Restaurant365 / CrunchTime! / Eat App

## 一、现状盘点

### 已有报表能力（4 个模块）

| 模块 | 平台 | 覆盖内容 |
|------|------|---------|
| **交班报表 (Shift Report)** | Android + Web Admin + Server API | 订单数/客人数、毛/净营收、折扣/税/服务费/小费、均单/人均、支付方式分布(CASH/CARD/QR)、热销菜品 Top 5、交班结算(现金对账+Z-Report 打印) |
| **订单历史 (Order History)** | Android + Web Admin | 按桌台号搜索、按订单状态筛选(CLOSED/VOIDED)、日期范围查询 |
| **仪表盘 (Dashboard)** | Web Admin | 今日净营收/订单数/客人数/均单/折扣/毛营收/人均、支付方式分布条形图、库存预警 |
| **库存报表 (Inventory)** | Web Admin | 物料收发汇总、按供应商采购金额、低库存预警、CSV 导出 |

### 核心架构

```
ShiftReportUseCase (core:domain)
  └── OrderRepository.getClosedInRange() → 聚合订单
  └── PaymentRepository.getByOrder()     → 聚合支付
  └── ShiftReport 数据类                → 单一时段快照报表
```

**底层问题**: 报表架构只设计了"单一时段快照"模式，缺少时序数据模型，无法支撑趋势分析。

---

## 二、海外竞品对标矩阵

### 对标系统简介

| 竞品 | 定位 | 目标客群 | 报表能力等级 |
|------|------|---------|:---:|
| **Toast POS** | 全栈餐饮操作系统 | 全规模餐厅（SMB→Enterprise） | ⭐⭐⭐⭐⭐ |
| **Lightspeed Restaurant (K-Series)** | 云端多门店 POS | 独立餐厅、酒店集团 | ⭐⭐⭐⭐⭐ |
| **Square for Restaurants** | 轻量易用 POS | QSR/快餐/小餐厅 | ⭐⭐⭐ |
| **TouchBistro** | iPad 原生 POS | 全服务餐厅 | ⭐⭐⭐⭐ |
| **GloriaFood** | 在线订餐+POS | 外卖/线上餐厅 | ⭐⭐⭐ |
| **Clover** | 一体化商业平台 | 小商家/快餐 | ⭐⭐⭐ |
| **Restaurant365** | POS+会计一体化 | 多门店连锁 | ⭐⭐⭐⭐⭐ |

### 完整对标矩阵

| 报表能力 | Toast | Lightspeed | Square | TouchBistro | GloriaFood | Clover | **本项目** |
|----------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **班次/日结销售汇总** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **日/周/月/年营收趋势** | ✅ | ✅ | ✅ (WoW/YoY) | ✅ | — | ✅ | ❌ |
| **时段营收分析（小时/班次）** | ✅ | ✅ Hourly | — | ✅ | — | — | ❌ |
| **同比/环比对比** | ✅ | ✅ Benchmark | ✅ | ✅ | — | ✅ | ❌ |
| **菜品分类营收排名** | ✅ | ✅ | ✅ Category | ✅ | ✅ #1,#2 | ✅ | ❌ |
| **单品全量分析（销量/趋势）** | ✅ | ✅ | ✅ Item | ✅ | ✅ #3 | ✅ | ⚠️ 仅 Top5 |
| **单品成本与毛利分析** | — | — | — | ✅ | — | — | ❌ |
| **滞销品/零销量识别** | — | — | — | — | — | — | ❌ |
| **折扣/优惠活动效果** | ✅ | ✅ | ✅ | ✅ | ✅ #1 | ✅ | ⚠️ 仅总额 |
| **员工绩效报表** | ✅ | ✅ Staff | — | ✅ | ✅ #5,#8,#9 | ✅ | ❌ |
| **员工小费报表** | ✅ | — | ✅ Tips | ✅ | ✅ #6 | ✅ | ❌ |
| **收银员对账** | ✅ | ✅ | ✅ | ✅ | ✅ #5 | ✅ | ❌ |
| **客户分析/CRM** | — | — | — | — | — | ✅ | ❌ |
| **渠道分析（堂食/外卖/自提）** | ✅ | ✅ | ✅ Source | ✅ | — | — | ❌ |
| **退菜/作废分析** | — | ✅ | — | ✅ | — | — | ❌ |
| **多门店集中看板** | ✅ | ✅ Location | — | — | ✅ #4 | — | ❌ |
| **实时营业仪表盘** | ✅ | ✅ | ✅ | ✅ | — | ✅ | ⚠️ 静态 |
| **自定义报表/BI 灵活查询** | ✅ | ✅ | — | ✅ | — | ✅ | ❌ |
| **PDF/Excel/CSV 导出** | ✅ | ✅ Auto-email | ✅ Email | ✅ Print | ✅ | ✅ | ⚠️ 仅CSV |
| **移动端老板看板** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| **营业预测/趋势预测** | — | — | — | — | — | ✅ Insights | ❌ |
| **行业基准对标** | — | ✅ Benchmark | — | — | — | — | ❌ |
| **劳动力成本率追踪** | — | — | — | ✅ | — | — | ❌ |
| **菜品工程矩阵（明星/谜题/主力/狗）** | — | — | — | — | — | — | ❌ |
| **翻台率与上座率** | — | — | — | ✅ | — | — | ❌ |
| **定时自动推送报表** | ✅ | ✅ | ✅ Daily | ✅ | ✅ | ✅ | ❌ |

> **#数字** 表示 GloriaFood 12 种必备报表中的序号

**差距汇总**: 26 项海外竞品标配能力中，本项目覆盖 **4 项**（含部分覆盖 3 项），硬缺口 **19 项**。

---

## 三、差距分优先级与影响分析

### P0 — 致命缺失（任何餐厅运营都必须）

| 序号 | 报表能力 | 对标依据 | 业务影响 | 技术难度 |
|:---:|---------|---------|---------|:---:|
| 1 | **日/周/月营收趋势** | Toast/Sq/LS/TB/Clover 全有 | 无法判断经营走势 | ⭐⭐ |
| 2 | **菜品分类营收排名** | 6/6 竞品全有 | 哪类菜赚钱完全不知道 | ⭐ |
| 3 | **单品全量分析** | 目前仅 Top5，6/6 竞品全有 | 无法做菜单优化决策 | ⭐ |
| 4 | **收银员对账** | Toast/LS/Sq/TB 标配 | 多人收银现金差无法追溯 | ⭐⭐ |

### P1 — 竞争力短板（中端以上餐厅必备）

| 序号 | 报表能力 | 对标依据 | 业务影响 | 技术难度 |
|:---:|---------|---------|---------|:---:|
| 5 | **时段营收分析** | Toast Hourly + LS Hourly | 不能按午市/晚市调配资源 | ⭐⭐ |
| 6 | **菜品成本/毛利分析** | TouchBistro 支持 | 无法评估单品盈利能力 | ⭐⭐⭐ |
| 7 | **退菜/作废分析** | Lightspeed + TouchBistro 有 | 无法发现后厨问题 | ⭐ |
| 8 | **折扣/活动效果分析** | 仅显示总额，竞品有明细 | 营销花了钱不知道效果 | ⭐⭐ |
| 9 | **实时营业仪表盘** | Toast/Sq/LS/Clover 全有 | 当前需手动刷新，老板无法远程看 | ⭐⭐⭐ |

### P2 — 增值差异（规模化经营需要）

| 序号 | 报表能力 | 对标依据 | 技术难度 |
|:---:|---------|---------|:---:|
| 10 | **员工绩效 + 小费报表** | Toast/TB/GloriaFood 标配 | ⭐⭐⭐ |
| 11 | **劳动力成本率追踪** | TouchBistro 支持 | ⭐⭐⭐ |
| 12 | **渠道分析（堂食/外卖/自提）** | Toast/LS/Square 有 Order Source | ⭐⭐ |
| 13 | **多门店集中看板** | 独家门店暂不需要，但架构应预留 | ⭐⭐⭐⭐ |
| 14 | **移动端老板看板** | 6/6 竞品均有 | ⭐⭐⭐⭐ |
| 15 | **PDF 导出 + 定时邮件推送** | LS Auto-email, Sq Daily Email 标配 | ⭐⭐ |
| 16 | **翻台率与上座率** | TouchBistro 支持 | ⭐⭐⭐ |
| 17 | **菜品工程矩阵** | 行业最佳实践（明星/主力/谜题/狗） | ⭐⭐ |
| 18 | **自定义报表/BI** | Toast/LS/TB/Clover 有 | ⭐⭐⭐⭐⭐ |
| 19 | **营业预测/趋势预测** | Clover Insights 有 ML 预测 | ⭐⭐⭐⭐⭐ |

---

## 四、补齐方案 — 分阶段开发计划

### Phase 1: 时序数据底座 + 营收趋势（6 个工作日）

**对标**: Toast Sales Trends / Square WoW+YoY / Lightspeed Sales Summary

**目标**: 建立可支撑趋势分析的数据模型

```
新增 core/domain:
├── DailyReportUseCase         # 日聚合报表（多日汇总）
├── TrendDataPoint             # 数据模型: (date, netRevenue, orderCount, guestCount, avgCheck, ...)
└── ReportRepository           # 报表持久化接口（缓存预计算）

新增 core/model:
└── DailySnapshot (Room Entity)
    ├── date: String (yyyy-MM-dd, PK)
    ├── netRevenue: Long, grossRevenue: Long
    ├── orderCount: Int, guestCount: Int
    ├── avgCheck: Long, avgPerGuest: Long
    ├── discountTotal: Long, taxTotal: Long
    ├── serviceChargeTotal: Long, tipTotal: Long
    └── paymentBreakdown: String (JSON)

Server API:
├── GET /admin/reports/trend?from=2026-05-01&to=2026-06-07&granularity=day|week|month
│   返回: { "dataPoints": [{date, netRevenue, orderCount, guestCount, avgCheck, ...}], "summary": {...} }
└── POST /admin/reports/snapshot/regenerate  # 手动触发预计算（兜底）

Web Admin 改造:
├── ReportPage → 增加 Recharts LineChart（日/周/月切换）
├── DashboardPage → 本周 vs 上周对比卡片（↑12% / ↓8%）
└── 新增 TrendReportPage Tab

Android 改造:
└── ShiftReportScreen 底部追加昨日对比条
```

### Phase 2: 菜品分析矩阵（8 个工作日）

**对标**: Toast Menu Performance / TouchBistro Menu Reports / GloriaFood #1-#3

**目标**: "什么菜赚钱/什么菜该下架"一目了然

```
新增 core/domain:
├── MenuItemAnalyticsUseCase
│   ├── getCategoryRanking(from, to) → List<CategorySales>
│   ├── getItemRanking(from, to, sortBy=sales|revenue|margin) → List<ItemSales>
│   ├── getZeroSalesItems(from, to) → List<MenuItem>
│   └── getItemTrend(itemId, days) → List<DailyItemSales>
├── MenuEngineeringUseCase
│   └── classifyItems() → MenuEngineeringMatrix (Star/Puzzle/Plowhorse/Dog)

Model 追加:
└── MenuItem.costMinorUnit       # 单品成本（支持毛利计算）

Server API:
├── GET /admin/reports/menu/items?from=&to=&sort=sales&limit=50
├── GET /admin/reports/menu/categories?from=&to=
├── GET /admin/reports/menu/item-trend/{itemId}?days=30
├── GET /admin/reports/menu/zero-sales?from=&to=
└── GET /admin/reports/menu/engineering?from=&to=      

Web Admin 新增:
├── MenuReportPage
│   ├── Tab1: 热门榜（按销量/营收/毛利排序，分类筛选）
│   ├── Tab2: 滞销榜（零销量 + 低销量）
│   └── Tab3: 菜品工程矩阵（散点图: X=人气, Y=利润）
└── 单菜品详情页（30天折线趋势）

Android 改造:
└── ShiftReportScreen → TopItems 从 5 → 20，加分类 Tab 切换
```

### Phase 3: 时段分析 + 经营诊断（5 个工作日）

**对标**: Toast Hourly Sales / Lightspeed Hourly Sales Report

**目标**: 知道午市/晚市哪个时段最赚钱

```
新增 core/domain:
├── DaypartAnalysisUseCase
│   ├── getHourlySales(date) → List<HourlySalesPoint>
│   ├── getDaypartSummary(from, to, daypart) → DaypartSummary
│   └── getPeakHours(days) → PeakHourAnalysis
├── Daypart enum: BREAKFAST(6-10) / LUNCH(10-14) / TEA(14-17) / DINNER(17-21) / NIGHT(21-2)
├── VoidAnalysisUseCase         # 退菜/作废分析
│   ├── getVoidRateTrend(days)
│   └── getVoidByReason(from, to) → 按取消原因分组

Server API:
├── GET /admin/reports/daypart?from=&to=&buckets=1h|2h|4h
├── GET /admin/reports/peak-hours?days=30
├── GET /admin/reports/voids?from=&to=
└── GET /admin/reports/voids/by-reason?from=&to=

Web Admin 新增:
├── ReportPage → "时段分析" Tab
│   ├── 小时柱状图 (Recharts BarChart, 24h or 营业时段)
│   └── 日段饼图 (BREAKFAST/LUNCH/DINNER/NIGHT)
└── 退菜分析页（退菜率趋势 + 按原因分类）

Android 新增:
└── 交班报表追加时段分布水平条
```

### Phase 4: 收银对账 + 员工绩效（8 个工作日）

**对标**: TouchBistro Shift Report / GloriaFood #5-#9 / Lightspeed Staff Reports

**目标**: 多人收银可追溯，现金差错可追责

```
新增 core/domain:
├── CashierReconciliationUseCase
│   ├── per cashier: 开单数、营收、现金实收 vs 预期、差异金额
│   └── 差异 > 阈值自动标记告警
├── StaffPerformanceUseCase
│   ├── per staff: 开单数/营收/均单/追销率/小费率
│   ├── 退菜率、折扣率（监控潜在舞弊）
│   └── 时段绩效（早班 vs 晚班对比）

新增 core/model:
├── Order.cashierId              # 订单关联收银员
├── ShiftReconciliation          # (staffId, date, shift, expectedCash, actualCash, variance)

Server API:
├── GET /admin/reports/cashier-reconciliation?date=
├── GET /admin/reports/staff/performance?from=&to=
├── GET /admin/reports/staff/tips?from=&to=
└── GET /admin/reports/staff/{staffId}/detail?from=&to=

Web Admin 新增:
├── StaffReportPage
│   ├── Tab1: 收银员对账（预期 vs 实收，差异高亮）
│   ├── Tab2: 员工绩效排行（营收/开单/均单/追销）
│   └── Tab3: 小费汇总

Android 改造:
└── 交班结算对话框按收银员分组展示
```

### Phase 5: 实时看板 + 导出增强 + 自动化（6 个工作日）

**对标**: Toast Real-time Dashboard / Lightspeed Auto-email / Square Daily Email

**目标**: Web Admin 自动刷新 + 多格式导出 + 定时推送

```
Server 改造:
├── WebSocket 推送 (营业数据变更实时通知)
├── GET /admin/reports/export/pdf?type=shift&date=  # 生成 PDF 报表
├── GET /admin/reports/export/excel?type=menu&from=&to=  # Excel 导出
└── POST /admin/reports/schedule-email  # 定时邮件配置

Web Admin 改造:
├── DashboardPage → WebSocket 实时刷新
├── 所有报表页追加 PDF/Excel 导出按钮
├── 自动刷新开关（15s/30s/60s/手动）
└── ReportSchedulePage（定时推送配置）

Android 改造:
└── 交班报表追加 PDF 分享按钮 (Android Print API)
```

### Phase 6: 高级分析（20 个工作日，可选）

**对标**: Clover Insights ML / Restaurant365 P&L / TouchBistro Table Turnover

**目标**: 规模化经营 + 财务级分析

```
新增能力:
├── ChannelAnalyticsUseCase     # 堂食 vs 外卖 vs 自提营收对比
├── TableTurnoverUseCase        # 翻台率、平均用餐时间、上座率
├── LaborCostRatioUseCase       # 劳动力成本 / 营收比率
├── MultiStoreReportUseCase     # 跨门店对比、排名、汇总
├── ForecastUseCase             # 基于历史数据的销售预测（简单回归）
└── PnLReportUseCase            # 简化版损益表

前置依赖:
├── 会员系统 (Member 模型)
├── 渠道标识 (Channel enum: DINE_IN/TAKEOUT/DELIVERY)
├── 多门店架构 (Store 模型)
└── 劳动力成本数据源 (排班时间 × 时薪)

Server API:
├── GET /admin/reports/channel?from=&to=
├── GET /admin/reports/table-turnover?from=&to=
├── GET /admin/reports/labor-ratio?from=&to=
├── GET /admin/reports/pnl?month=
└── GET /admin/reports/forecast?days=7
```

---

## 五、架构升级：从"单一快照"到"多维分析"

### 当前架构问题

```
ShiftReportUseCase(fromEpoch, toEpoch) → 单次查询 → ShiftReport
     ↓
  每次请求都实时聚合所有 CLOSED 订单
     ↓
  数据量大时性能崩（O(n) 全表扫描）
```

### 目标架构

```
┌─────────────────────────────────────────────────────────┐
│                   ReportRepository                       │
│  ┌──────────────┐ ┌───────────────┐ ┌────────────────┐  │
│  │ DailySnapshot │ │ HourlySummary │ │ ItemDailySummary│  │
│  │ (date, rev,  │ │ (date, hour,  │ │ (date, itemId,  │  │
│  │  orders,     │ │  rev, orders, │ │  qty, rev,      │  │
│  │  guests,...) │ │  guests)      │ │  cost, margin)  │  │
│  └──────────────┘ └───────────────┘ └────────────────┘  │
└─────────────────────────────────────────────────────────┘
         ↑ 每晚定时 ETL 预计算（WorkManager 凌晨 2:00）

┌─────────────────────────────────────────────────────────┐
│              ReportAnalyticsUseCase                      │
│  ├── getTrend(days) → List<DailySnapshot>                │
│  ├── getDaypartReport(date) → List<HourlySummary>        │
│  ├── getItemRanking(from, to) → List<ItemDailySummary>   │
│  ├── getYoY/MoM comparison                               │
│  └── getForecast(days) → 简单线性回归预测                  │
└─────────────────────────────────────────────────────────┘
```

### 关键技术决策

| 决策点 | 方案 | 理由 |
|--------|------|------|
| **预计算 vs 实时聚合** | 日快照预计算 + 实时当日补算 | 历史数据预计算避免全表扫；当日数据量小实时查 |
| **ETL 时机** | WorkManager 每日凌晨 2:00 | 非营业时间，数据稳定，失败可重试 |
| **时序存储** | Room `DailySnapshot` / `HourlySummary` / `ItemDailySummary` 三表 | Daily: 365 行/年, Hourly: ~5000 行/年, Item: ~18000 行/年，查询极快 |
| **图表库** | Web: Recharts（已有）/ Android: Vico | Recharts 已在项目中使用，Vico 是 Compose 原生图表库 |
| **实时推送** | Server: Ktor WebSocket | 已有 Ktor server，零额外依赖 |
| **PDF 生成** | Server: Apache PDFBox | JVM 原生，无外部依赖 |
| **定时推送** | Server: Quartz Scheduler | 成熟调度框架，支持 cron 表达式 |

---

## 六、工作量估算

| Phase | 内容 | 后端 | Web Admin | Android | 总计 |
|-------|------|:---:|:---:|:---:|:---:|
| P1 | 营收趋势 + 时序底座 | 3d | 2d | 1d | **6d** |
| P2 | 菜品分析矩阵 | 3d | 3d | 2d | **8d** |
| P3 | 时段分析 + 退菜诊断 | 2d | 2d | 1d | **5d** |
| P4 | 收银对账 + 员工绩效 | 3d | 3d | 2d | **8d** |
| P5 | 实时看板 + 导出自动化 | 2d | 3d | 1d | **6d** |
| P6 | 高级分析（渠道/翻台/预测） | 8d | 8d | 4d | **20d** |
| **合计** | | **21d** | **21d** | **11d** | **53d** |

**如果 P6 暂缓：P1-P5 合计 33 个工作日（约 6.5 周）**

---

## 七、竞品对标深度分析

### Toast POS — 最全面的报表体系

| 报表域 | 具体能力 |
|--------|---------|
| Sales | Daily/Weekly/Monthly trends, Hourly sales, Sales by menu category/item, Discount & comp analysis, Payment method breakdown |
| Labor | Staff performance metrics, Tip reports, Labor cost vs sales ratio |
| Menu | Item popularity ranking, Menu engineering insights, Seasonal trend identification |
| Operations | Real-time dashboard, Custom report builder, Multi-location consolidation |
| Distribution | Auto-scheduled email reports, PDF/CSV export, API access for BI tools |

**可取之处**: 实时仪表盘 + 自定义报表 + API 开放是核心差异化

### Lightspeed Restaurant (K-Series) — 最强的自动化报表

| 报表域 | 具体能力 |
|--------|---------|
| Sales | Sales Summary dashboard, Hourly Sales (图形化), Location Summary (多门店) |
| Fiscal | 财年汇总报表，用于税务申报 |
| Staff | 员工班次销售关联、分时段绩效 |
| Orders | 已开/已结/预结/交易全量明细 |
| Discounts | 折扣与调整类操作明细 |
| Automation | **最多 5 份报表定时自动邮件推送** + 自定义报表班次 |
| Benchmark | **同类型/同区域商家数据对比**（行业对标） |

**可取之处**: 报表自动化 + 行业对标 Benchmark 是独特优势

### TouchBistro — 最强的员工与运营报表

| 报表域 | 具体能力 |
|--------|---------|
| Menu | 菜品销售报表、单品成本与毛利、库存关联 |
| Staff | 员工财务报表、员工小费报表、未结/已结账单报表、员工流水报表、详细班次报表 |
| Operations | 翻台率分析、未来预结账单（预判高峰） |
| Audit | 账单流水（防欺诈审计）、订单重开追踪 |
| Print | iPad 端直接打印所有报表 |

**可取之处**: 防欺诈审计（频繁重开账单检测）+ 预结账单预判高峰是独特能力

### Square for Restaurants — 最简洁的日报系统

| 报表域 | 具体能力 |
|--------|---------|
| Daily Summary | **自动邮件推送**（营业结束后 1.5h 发送），含 WoW/YoY 对比 |
| Sales | Gross/Net sales, Item sales, Category sales, Order source sales |
| Payments | Tips rate, Service charges, Discounts & comps detail |
| Multi-channel | 堂食 vs 外卖 vs 自提分渠道统计 |

**可取之处**: 日报自动推送 + YoY/WoW 对比 + Order Source 拆分是亮点

### CrunchTime! — 最强的供应链与成本管控（参考竞品）

| 报表域 | 具体能力 |
|--------|---------|
| Recipe/Cost | 配方成本计算、理论成本 vs 实际成本差异、菜品工程利润分析 |
| Inventory | 库存循环盘点、Par 水平自动补货、供应商管理、采购订单自动化 |
| Production | 厨房生产计划（ChefTec）、按预订量预判备料量 |
| Labor | 排班优化、工时成本追踪、劳动力成本率实时监控 |
| Analytics | 异常损耗预警、供应商价格趋势、门店间成本对比 |

**可取之处**: 理论成本 vs 实际成本差异分析是餐饮供应链的核心能力，本项目 Phase 6 的菜品成本分析可借鉴其思路；生产计划与预订联动是"预结账单预判高峰"的进阶版。

### Eat App — 最强的客CRM与营销自动化（参考竞品）

| 报表域 | 具体能力 |
|--------|---------|
| CRM | 客人画像（用餐偏好/过敏原/投诉记录）、到店频率/消费金额/平均菜品单价 |
| Reservations | 预订管理、Waitlist 管理、桌位分配优化、NO-SHOW 率统计 |
| Marketing | 邮件/SMS 营销自动化、沉睡客户唤醒、生日/纪念日营销、评价管理 |
| Reviews | 多平台评价聚合、情感分析、差评预警 |
| Analytics | 客户生命周期价值 (CLV)、复购率、营销 ROI |

**可取之处**: 客人 CRM 与复购率分析是餐饮长期经营的核心，本项目 CRM 模块可参照其客户画像 + 营销自动化设计；评价管理对品牌声誉至关重要。

---

## 八、立即可落地的 Quick Wins（本周可完成，零后端改动）

1. **Web Admin ReportPage 增加折线趋势图** — 调 N 次现有 API 前端拼多日数据，无需后端改动
2. **Top 5 → Top 20** — `ShiftReportUseCase` 默认参数从 5 改为 20
3. **CSV 导出补充菜品明细** — 现有仅支付方式列，追加热销菜品列表
4. **Android 交班报表底部追加简单趋势条** — 显示"较昨日 ↑12% / ↓8%"
5. **Dashboard 加本周 vs 上周对比卡** — 前端纯算（需两日数据差）

---

## 附录：GloriaFood 12 种必备报表速查

| # | 报表名称 | # | 报表名称 |
|:--:|---------|:--:|---------|
| 1 | 主要分组销售报表（菜单大类） | 7 | 员工未结账单报表 |
| 2 | 次要分组销售报表（菜单子类） | 8 | 员工已结账单报表 |
| 3 | 菜品销售报表 | 9 | 员工流水报表 |
| 4 | 门店财务报表 | 10 | 未来预结账单报表 |
| 5 | 员工财务报表 | 11 | 账单流水报表 |
| 6 | 员工小费报表 | 12 | 税费汇总报表 |
