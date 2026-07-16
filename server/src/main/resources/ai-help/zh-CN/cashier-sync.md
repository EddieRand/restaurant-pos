---
id: cashier-sync
title: Cashier 订单同步
route: /orders
keywords: Cashier,收银,同步,结账,订单没出现,离线
lastVerifiedAt: 1784102400000
---
## 检查 Cashier 同步

1. 确认 Cashier 已完成现金结账，订单状态为已结。
2. 确认 Cashier 连接的服务端地址可访问。
3. 在 Web“订单”页等待自动刷新。
4. 若订单仍未出现，检查 Cashier 的同步队列和服务端健康状态。

Demo 模拟器连接本机服务端时使用 `http://10.0.2.2:8080`。Web 管理后台使用本机 Ktor 服务端 `8080`。
