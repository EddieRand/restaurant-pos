const en = {
  eyebrow: 'AI OPERATIONS ASSISTANT',
  title: 'AI Operations Brief',
  description: 'Turn the selected period’s verified business metrics into concise observations and next steps.',
  generate: 'Generate brief',
  regenerate: 'Regenerate',
  generating: 'Generating…',
  retry: 'Retry',
  snapshot: 'Data snapshot',
  orders: 'Orders',
  revenue: 'Net revenue',
  avgOrder: 'Avg. order',
  guests: 'Guests',
  observations: 'Observations',
  actions: 'Recommended actions',
  evidence: 'Evidence',
  priorityLabels: { high: 'High', medium: 'Medium', low: 'Low' },
  evidenceLabels: {
    orderCount: 'Orders',
    grossRevenue: 'Gross revenue',
    netRevenue: 'Net revenue',
    averageOrderValue: 'Avg. order',
    guestCount: 'Guests',
    discount: 'Discounts',
    refund: 'Refunds',
    paymentMethods: 'Payment mix',
    topItems: 'Top items',
    peakHours: 'Peak hours',
    periodComparison: 'Period comparison',
  } as Record<string, string>,
  generatedAt: 'Generated {{time}} · {{model}}',
  disclaimer: 'AI recommendations are for operational reference only. They cannot change orders, menus, inventory, or settings.',
  errors: {
    unauthorized: 'AI authorization failed. Your session may have expired or the service key may be invalid.',
    quota: 'AI service credit is insufficient. Ask an administrator to top up the account.',
    rateLimited: 'The AI service is busy. Wait a moment and retry.',
    unavailable: 'AI is not configured or is temporarily unavailable. Core reports are unaffected.',
    timeout: 'The AI request timed out. Check the network and retry.',
    generic: 'Could not generate the AI brief. Please retry.',
  },
}

const zh: typeof en = {
  eyebrow: 'AI OPERATIONS ASSISTANT',
  title: 'AI 经营助手',
  description: '基于所选周期的已核实经营指标，生成重点观察和下一步行动建议。',
  generate: '生成经营简报',
  regenerate: '重新生成',
  generating: '正在生成…',
  retry: '重试',
  snapshot: '数据快照',
  orders: '订单数',
  revenue: '净营业额',
  avgOrder: '均单价',
  guests: '接待客数',
  observations: '经营观察',
  actions: '行动建议',
  evidence: '依据',
  priorityLabels: { high: '高', medium: '中', low: '低' },
  evidenceLabels: {
    orderCount: '订单数',
    grossRevenue: '毛营业额',
    netRevenue: '净营业额',
    averageOrderValue: '均单价',
    guestCount: '接待客数',
    discount: '折扣',
    refund: '退款',
    paymentMethods: '支付方式',
    topItems: '热销菜品',
    peakHours: '高峰时段',
    periodComparison: '环比',
  } as Record<string, string>,
  generatedAt: '生成于 {{time}} · {{model}}',
  disclaimer: 'AI 建议仅供经营参考，不能修改订单、菜单、库存或系统设置。',
  errors: {
    unauthorized: 'AI 授权失败，可能是登录已过期或服务密钥无效。',
    quota: 'AI 服务余额不足，请联系管理员充值后重试。',
    rateLimited: 'AI 服务繁忙，请稍后重试。',
    unavailable: 'AI 尚未配置或暂时不可用，核心订单和报表不受影响。',
    timeout: 'AI 请求超时，请检查网络后重试。',
    generic: '暂时无法生成 AI 简报，请重试。',
  },
}

export function getAiCopy(locale: string) {
  return locale.toLowerCase() === 'zh-cn' ? zh : en
}

export function interpolate(template: string, values: Record<string, string>) {
  return Object.entries(values).reduce((result, [key, value]) => result.replace(`{{${key}}}`, value), template)
}

/** Human-readable fallback for an evidence key outside the known map (camelCase → "Camel case"). */
export function prettifyEvidenceKey(key: string) {
  const spaced = key.replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/[_-]+/g, ' ').trim()
  return spaced ? spaced.charAt(0).toUpperCase() + spaced.slice(1) : key
}
