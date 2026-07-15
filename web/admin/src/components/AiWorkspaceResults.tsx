import { useEffect, useState } from 'react'
import type {
  AiPriceExecution,
  AiPriceProposal,
  AiWorkspaceEvidence,
  AiWorkspaceRun,
  AiWorkspaceStep,
} from '../api/aiWorkspace'
import { aiWorkspaceCopy as copy, workspaceErrorMessage } from '../i18n/aiWorkspace'

function makeMoneyFormatter(currencyCode = 'CNY', minorUnitDigits = 2) {
  const divisor = Math.pow(10, minorUnitDigits)
  let formatter: Intl.NumberFormat
  try {
    formatter = new Intl.NumberFormat('zh-CN', {
      style: 'currency', currency: currencyCode,
      minimumFractionDigits: minorUnitDigits, maximumFractionDigits: minorUnitDigits,
    })
  } catch {
    formatter = new Intl.NumberFormat('zh-CN', { minimumFractionDigits: minorUnitDigits, maximumFractionDigits: minorUnitDigits })
  }
  return (minor: number) => formatter.format(minor / divisor)
}

function formatPercent(basisPoints: number | null) {
  if (basisPoints == null) return '—'
  const value = basisPoints / 100
  return `${value > 0 ? '+' : ''}${value.toFixed(2)}%`
}

function statusClass(status: AiWorkspaceStep['status']) {
  if (status === 'FAILED') return 'bg-red-50 text-red-700'
  if (status === 'AWAITING_CONFIRMATION') return 'bg-amber-50 text-amber-700'
  if (status === 'SUCCEEDED' || status === 'EXECUTED') return 'bg-emerald-50 text-emerald-700'
  if (status === 'RUNNING') return 'bg-blue-50 text-blue-700'
  return 'bg-gray-100 text-gray-600'
}

function EvidenceValue({ evidence }: { evidence: AiWorkspaceEvidence }) {
  const value = evidence.unit === 'MINOR_UNIT'
    ? makeMoneyFormatter()(evidence.numericValue)
    : evidence.unit === 'BASIS_POINTS'
      ? formatPercent(evidence.numericValue)
      : new Intl.NumberFormat('zh-CN').format(evidence.numericValue)
  return (
    <div className="rounded-xl border border-gray-100 bg-gray-50/70 p-3">
      <div className="flex items-start justify-between gap-3">
        <p className="text-xs text-gray-500">{evidence.label}</p>
        {evidence.dimensionValue && <span className="rounded bg-white px-1.5 py-0.5 text-[10px] text-gray-400">{evidence.dimensionValue}</span>}
      </div>
      <p className="mt-1 text-base font-semibold tabular-nums text-gray-900">{value}</p>
    </div>
  )
}

function InsightResult({ step }: { step: AiWorkspaceStep }) {
  const insight = step.result?.insight
  if (!insight) return null
  const snapshot = [
    [copy.insight.orders, String(insight.snapshot.orderCount)],
    [copy.insight.revenue, makeMoneyFormatter()(insight.snapshot.netRevenueMinorUnit)],
    [copy.insight.averageOrder, makeMoneyFormatter()(insight.snapshot.averageOrderValueMinorUnit)],
    [copy.insight.guests, String(insight.snapshot.guestCount)],
  ]
  return (
    <div className="space-y-4">
      <div><h4 className="text-base font-semibold text-gray-900">{insight.headline}</h4><p className="mt-1 text-sm leading-6 text-gray-600">{insight.summary}</p></div>
      <div>
        <p className="mb-2 text-xs font-semibold text-gray-400">{copy.insight.snapshot}</p>
        <div className="grid grid-cols-2 gap-2 lg:grid-cols-4">
          {snapshot.map(([label, value]) => <div key={label} className="rounded-lg bg-gray-50 p-3"><p className="text-[11px] text-gray-400">{label}</p><p className="mt-1 font-semibold tabular-nums text-gray-900">{value}</p></div>)}
        </div>
      </div>
      {insight.observations.length > 0 && <div><p className="mb-2 text-xs font-semibold text-gray-400">{copy.insight.observations}</p><div className="grid gap-2 md:grid-cols-2">{insight.observations.map((item, index) => <div key={`${item.title}-${index}`} className={`rounded-lg border p-3 ${item.severity === 'warning' ? 'border-amber-100 bg-amber-50/60' : item.severity === 'positive' ? 'border-emerald-100 bg-emerald-50/60' : 'border-gray-100 bg-gray-50'}`}><p className="text-sm font-semibold text-gray-800">{item.title}</p><p className="mt-1 text-xs leading-5 text-gray-600">{item.detail}</p></div>)}</div></div>}
      {insight.actions.length > 0 && <div><p className="mb-2 text-xs font-semibold text-gray-400">{copy.insight.actions}</p><ol className="space-y-2">{insight.actions.map((action, index) => <li key={`${action.title}-${index}`} className="flex gap-3 rounded-lg border border-gray-100 p-3"><span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-brand-50 text-xs font-semibold text-brand-600">{index + 1}</span><div><p className="text-sm font-semibold text-gray-800">{action.title}</p><p className="mt-0.5 text-xs leading-5 text-gray-500">{action.reason}</p></div></li>)}</ol></div>}
    </div>
  )
}

function QueryResult({ step }: { step: AiWorkspaceStep }) {
  const result = step.result?.query
  if (!result) return null
  return (
    <div>
      <p className="text-sm leading-6 text-gray-700">{result.answer}</p>
      {result.evidence.length > 0 && <div className="mt-4"><p className="mb-2 text-xs font-semibold text-gray-400">{copy.query.evidence}</p><div className="grid grid-cols-2 gap-2 lg:grid-cols-3">{result.evidence.map((item, index) => <EvidenceValue key={`${item.key}-${item.dimensionValue ?? index}`} evidence={item} />)}</div></div>}
      <p className="mt-3 text-[11px] text-gray-400">{copy.query.source}：{result.sourceTool} · {new Date(result.period.fromMs).toLocaleDateString('zh-CN')} – {new Date(result.period.toMs).toLocaleDateString('zh-CN')}</p>
    </div>
  )
}

function HowToResult({ step }: { step: AiWorkspaceStep }) {
  const result = step.result?.howTo
  if (!result) return null
  return (
    <div>
      <p className="text-sm leading-6 text-gray-700">{result.answer}</p>
      {result.steps.length > 0 && <div className="mt-4"><p className="mb-2 text-xs font-semibold text-gray-400">{copy.howTo.steps}</p><ol className="space-y-2">{result.steps.map((item, index) => <li key={`${index}-${item}`} className="flex gap-3 text-sm text-gray-700"><span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-blue-50 text-xs font-semibold text-blue-600">{index + 1}</span><span className="pt-0.5 leading-5">{item}</span></li>)}</ol></div>}
      {result.sources.length > 0 && <div className="mt-4 border-t border-gray-100 pt-3"><p className="mb-2 text-xs font-semibold text-gray-400">{copy.howTo.sources}</p><div className="flex flex-wrap gap-2">{result.sources.map(source => <span key={`${source.documentId}-${source.section}`} className="rounded-lg border border-blue-100 bg-blue-50/50 px-3 py-2 text-xs text-blue-700"><strong>{source.title}</strong> · {source.section}{source.route ? ` · ${source.route}` : ''}<span className="ml-2 text-blue-400">{copy.howTo.verified} {new Date(source.lastVerifiedAt).toLocaleDateString('zh-CN')}</span></span>)}</div></div>}
    </div>
  )
}

function PriceProposalResult({ step, execution, executing, onExecute }: {
  step: AiWorkspaceStep
  execution?: AiPriceExecution
  executing: boolean
  onExecute: (step: AiWorkspaceStep, proposal: AiPriceProposal) => Promise<void>
}) {
  const proposal = step.result?.priceProposal
  const persistedExecution = step.result?.execution ?? execution
  const [dialogOpen, setDialogOpen] = useState(false)
  const [now, setNow] = useState(Date.now())
  useEffect(() => {
    if (!proposal || persistedExecution) return
    const timer = window.setInterval(() => setNow(Date.now()), 1_000)
    return () => window.clearInterval(timer)
  }, [proposal, persistedExecution])
  if (!proposal) return null
  const money = makeMoneyFormatter(proposal.currencyCode, proposal.minorUnitDigits)
  const expired = now >= proposal.expiresAt
  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2"><h4 className="text-sm font-semibold text-gray-900">{copy.price.proposal}</h4><span className={`rounded-full px-2 py-1 text-[11px] ${expired && !persistedExecution ? 'bg-red-50 text-red-600' : 'bg-gray-100 text-gray-500'}`}>{copy.price.expires} {new Date(proposal.expiresAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}</span></div>
      <div className="overflow-x-auto rounded-lg border border-gray-100">
        <table className="min-w-[620px] w-full text-sm"><thead className="bg-gray-50 text-xs text-gray-500"><tr><th className="px-4 py-2 text-left font-medium">{copy.price.item}</th><th className="px-4 py-2 text-right font-medium">{copy.price.oldPrice}</th><th className="px-4 py-2 text-right font-medium">{copy.price.newPrice}</th><th className="px-4 py-2 text-right font-medium">{copy.price.delta}</th><th className="px-4 py-2 text-right font-medium">{copy.price.percent}</th></tr></thead>
          <tbody className="divide-y divide-gray-50">{proposal.changes.map(change => <tr key={change.itemId}><td className="px-4 py-3 font-medium text-gray-900">{change.itemName}</td><td className="px-4 py-3 text-right tabular-nums text-gray-400 line-through">{money(change.oldPriceMinorUnit)}</td><td className="px-4 py-3 text-right font-semibold tabular-nums text-gray-900">{money(change.newPriceMinorUnit)}</td><td className={`px-4 py-3 text-right font-medium tabular-nums ${change.deltaMinorUnit > 0 ? 'text-red-600' : 'text-emerald-600'}`}>{change.deltaMinorUnit > 0 ? '+' : ''}{money(change.deltaMinorUnit)}</td><td className="px-4 py-3 text-right tabular-nums text-gray-600">{formatPercent(change.deltaPercentBasisPoints)}</td></tr>)}</tbody>
        </table>
      </div>
      {proposal.warnings.length > 0 && <div className="mt-3 rounded-lg bg-amber-50 p-3 text-xs text-amber-700"><p className="font-semibold">{copy.price.warnings}</p><ul className="mt-1 list-disc space-y-1 pl-4">{proposal.warnings.map((warning, index) => <li key={`${warning.code}-${index}`}>{warning.message}</li>)}</ul></div>}
      <p className="mt-3 text-xs leading-5 text-gray-400">{copy.price.serverComputed}</p>
      {persistedExecution ? <div className="mt-4 rounded-lg border border-emerald-100 bg-emerald-50 p-4"><p className="font-semibold text-emerald-800">✓ {copy.price.executed}</p><p className="mt-1 text-xs text-emerald-700">{copy.price.audit}：<code className="rounded bg-white px-1.5 py-0.5">{persistedExecution.auditId}</code></p>{persistedExecution.idempotentReplay && <p className="mt-1 text-xs text-amber-700">{copy.price.replay}</p>}</div>
        : <div className="mt-4 flex justify-end"><button type="button" className="btn-primary" disabled={expired || executing} onClick={() => setDialogOpen(true)}>{expired ? copy.price.expired : executing ? copy.price.executing : copy.price.review}</button></div>}
      {dialogOpen && <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" role="dialog" aria-modal="true" aria-labelledby={`confirm-${step.stepId}`} onClick={() => setDialogOpen(false)}><div className="w-full max-w-lg rounded-2xl bg-white p-5 shadow-xl sm:p-6" onClick={event => event.stopPropagation()}><h3 id={`confirm-${step.stepId}`} className="text-lg font-semibold text-gray-900">{copy.price.dialogTitle}</h3><p className="mt-2 text-sm leading-6 text-gray-600">{copy.price.dialogBody}</p><ul className="mt-4 max-h-52 space-y-2 overflow-y-auto rounded-xl bg-gray-50 p-3">{proposal.changes.map(change => <li key={change.itemId} className="flex justify-between gap-4 text-sm"><span className="font-medium text-gray-800">{change.itemName}</span><span className="shrink-0 tabular-nums text-gray-600">{money(change.oldPriceMinorUnit)} → <strong>{money(change.newPriceMinorUnit)}</strong></span></li>)}</ul><div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end"><button type="button" className="btn-secondary" onClick={() => setDialogOpen(false)}>{copy.price.cancel}</button><button type="button" className="btn-primary" onClick={async () => { setDialogOpen(false); await onExecute(step, proposal) }}>{copy.price.confirm}</button></div></div></div>}
    </div>
  )
}

function ExecutionOnlyResult({ step }: { step: AiWorkspaceStep }) {
  const execution = step.result?.execution
  if (!execution || step.result?.priceProposal) return null
  return (
    <div className="rounded-lg border border-emerald-100 bg-emerald-50 p-4">
      <p className="font-semibold text-emerald-800">✓ {copy.price.executed}</p>
      <p className="mt-1 text-xs text-emerald-700">{copy.price.audit}：<code className="rounded bg-white px-1.5 py-0.5">{execution.auditId}</code></p>
      {execution.idempotentReplay && <p className="mt-1 text-xs text-amber-700">{copy.price.replay}</p>}
    </div>
  )
}

export default function AiWorkspaceRunPanel({ run, liveSteps, executionByStep, executingStepId, streamState, onExecute }: {
  run: AiWorkspaceRun
  liveSteps?: AiWorkspaceStep[]
  executionByStep: Record<string, AiPriceExecution>
  executingStepId?: string | null
  streamState?: 'live' | 'reconnecting' | 'complete'
  onExecute: (step: AiWorkspaceStep, proposal: AiPriceProposal) => Promise<void>
}) {
  const steps = liveSteps ?? run.steps
  return (
    <section className="mt-3 overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm">
      <div className="flex items-center justify-between gap-3 border-b border-gray-100 bg-gray-50/60 px-4 py-3"><p className="text-xs font-semibold text-gray-600">{copy.runResults}</p>{streamState && <span className={`flex items-center gap-1.5 text-[11px] ${streamState === 'reconnecting' ? 'text-amber-600' : streamState === 'live' ? 'text-blue-600' : 'text-emerald-600'}`}><span className={`h-1.5 w-1.5 rounded-full ${streamState === 'reconnecting' ? 'bg-amber-500' : streamState === 'live' ? 'animate-pulse bg-blue-500' : 'bg-emerald-500'}`} />{streamState === 'reconnecting' ? copy.reconnecting : streamState === 'live' ? copy.live : copy.complete}</span>}</div>
      {run.error && <div role="alert" className="border-b border-red-100 bg-red-50 p-4 text-sm text-red-700"><p className="font-semibold">{run.error.code}</p><p className="mt-1">{workspaceErrorMessage(run.error.code, run.error.message)}</p></div>}
      {steps.length === 0 ? (!run.error && <div className="p-5 text-sm text-gray-400">{copy.noSteps}</div>) : <div className="divide-y divide-gray-100">{steps.map((step, index) => <article key={step.stepId} className="p-4 sm:p-5"><div className="mb-4 flex items-start gap-3"><span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-gray-100 text-xs font-semibold text-gray-600">{index + 1}</span><div className="min-w-0 flex-1"><p className="font-semibold text-gray-900">{step.displayTitle}</p><p className="mt-0.5 break-all text-[11px] text-gray-400">{step.tool}</p></div><span className={`shrink-0 rounded-full px-2 py-1 text-[11px] font-medium ${statusClass(step.status)}`}>{copy.stepStatuses[step.status]}</span></div>{step.error && <div role="alert" className="mb-3 rounded-lg border border-red-100 bg-red-50 p-3 text-sm text-red-700"><strong>{step.error.code}</strong> · {step.error.message}</div>}<InsightResult step={step} /><QueryResult step={step} /><HowToResult step={step} /><PriceProposalResult step={step} execution={executionByStep[step.stepId]} executing={executingStepId === step.stepId} onExecute={onExecute} /><ExecutionOnlyResult step={step} /></article>)}</div>}
    </section>
  )
}
