/**
 * Copy for the AI 改价 (menu price) console. Self-contained zh/en, mirroring the
 * read-plane `i18n/ai.ts` pattern so it stays out of the typed 9-locale files.
 * Error keys match the server's stable codes in docs/AI_PRICE_AGENT_API.md.
 */

const en = {
  eyebrow: 'AI WRITE-PLANE · MENU PRICING',
  title: 'AI Price Assistant',
  description:
    'Describe a price change in plain language. The server resolves the item and computes every amount — you review a diff and explicitly confirm before anything changes.',
  instructionLabel: 'Instruction',
  instructionPlaceholder: 'e.g. Raise Kung Pao Chicken by 5 yuan',
  examplesLabel: 'Try',
  examples: ['Raise Kung Pao Chicken by 5 yuan', 'Set Signature Beef Noodles to 45', 'Increase Bubble Tea by 10%'],
  generate: 'Generate proposal',
  generating: 'Analyzing…',
  regenerate: 'New proposal',
  proposalHeading: 'Proposed change',
  generatedAt: 'Proposed {{time}}',
  expiresIn: 'Confirm within {{mmss}}',
  expired: 'Expired — regenerate',
  thItem: 'Item',
  thOld: 'Current',
  thNew: 'New',
  thDelta: 'Change',
  thPercent: '%',
  warningsHeading: 'Warnings',
  serverComputedNote: 'All amounts are computed and stored by the server. Confirming executes the stored values, not anything sent from this screen.',
  confirmCta: 'Confirm & apply',
  discard: 'Discard',
  dialogTitle: 'Apply this price change?',
  dialogIntro: 'You are about to apply {{count}} menu price change(s). This updates live menu prices.',
  dialogItemLine: '{{item}}: {{old}} → {{new}} ({{delta}})',
  dialogConfirm: 'Yes, apply now',
  dialogCancel: 'Cancel',
  executing: 'Applying…',
  successHeading: 'Price change applied',
  successBody: 'The menu price was updated and recorded.',
  replayNote: 'This request was already applied — showing the original result.',
  auditLabel: 'Audit ID',
  done: 'Done',
  disclaimer: 'AI structures your instruction only. Item resolution, pricing, permission and version checks, and the audit record are all enforced server-side.',
  errorHeading: 'Could not complete',
  retry: 'Retry',
  errors: {
    AI_INVALID_REQUEST: 'The instruction could not be understood. Rephrase it with a clear item and price change.',
    AI_UNAUTHORIZED: 'Your session has expired. Please sign in again.',
    AI_PERMISSION_DENIED: 'You lack the menu-edit permission required to change prices. Contact an administrator.',
    AI_PROPOSAL_NOT_FOUND: 'This proposal no longer exists. Generate a new one.',
    AI_PROPOSAL_STALE: 'This item’s price changed after the proposal was created, so it was discarded. Generate a new proposal for the latest price.',
    AI_PROPOSAL_ALREADY_EXECUTED: 'This proposal was already applied. No duplicate change was made.',
    AI_IDEMPOTENCY_CONFLICT: 'A submission conflict occurred. Generate a fresh proposal and try again.',
    AI_PROPOSAL_EXPIRED: 'The confirmation window elapsed and the proposal expired. Generate a new one.',
    AI_TARGET_AMBIGUOUS: 'The instruction matched no item, or more than one. Name the specific menu item.',
    AI_AGENT_DISABLED: 'AI price changes are currently disabled. Contact an administrator.',
    AI_NOT_CONFIGURED: 'The AI service is not configured. Contact an administrator.',
    AI_AUTH_FAILED: 'The AI service rejected its credentials. Contact an administrator.',
    AI_QUOTA_EXCEEDED: 'AI service credit is insufficient. Ask an administrator to top up.',
    AI_RATE_LIMITED: 'The AI service is busy. Wait a moment and retry.',
    AI_PROVIDER_UNAVAILABLE: 'The AI service is temporarily unavailable. Menu data is unaffected.',
    AI_INVALID_RESPONSE: 'The AI returned an invalid result. Please retry.',
    AI_TIMEOUT: 'The AI request timed out. Please retry.',
    generic: 'Something went wrong. Please retry.',
  } as Record<string, string>,
}

const zh: typeof en = {
  eyebrow: 'AI 写平面 · 菜单改价',
  title: 'AI 改价助手',
  description:
    '用自然语言描述改价需求。系统在服务端解析菜品并计算所有金额——你先看到改前改后的对比，明确确认后才会真正改动。',
  instructionLabel: '改价指令',
  instructionPlaceholder: '例如：把宫保鸡丁价格提高 5 元',
  examplesLabel: '试试',
  examples: ['把宫保鸡丁价格提高 5 元', '招牌牛肉面调到 45 元', '珍珠奶茶涨价 10%'],
  generate: '生成改价提案',
  generating: '正在分析…',
  regenerate: '重新生成',
  proposalHeading: '改价提案',
  generatedAt: '生成于 {{time}}',
  expiresIn: '请在 {{mmss}} 内确认',
  expired: '已过期，请重新生成',
  thItem: '菜品',
  thOld: '原价',
  thNew: '新价',
  thDelta: '变化',
  thPercent: '幅度',
  warningsHeading: '提示',
  serverComputedNote: '所有金额均由服务端计算并保存。确认后执行的是服务端保存的数值，而非本页发送的任何价格。',
  confirmCta: '确认并执行',
  discard: '放弃',
  dialogTitle: '确认执行本次改价？',
  dialogIntro: '即将执行 {{count}} 项菜单改价，将更新线上菜单价格。',
  dialogItemLine: '{{item}}：{{old}} → {{new}}（{{delta}}）',
  dialogConfirm: '确认，立即执行',
  dialogCancel: '取消',
  executing: '正在执行…',
  successHeading: '改价已执行',
  successBody: '菜单价格已更新并记录。',
  replayNote: '该请求此前已执行——显示的是原始结果。',
  auditLabel: '审计 ID',
  done: '完成',
  disclaimer: 'AI 仅负责理解你的指令。菜品匹配、价格计算、权限与版本校验、审计记录全部由服务端强制执行。',
  errorHeading: '未能完成',
  retry: '重试',
  errors: {
    AI_INVALID_REQUEST: '无法识别该指令，请换一种说法，明确菜品与价格调整。',
    AI_UNAUTHORIZED: '登录已过期，请重新登录后再试。',
    AI_PERMISSION_DENIED: '你没有「菜单编辑」权限，无法改价。请联系管理员。',
    AI_PROPOSAL_NOT_FOUND: '该提案已不存在，请重新生成。',
    AI_PROPOSAL_STALE: '该菜品价格在提案生成后已被改动，提案作废。请重新生成以获取最新价格。',
    AI_PROPOSAL_ALREADY_EXECUTED: '该提案已执行过，未重复改动。',
    AI_IDEMPOTENCY_CONFLICT: '提交冲突，请重新生成提案后再试。',
    AI_PROPOSAL_EXPIRED: '确认时限已过，提案过期。请重新生成。',
    AI_TARGET_AMBIGUOUS: '指令未唯一匹配到菜品（无匹配或匹配多个）。请指明具体菜品名。',
    AI_AGENT_DISABLED: 'AI 改价功能当前未开启。请联系管理员。',
    AI_NOT_CONFIGURED: 'AI 服务尚未配置，请联系管理员。',
    AI_AUTH_FAILED: 'AI 服务鉴权失败，请联系管理员。',
    AI_QUOTA_EXCEEDED: 'AI 服务余额不足，请联系管理员充值。',
    AI_RATE_LIMITED: 'AI 服务繁忙，请稍后重试。',
    AI_PROVIDER_UNAVAILABLE: 'AI 服务暂时不可用，请稍后重试。菜单数据未受影响。',
    AI_INVALID_RESPONSE: 'AI 返回内容无效，请重试。',
    AI_TIMEOUT: 'AI 请求超时，请重试。',
    generic: '操作失败，请重试。',
  } as Record<string, string>,
}

export type AiPriceCopy = typeof en

export function getAiPriceCopy(locale: string): AiPriceCopy {
  return locale.toLowerCase() === 'zh-cn' ? zh : en
}

export function interpolateAiPrice(template: string, values: Record<string, string>) {
  return Object.entries(values).reduce((out, [k, v]) => out.replace(`{{${k}}}`, v), template)
}

/** Localized message for a server error code, falling back to generic. */
export function aiPriceErrorMessage(copy: AiPriceCopy, code: string | undefined) {
  return (code && copy.errors[code]) || copy.errors.generic
}
