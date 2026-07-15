import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  aiPriceApi,
  aiAgentErrorCode,
  type AiPriceChange,
  type AiPriceProposalResponse,
  type ExecuteAiPriceProposalResponse,
} from '../api/aiPrice'
import { getAiPriceCopy, interpolateAiPrice, aiPriceErrorMessage } from '../i18n/aiPrice'

type Phase = 'idle' | 'proposing' | 'proposed' | 'executing' | 'done'

/** Codes that kill the current proposal — the only path forward is a new one. */
const DEAD_PROPOSAL_CODES = new Set([
  'AI_PROPOSAL_STALE',
  'AI_PROPOSAL_EXPIRED',
  'AI_PROPOSAL_NOT_FOUND',
  'AI_IDEMPOTENCY_CONFLICT',
  'AI_PROPOSAL_ALREADY_EXECUTED',
])

function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  return `idem-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function makeMoneyFormatter(locale: string, currencyCode: string, minorUnitDigits: number) {
  const divisor = Math.pow(10, minorUnitDigits)
  let nf: Intl.NumberFormat
  try {
    nf = new Intl.NumberFormat(locale, {
      style: 'currency',
      currency: currencyCode,
      minimumFractionDigits: minorUnitDigits,
      maximumFractionDigits: minorUnitDigits,
    })
  } catch {
    nf = new Intl.NumberFormat(locale, { minimumFractionDigits: minorUnitDigits, maximumFractionDigits: minorUnitDigits })
  }
  return (minor: number) => nf.format(minor / divisor)
}

function formatSignedMoney(fmt: (m: number) => string, minor: number): string {
  const sign = minor > 0 ? '+' : ''
  return `${sign}${fmt(minor)}`
}

function formatPercent(basisPoints: number | null): string {
  if (basisPoints == null) return '—'
  const pct = basisPoints / 100
  const sign = pct > 0 ? '+' : ''
  return `${sign}${pct.toFixed(2)}%`
}

function mmss(remainingMs: number): string {
  const s = Math.max(0, Math.floor(remainingMs / 1000))
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
}

export default function AiPriceConsolePage() {
  const { i18n } = useTranslation()
  const copy = useMemo(() => getAiPriceCopy(i18n.language), [i18n.language])

  const [phase, setPhase] = useState<Phase>('idle')
  const [instruction, setInstruction] = useState('')
  const [proposal, setProposal] = useState<AiPriceProposalResponse | null>(null)
  const [result, setResult] = useState<ExecuteAiPriceProposalResponse | null>(null)
  const [errorCode, setErrorCode] = useState<string | undefined>(undefined)
  const [hasError, setHasError] = useState(false)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [now, setNow] = useState(() => Date.now())

  // Idempotency key is tied to a proposal and reused across execute retries of that proposal.
  const idempotencyKeyRef = useRef<string>('')

  // 1s tick only while a live proposal is on screen (for the expiry countdown).
  useEffect(() => {
    if (phase !== 'proposed' || !proposal) return
    const t = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(t)
  }, [phase, proposal])

  const expired = !!proposal && now >= proposal.expiresAt
  const money = useMemo(
    () => (proposal ? makeMoneyFormatter(i18n.language, proposal.currencyCode, proposal.minorUnitDigits) : null),
    [proposal, i18n.language],
  )

  const resetError = () => {
    setHasError(false)
    setErrorCode(undefined)
  }

  const generate = useCallback(async () => {
    const text = instruction.trim()
    if (!text || phase === 'proposing') return
    resetError()
    setResult(null)
    setProposal(null)
    setPhase('proposing')
    try {
      const p = await aiPriceApi.createProposal({ instruction: text, locale: i18n.language })
      idempotencyKeyRef.current = newIdempotencyKey()
      setProposal(p)
      setNow(Date.now())
      setPhase('proposed')
    } catch (err) {
      setErrorCode(aiAgentErrorCode(err))
      setHasError(true)
      setPhase('idle')
    }
  }, [instruction, phase, i18n.language])

  const execute = useCallback(async () => {
    if (!proposal || expired) return
    setDialogOpen(false)
    resetError()
    setPhase('executing')
    try {
      const res = await aiPriceApi.executeProposal(proposal.proposalId, {
        confirmed: true,
        idempotencyKey: idempotencyKeyRef.current,
      })
      setResult(res)
      setPhase('done')
    } catch (err) {
      setErrorCode(aiAgentErrorCode(err))
      setHasError(true)
      // A dead-proposal error can't be retried against the same proposal.
      setPhase('proposed')
    }
  }, [proposal, expired])

  const startOver = () => {
    resetError()
    setProposal(null)
    setResult(null)
    setPhase('idle')
  }

  const deadProposal = !!errorCode && DEAD_PROPOSAL_CODES.has(errorCode)

  return (
    <div className="mx-auto max-w-4xl">
      <header className="mb-6">
        <div className="mb-2 flex items-center gap-2 text-xs font-semibold tracking-[0.16em] text-brand-600">
          <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-brand-100 text-base" aria-hidden="true">⚡</span>
          {copy.eyebrow}
        </div>
        <h1 className="text-2xl font-semibold text-gray-900">{copy.title}</h1>
        <p className="mt-1 max-w-2xl text-sm leading-6 text-gray-500">{copy.description}</p>
      </header>

      {/* Instruction input */}
      <section className="card p-5">
        <label htmlFor="ai-price-instruction" className="mb-2 block text-sm font-medium text-gray-700">{copy.instructionLabel}</label>
        <div className="flex flex-col gap-3 sm:flex-row">
          <input
            id="ai-price-instruction"
            type="text"
            className="min-w-0 flex-1 rounded-lg border border-gray-200 px-3 py-2 text-sm focus:border-brand-400 focus:outline-none focus:ring-1 focus:ring-brand-400"
            placeholder={copy.instructionPlaceholder}
            value={instruction}
            onChange={e => setInstruction(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter') generate() }}
            disabled={phase === 'proposing' || phase === 'executing'}
          />
          <button
            type="button"
            className="btn-primary shrink-0 disabled:cursor-not-allowed disabled:opacity-60"
            onClick={generate}
            disabled={!instruction.trim() || phase === 'proposing' || phase === 'executing'}
          >
            {phase === 'proposing' ? copy.generating : proposal ? copy.regenerate : copy.generate}
          </button>
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-gray-400">
          <span>{copy.examplesLabel}:</span>
          {copy.examples.map(ex => (
            <button
              key={ex}
              type="button"
              className="rounded-full border border-gray-200 px-2.5 py-1 text-gray-500 transition-colors hover:border-brand-300 hover:text-brand-600 disabled:opacity-50"
              onClick={() => setInstruction(ex)}
              disabled={phase === 'proposing' || phase === 'executing'}
            >
              {ex}
            </button>
          ))}
        </div>
      </section>

      {/* Error banner */}
      {hasError && phase !== 'done' && (
        <div role="alert" className="mt-4 flex items-start justify-between gap-4 rounded-lg border border-red-100 bg-red-50 p-4 text-sm text-red-700">
          <div>
            <p className="font-semibold">{copy.errorHeading}</p>
            <p className="mt-0.5">{aiPriceErrorMessage(copy, errorCode)}</p>
          </div>
          {deadProposal
            ? <button type="button" onClick={startOver} className="shrink-0 font-semibold underline underline-offset-2">{copy.regenerate}</button>
            : proposal
              ? null
              : <button type="button" onClick={generate} className="shrink-0 font-semibold underline underline-offset-2">{copy.retry}</button>}
        </div>
      )}

      {/* Proposal diff */}
      {proposal && money && phase !== 'done' && (
        <section className="card mt-4 overflow-hidden">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-gray-100 p-5">
            <div>
              <h2 className="text-base font-semibold text-gray-900">{copy.proposalHeading}</h2>
              <p className="mt-0.5 text-xs text-gray-400">
                {interpolateAiPrice(copy.generatedAt, { time: new Date(proposal.createdAt).toLocaleString(i18n.language) })}
              </p>
            </div>
            <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${expired ? 'bg-red-50 text-red-600' : 'bg-amber-50 text-amber-700'}`}>
              {expired ? copy.expired : interpolateAiPrice(copy.expiresIn, { mmss: mmss(proposal.expiresAt - now) })}
            </span>
          </div>

          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="bg-gray-50 text-left text-xs text-gray-500">
                <tr>
                  <th className="px-5 py-2 font-medium">{copy.thItem}</th>
                  <th className="px-5 py-2 text-right font-medium">{copy.thOld}</th>
                  <th className="px-5 py-2 text-right font-medium">{copy.thNew}</th>
                  <th className="px-5 py-2 text-right font-medium">{copy.thDelta}</th>
                  <th className="px-5 py-2 text-right font-medium">{copy.thPercent}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {proposal.changes.map((c: AiPriceChange) => {
                  const up = c.deltaMinorUnit > 0
                  return (
                    <tr key={c.itemId}>
                      <td className="px-5 py-3 font-medium text-gray-900">{c.itemName}</td>
                      <td className="px-5 py-3 text-right tabular-nums text-gray-400 line-through">{money(c.oldPriceMinorUnit)}</td>
                      <td className="px-5 py-3 text-right font-semibold tabular-nums text-gray-900">{money(c.newPriceMinorUnit)}</td>
                      <td className={`px-5 py-3 text-right tabular-nums font-medium ${up ? 'text-red-600' : 'text-emerald-600'}`}>{formatSignedMoney(money, c.deltaMinorUnit)}</td>
                      <td className={`px-5 py-3 text-right tabular-nums ${up ? 'text-red-600' : 'text-emerald-600'}`}>{formatPercent(c.deltaPercentBasisPoints)}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          {proposal.warnings.length > 0 && (
            <div className="border-t border-amber-100 bg-amber-50/60 px-5 py-3">
              <p className="mb-1 text-xs font-semibold text-amber-700">{copy.warningsHeading}</p>
              <ul className="list-disc space-y-0.5 pl-4 text-xs text-amber-700">
                {proposal.warnings.map((w, i) => <li key={`${w.code}-${i}`}>{w.message}</li>)}
              </ul>
            </div>
          )}

          <div className="flex flex-col gap-3 border-t border-gray-100 p-5 sm:flex-row sm:items-center sm:justify-between">
            <p className="max-w-xl text-xs leading-5 text-gray-400">{copy.serverComputedNote}</p>
            <div className="flex shrink-0 gap-2">
              <button type="button" className="rounded-lg border border-gray-200 px-4 py-2 text-sm font-medium text-gray-600 hover:bg-gray-50" onClick={startOver}>{copy.discard}</button>
              <button
                type="button"
                className="btn-primary disabled:cursor-not-allowed disabled:opacity-60"
                onClick={() => setDialogOpen(true)}
                disabled={expired || deadProposal || phase === 'executing'}
              >
                {phase === 'executing' ? copy.executing : copy.confirmCta}
              </button>
            </div>
          </div>
        </section>
      )}

      {/* Success */}
      {phase === 'done' && result && (
        <section className="card mt-4 border border-emerald-100 bg-emerald-50/40 p-6">
          <div className="flex items-start gap-3">
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-emerald-100 text-emerald-700" aria-hidden="true">✓</span>
            <div className="min-w-0">
              <h2 className="text-base font-semibold text-gray-900">{copy.successHeading}</h2>
              <p className="mt-1 text-sm text-gray-600">{copy.successBody}</p>
              {result.idempotentReplay && <p className="mt-1 text-xs text-amber-700">{copy.replayNote}</p>}
              <p className="mt-3 text-xs text-gray-500">
                {copy.auditLabel}: <code className="rounded bg-white px-1.5 py-0.5 font-mono text-[11px] text-gray-700">{result.auditId}</code>
              </p>
            </div>
          </div>
          <div className="mt-5">
            <button type="button" className="btn-primary" onClick={startOver}>{copy.done}</button>
          </div>
        </section>
      )}

      <p className="mt-6 text-xs leading-5 text-gray-400">{copy.disclaimer}</p>

      {/* Non-skippable confirmation dialog */}
      {dialogOpen && proposal && money && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="ai-price-confirm-title"
          onClick={() => setDialogOpen(false)}
          onKeyDown={e => { if (e.key === 'Escape') setDialogOpen(false) }}
        >
          <div className="w-full max-w-md rounded-xl bg-white p-6 shadow-xl" onClick={e => e.stopPropagation()}>
            <h3 id="ai-price-confirm-title" className="text-lg font-semibold text-gray-900">{copy.dialogTitle}</h3>
            <p className="mt-2 text-sm text-gray-600">{interpolateAiPrice(copy.dialogIntro, { count: String(proposal.changes.length) })}</p>
            <ul className="mt-3 space-y-1 rounded-lg bg-gray-50 p-3 text-sm text-gray-700">
              {proposal.changes.map(c => (
                <li key={c.itemId} className="tabular-nums">
                  {interpolateAiPrice(copy.dialogItemLine, {
                    item: c.itemName,
                    old: money(c.oldPriceMinorUnit),
                    new: money(c.newPriceMinorUnit),
                    delta: formatSignedMoney(money, c.deltaMinorUnit),
                  })}
                </li>
              ))}
            </ul>
            <div className="mt-6 flex justify-end gap-2">
              <button type="button" className="rounded-lg border border-gray-200 px-4 py-2 text-sm font-medium text-gray-600 hover:bg-gray-50" onClick={() => setDialogOpen(false)}>{copy.dialogCancel}</button>
              <button type="button" className="btn-primary" onClick={execute}>{copy.dialogConfirm}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
