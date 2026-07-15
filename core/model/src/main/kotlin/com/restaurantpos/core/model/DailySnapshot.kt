package com.restaurantpos.core.model

/**
 * 日聚合快照 - 用于时序数据分析和趋势报表
 *
 * 每晚通过 WorkManager 定时任务（ETL）从订单数据预计算生成，
 * 避免每次查询都实时聚合所有历史订单。
 *
 * @param date 日期字符串 (yyyy-MM-dd)，作为主键
 * @param netRevenueMinorUnit 净营收（扣除退款后）
 * @param grossRevenueMinorUnit 毛营收（未扣除退款）
 * @param orderCount 订单总数
 * @param guestCount 客人总数
 * @param avgCheckMinorUnit 均单（平均每张订单金额）
 * @param avgPerGuestMinorUnit 人均消费
 * @param discountTotalMinorUnit 折扣总额
 * @param taxTotalMinorUnit 税额总计
 * @param serviceChargeTotalMinorUnit 服务费总计
 * @param tipTotalMinorUnit 小费总计
 * @param paymentBreakdownJson 支付方式分布（JSON 字符串）
 * @param createdAt 快照创建时间戳
 * @param updatedAt 快照最后更新时间戳
 */
data class DailySnapshot(
    val date: String,

    val netRevenueMinorUnit: Long = 0L,
    val grossRevenueMinorUnit: Long = 0L,
    val orderCount: Int = 0,
    val guestCount: Int = 0,

    val avgCheckMinorUnit: Long = 0L,
    val avgPerGuestMinorUnit: Long = 0L,

    val discountTotalMinorUnit: Long = 0L,
    val taxTotalMinorUnit: Long = 0L,
    val serviceChargeTotalMinorUnit: Long = 0L,
    val tipTotalMinorUnit: Long = 0L,

    val paymentBreakdownJson: String = "{}",

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
