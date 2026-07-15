# CLAUDE.md — 餐饮 POS 安卓项目

## 硬规矩（不可违反）

1. **Room 本地库 = 唯一权威源**：写操作先落本地再异步同步；冲突 last-write-wins + 服务端权威字段。
2. **金额一律 `Long`（分）**：禁止使用 `float`/`double` 表示金额。
3. **业务逻辑只在 `:core` / `:feature`**：`:app` 只做 UI + 权限装配。
4. **硬件全走抽象接口**：SUNMI 实现 + Mock 实现，两套并存。
5. **四端共用一套领域模型**：端内禁写业务逻辑。
6. **货币/语言/税/格式全部读 `:core:config`**：禁止任何国家常量、税率、合规分支硬编码。
7. **`:core`、`:feature` 不得依赖或感知任何 `:market:*`**。
8. **i18n 从第一行做**：菜单内容也要多语言，不可 hardcode 中文/英文字符串。
9. **每个功能配测试**：DAO 测试 / 状态机单测 / 金额计算单测，缺一不可。

## 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose + Material 3 Expressive
- **主色**：SUNMI 橙 `#FF5C00`，禁用 dynamic color
- **数据库**：Room
- **异步**：Coroutines + Flow
- **架构**：MVVM / Clean Architecture

## 模块结构

```
:core:model        领域模型，纯 Kotlin，无 Android 依赖
:core:config       区域/货币/税/格式 配置引擎 + Market Pack 的 SPI
:core:database     Room / DAO（权威源）
:core:domain       用例 + 状态机
:core:hardware     硬件抽象 + SUNMI实现 + Mock实现
:core:sync         同步引擎（Batch 5 实现）
:core:designsystem 主题 + 共享组件
:feature:order     点单功能
:feature:tables    桌台管理
:feature:checkout  收银结账
:feature:kds       厨显
:app:cashier       主机收银端（本期主力）
:app:handheld      手持点单端
:app:kds           厨显端
:app:kiosk         自助点餐端
:market:<country>  各国适配包，按需新增，核心不依赖
```

## UI 规范

- M3 Expressive：用 MotionScheme 动效 token、形状 morph、shared element 过渡。
- 主题集中 `:core:designsystem`，所有界面从这里取 token，禁止在 feature/app 模块定义颜色常量。
- **动效克制**：点单录入、金额变化必须即时无动画；只在按钮按压、桌台变色、KDS 入单/bump、页面切换加动效；不掉帧，列表用 `key`。

## Git 分支规范

- 每个 Batch 一条分支：`batch/<n>-<name>`
- 每个可验证任务一个 commit，commit message 格式：`feat(<module>): <描述>`
- 合并前必须通过对应 Batch 的验收标准

## 状态机（严格实现 + 单测）

| 实体 | 状态流转 |
|------|----------|
| 订单 | 草稿→(加首项)进行中→(下单)已下单→(全部出品)待结账→(支付完成)已结；任意未结→(作废,需提权)已作废 |
| 订单项 | 待下单→已下单→制作中→已出品（退/赠为分支） |
| 桌台 | 空闲→(开台)已占用→(首次下单)已下单→(发起结账)结账中→(完成)待清理→(清台)空闲；空闲→(预订)预订锁定 |
| 支付 | 未支付→支付中→部分支付→(补付)已支付；已支付→(退款)已退款 |
| KDS | 新建→制作中→完成(bump)（召回为分支） |
