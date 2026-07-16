import { useEffect, useMemo, useState } from 'react'
import type {
  AiGrowthBriefing,
  AiGrowthDataMode,
  AiGrowthEditableParams,
  AiGrowthEvidence,
  AiGrowthExecution,
  AiGrowthProposal,
} from '../api/aiWorkspace'
import { fmtMoney } from '../api/reports'
import { aiWorkspaceCopy as copy } from '../i18n/aiWorkspace'

const modeStyles: Record<AiGrowthDataMode, string> = {
  REAL: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  AI_GENERATED: 'border-violet-200 bg-violet-50 text-violet-700',
  DEMO_SIGNAL: 'border-amber-200 bg-amber-50 text-amber-800',
}

export function GrowthDataModeBadge({ mode }: { mode: AiGrowthDataMode }) {
  return <span className={`inline-flex rounded-full border px-2 py-0.5 text-[10px] font-semibold ${modeStyles[mode]}`}>{mode} · {copy.growth.modes[mode]}</span>
}

function DemoSignalNotice() {
  return <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs font-semibold text-amber-800">⚠ {copy.growth.demoNotice}</div>
}

function formatEvidenceValue(evidence: AiGrowthEvidence) {
  if (evidence.textValue != null) return evidence.textValue
  if (evidence.numericValue == null) return '—'
  if (evidence.unit === 'MINOR_UNIT') return fmtMoney(evidence.numericValue)
  if (evidence.unit === 'BASIS_POINTS') return `${(evidence.numericValue / 100).toFixed(2)}%`
  if (evidence.unit === 'COUNT') return new Intl.NumberFormat('zh-CN').format(evidence.numericValue)
  return `${new Intl.NumberFormat('zh-CN').format(evidence.numericValue)} ${evidence.unit}`
}

function GrowthEvidenceGrid({ evidence }: { evidence: AiGrowthEvidence[] }) {
  if (evidence.length === 0) return null
  return (
    <div>
      <p className="mb-2 text-xs font-semibold text-gray-500">{copy.growth.evidence}</p>
      <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
        {evidence.map((item, index) => (
          <div key={`${item.key}-${index}`} className="rounded-xl border border-gray-100 bg-gray-50/70 p-3">
            <div className="flex flex-wrap items-start justify-between gap-2"><p className="text-xs text-gray-500">{item.label}</p><GrowthDataModeBadge mode={item.dataMode} /></div>
            <p className="mt-2 text-base font-semibold leading-6 text-gray-900">{formatEvidenceValue(item)}</p>
            <p className="mt-1 truncate text-[10px] text-gray-400" title={item.source}>{item.source}</p>
          </div>
        ))}
      </div>
    </div>
  )
}

export function AiGrowthBriefingCard({ briefing, loading = false, error, onRefresh, compact = false }: {
  briefing?: AiGrowthBriefing | null
  loading?: boolean
  error?: string | null
  onRefresh?: () => void
  compact?: boolean
}) {
  const hasDemoSignal = briefing?.evidence.some(item => item.dataMode === 'DEMO_SIGNAL') ?? false
  if (loading) return <section className="card mb-5 border border-emerald-100 p-5 text-sm text-gray-500">{copy.growth.loading}</section>
  if (error && !briefing) return <section className="card mb-5 border border-red-100 bg-red-50/40 p-5"><p className="text-sm text-red-700">{error}</p>{onRefresh && <button type="button" className="btn-secondary mt-3" onClick={onRefresh}>{copy.retry}</button>}</section>
  if (!briefing) return null
  return (
    <section className={`${compact ? '' : 'card mb-5 overflow-hidden border border-emerald-100 bg-gradient-to-br from-white to-emerald-50/50'}`}>
      <div className={compact ? '' : 'p-5 sm:p-6'}>
        {!compact && <div className="mb-4"><p className="text-xs font-semibold tracking-[0.14em] text-emerald-700">{copy.growth.eyebrow}</p><h2 className="mt-1 text-lg font-semibold text-gray-900">{copy.growth.todayTitle}</h2><p className="mt-1 text-xs leading-5 text-gray-500">{copy.growth.todaySubtitle}</p></div>}
        {error && <div role="alert" className="mb-4 rounded-lg border border-red-100 bg-red-50 p-3 text-sm text-red-700">{error}</div>}
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div><div className="mb-2 flex flex-wrap items-center gap-2"><GrowthDataModeBadge mode="AI_GENERATED" /><span className="text-[11px] text-gray-400">{briefing.businessDate}</span></div><h3 className="text-lg font-semibold text-gray-900">{briefing.headline}</h3><p className="mt-2 text-sm leading-6 text-gray-600">{briefing.summary}</p></div>
          {onRefresh && <button type="button" className="btn-secondary shrink-0 px-3 py-2 text-xs" onClick={onRefresh}>{copy.growth.refresh}</button>}
        </div>
        <div className="mt-5"><GrowthEvidenceGrid evidence={briefing.evidence} /></div>
        {briefing.suggestions.length > 0 && <div className="mt-5"><div className="mb-2 flex items-center gap-2"><p className="text-xs font-semibold text-gray-500">{copy.growth.suggestions}</p><GrowthDataModeBadge mode="AI_GENERATED" /></div><ol className="space-y-2">{briefing.suggestions.map((suggestion, index) => <li key={`${index}-${suggestion}`} className="flex gap-3 rounded-lg border border-gray-100 bg-white/80 p-3 text-sm leading-6 text-gray-700"><span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-emerald-50 text-xs font-semibold text-emerald-700">{index + 1}</span>{suggestion}</li>)}</ol></div>}
        {briefing.contentDraft && <div className="mt-5 rounded-xl border border-violet-100 bg-violet-50/60 p-4"><div className="mb-2 flex items-center gap-2"><p className="text-xs font-semibold text-violet-800">{copy.growth.contentDraft}</p><GrowthDataModeBadge mode="AI_GENERATED" /></div><p className="whitespace-pre-wrap text-sm leading-6 text-gray-700">{briefing.contentDraft}</p></div>}
        {hasDemoSignal && <div className="mt-4"><DemoSignalNotice /></div>}
      </div>
    </section>
  )
}

export function AiGrowthExecutionCard({ execution }: { execution: AiGrowthExecution }) {
  return <div className="rounded-xl border border-emerald-100 bg-emerald-50 p-4"><p className="font-semibold text-emerald-800">✓ {copy.growth.executed}</p><dl className="mt-3 grid gap-2 text-xs text-emerald-800 sm:grid-cols-3"><div><dt className="text-emerald-600">{copy.growth.audit}</dt><dd className="mt-1 break-all font-mono">{execution.auditId}</dd></div><div><dt className="text-emerald-600">{copy.growth.coupon}</dt><dd className="mt-1 break-all font-mono">{execution.couponId}</dd></div><div><dt className="text-emerald-600">{copy.growth.campaign}</dt><dd className="mt-1 break-all font-mono">{execution.campaignId}</dd></div></dl>{execution.idempotentReplay && <p className="mt-3 text-xs text-amber-700">{copy.growth.replay}</p>}</div>
}

export function AiGrowthProposalCard({ proposal, execution, busy, error, onRevise, onExecute }: {
  proposal: AiGrowthProposal
  execution?: AiGrowthExecution
  busy: boolean
  error?: { code: string; message: string; retryable: boolean; operation: 'revise' | 'execute' }
  onRevise: (proposal: AiGrowthProposal, params: AiGrowthEditableParams) => Promise<void>
  onExecute: (proposal: AiGrowthProposal) => Promise<void>
}) {
  const [fixedAmount, setFixedAmount] = useState(String(proposal.editableParams.fixedAmountMinorUnit))
  const [validDays, setValidDays] = useState(String(proposal.editableParams.validDays))
  const [targetSegment, setTargetSegment] = useState(proposal.editableParams.targetSegment)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [now, setNow] = useState(Date.now())
  useEffect(() => {
    setFixedAmount(String(proposal.editableParams.fixedAmountMinorUnit))
    setValidDays(String(proposal.editableParams.validDays))
    setTargetSegment(proposal.editableParams.targetSegment)
  }, [proposal])
  useEffect(() => {
    if (execution) return
    const timer = window.setInterval(() => setNow(Date.now()), 1_000)
    return () => window.clearInterval(timer)
  }, [execution])

  const params = useMemo<AiGrowthEditableParams>(() => ({
    fixedAmountMinorUnit: Number(fixedAmount), validDays: Number(validDays), targetSegment: targetSegment.trim(),
  }), [fixedAmount, targetSegment, validDays])
  const valid = Number.isInteger(params.fixedAmountMinorUnit) && params.fixedAmountMinorUnit > 0 && Number.isInteger(params.validDays) && params.validDays > 0 && params.targetSegment.length > 0
  const changed = params.fixedAmountMinorUnit !== proposal.editableParams.fixedAmountMinorUnit || params.validDays !== proposal.editableParams.validDays || params.targetSegment !== proposal.editableParams.targetSegment
  const expired = now >= proposal.expiresAt
  const blocked = !!error && !error.retryable && (error.operation === 'execute' || error.code !== 'GROWTH_INVALID_PARAMS')
  const hasDemoSignal = proposal.dataMode === 'DEMO_SIGNAL' || proposal.expectedImpact.dataMode === 'DEMO_SIGNAL' || proposal.evidence.some(item => item.dataMode === 'DEMO_SIGNAL')

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-2"><div><div className="flex flex-wrap items-center gap-2"><h4 className="text-base font-semibold text-gray-900">{copy.growth.proposal}</h4><GrowthDataModeBadge mode={proposal.dataMode} /></div><p className="mt-1 text-xs text-gray-400">{copy.growth.proposalType} · {copy.growth.version} {proposal.version}</p></div><span className={`rounded-full px-2 py-1 text-[11px] ${expired && !execution ? 'bg-red-50 text-red-600' : 'bg-gray-100 text-gray-500'}`}>{copy.growth.expires} {new Date(proposal.expiresAt).toLocaleString('zh-CN')}</span></div>
      <GrowthEvidenceGrid evidence={proposal.evidence} />
      <div className="rounded-xl border border-blue-100 bg-blue-50/50 p-4"><div className="flex flex-wrap items-center gap-2"><p className="text-xs font-semibold text-blue-800">{copy.growth.expectedImpact}</p><GrowthDataModeBadge mode={proposal.expectedImpact.dataMode} /></div><p className="mt-2 text-sm font-semibold text-gray-900">{proposal.expectedImpact.title}</p><p className="mt-1 text-sm leading-6 text-gray-600">{proposal.expectedImpact.detail}</p></div>
      {hasDemoSignal && <DemoSignalNotice />}

      {!execution && <div className="rounded-xl border border-gray-200 p-4"><p className="text-sm font-semibold text-gray-900">{copy.growth.editTitle}</p><div className="mt-3 grid gap-3 sm:grid-cols-3"><label className="text-xs text-gray-500">{copy.growth.fixedAmount}<input className="input mt-1" type="number" min="1" step="1" value={fixedAmount} onChange={event => setFixedAmount(event.target.value)} disabled={busy || expired || blocked} /><span className="mt-1 block text-[10px] text-gray-400">{copy.growth.fixedAmountPreview}：{fmtMoney(Number(fixedAmount) || 0)}</span></label><label className="text-xs text-gray-500">{copy.growth.validDays}<input className="input mt-1" type="number" min="1" step="1" value={validDays} onChange={event => setValidDays(event.target.value)} disabled={busy || expired || blocked} /></label><label className="text-xs text-gray-500">{copy.growth.targetSegment}<input className="input mt-1" value={targetSegment} onChange={event => setTargetSegment(event.target.value)} disabled={busy || expired || blocked} /></label></div><div className="mt-3 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"><p className="text-xs leading-5 text-gray-400">{copy.growth.revisionNotice}</p><button type="button" className="btn-secondary shrink-0" disabled={!changed || !valid || busy || expired || blocked} onClick={() => { void onRevise(proposal, params) }}>{busy ? copy.growth.revising : copy.growth.revise}</button></div></div>}

      {error && <div role="alert" className="rounded-lg border border-red-100 bg-red-50 p-3 text-sm text-red-700">{error.message}</div>}
      {execution ? <AiGrowthExecutionCard execution={execution} />
        : <div className="flex justify-end"><button type="button" className="btn-primary" disabled={busy || expired || changed || !valid || blocked} onClick={() => setDialogOpen(true)}>{busy ? copy.growth.executing : expired ? copy.growth.expired : error?.operation === 'execute' && error.retryable ? copy.retry : copy.growth.confirmAction}</button></div>}

      {dialogOpen && <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" role="dialog" aria-modal="true" aria-labelledby={`growth-confirm-${proposal.proposalId}`} onClick={() => setDialogOpen(false)}><div className="w-full max-w-lg rounded-2xl bg-white p-5 shadow-xl sm:p-6" onClick={event => event.stopPropagation()}><h3 id={`growth-confirm-${proposal.proposalId}`} className="text-lg font-semibold text-gray-900">{copy.growth.confirmTitle}</h3><p className="mt-2 text-sm leading-6 text-gray-600">{copy.growth.confirmBody}</p><dl className="mt-4 grid gap-2 rounded-xl bg-gray-50 p-4 text-sm sm:grid-cols-3"><div><dt className="text-xs text-gray-400">{copy.growth.fixedAmount}</dt><dd className="mt-1 font-semibold">{fmtMoney(proposal.editableParams.fixedAmountMinorUnit)}</dd></div><div><dt className="text-xs text-gray-400">{copy.growth.validDays}</dt><dd className="mt-1 font-semibold">{proposal.editableParams.validDays}</dd></div><div><dt className="text-xs text-gray-400">{copy.growth.targetSegment}</dt><dd className="mt-1 font-semibold">{proposal.editableParams.targetSegment}</dd></div></dl><div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end"><button type="button" className="btn-secondary" onClick={() => setDialogOpen(false)}>{copy.growth.cancel}</button><button type="button" className="btn-primary" onClick={() => { setDialogOpen(false); void onExecute(proposal) }}>{copy.growth.confirm}</button></div></div></div>}
    </div>
  )
}
