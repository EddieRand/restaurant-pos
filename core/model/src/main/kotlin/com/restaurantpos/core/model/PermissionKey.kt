package com.restaurantpos.core.model

/**
 * 细粒度权限位（按功能域分组，字符串 key 持久化到 DB）。
 *
 * 与旧 [Permission] 枚举的区别：
 * - 27 个权限位（vs 旧 7 个）
 * - 按 6 个功能域分组（order / payment / menu / report / settings / staff）
 * - 字符串 key（如 "order.create"）写入 DB，与后端保持一致
 * - 编译期类型安全（枚举），运行时从 DB 读取映射
 *
 * @see RolePermission 角色→权限映射实体
 * @see com.restaurantpos.core.domain.CheckPermissionUseCase
 */
enum class PermissionKey(
    /** 持久化到 DB / JSON 的字符串标识，格式：`<domain>.<action>` */
    val key: String,
    /** 所属功能域，用于 UI 分组展示 */
    val domain: PermissionDomain,
    /** 权限中文描述（用于 Web Admin 矩阵行标签） */
    val labelZh: String,
) {
    // ── 订单 (order) ──────────────────────────────────────────
    ORDER_CREATE("order.create", PermissionDomain.ORDER, "创建订单/开台"),
    ORDER_MODIFY("order.modify", PermissionDomain.ORDER, "修改订单内容"),
    ORDER_VOID_ITEM("order.void_item", PermissionDomain.ORDER, "退项/作废单品"),
    ORDER_VOID("order.void", PermissionDomain.ORDER, "作废整单"),
    ORDER_TRANSFER("order.transfer", PermissionDomain.ORDER, "转台"),
    ORDER_MERGE("order.merge", PermissionDomain.ORDER, "并桌"),
    ORDER_SPLIT("order.split", PermissionDomain.ORDER, "拆单"),
    ORDER_NOTE("order.note", PermissionDomain.ORDER, "添加订单备注"),

    // ── 收银 (payment) ───────────────────────────────────────
    PAYMENT_PROCESS("payment.process", PermissionDomain.PAYMENT, "处理支付/结账"),
    PAYMENT_DISCOUNT("payment.discount", PermissionDomain.PAYMENT, "应用折扣"),
    PAYMENT_REFUND("payment.refund", PermissionDomain.PAYMENT, "退款"),
    PAYMENT_SPLIT("payment.split", PermissionDomain.PAYMENT, "拆账/混合支付"),
    PAYMENT_TIP("payment.tip", PermissionDomain.PAYMENT, "管理小费"),
    PAYMENT_COUPON("payment.coupon", PermissionDomain.PAYMENT, "应用优惠券"),

    // ── 菜单 (menu) ──────────────────────────────────────────
    MENU_VIEW("menu.view", PermissionDomain.MENU, "查看菜单"),
    MENU_EDIT("menu.edit", PermissionDomain.MENU, "编辑菜品/分类/规格"),
    MENU_SOLD_OUT("menu.sold_out", PermissionDomain.MENU, "沽清操作"),
    MENU_COMBO("menu.combo", PermissionDomain.MENU, "套餐管理"),

    // ── 报表 (report) ────────────────────────────────────────
    REPORT_DAILY("report.daily", PermissionDomain.REPORT, "日结报表"),
    REPORT_SHIFT("report.shift", PermissionDomain.REPORT, "交班报表"),
    REPORT_EXPORT("report.export", PermissionDomain.REPORT, "导出报表(PDF)"),

    // ── 设置 (settings) ──────────────────────────────────────
    SETTINGS_REGION("settings.region", PermissionDomain.SETTINGS, "区域配置(货币/税/语言)"),
    SETTINGS_PRINTER("settings.printer", PermissionDomain.SETTINGS, "打印机配置"),
    SETTINGS_RECEIPT("settings.receipt", PermissionDomain.SETTINGS, "小票模板"),
    SETTINGS_TAX("settings.tax", PermissionDomain.SETTINGS, "税档配置"),

    // ── 员工 (staff) ──────────────────────────────────────────
    STAFF_MANAGE("staff.manage", PermissionDomain.STAFF, "管理员工(增删改查)"),
    STAFF_ROLES("staff.roles", PermissionDomain.STAFF, "管理角色权限(分配权限位)"),
    ;

    companion object {
        private val KEY_MAP = entries.associateBy { it.key }
        fun fromKey(key: String): PermissionKey? = KEY_MAP[key]
    }
}

/**
 * 权限功能域——用于 UI 分组（折叠面板）和 DB 查询过滤。
 */
enum class PermissionDomain(
    /** 中文显示名（折叠面板标题） */
    val labelZh: String,
) {
    ORDER("订单"),
    PAYMENT("收银"),
    MENU("菜单"),
    REPORT("报表"),
    SETTINGS("设置"),
    STAFF("员工"),
}
