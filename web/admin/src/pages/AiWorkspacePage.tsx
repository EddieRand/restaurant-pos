import { useEffect, useMemo, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'
import {
  aiWorkspaceApi,
  aiWorkspaceErrorCode,
  streamAiWorkspaceRun,
  type AiPriceExecution,
  type AiPriceProposal,
  type AiWorkspaceEvent,
  type AiWorkspaceExpert,
  type AiWorkspaceRun,
  type AiWorkspaceSession,
  type AiWorkspaceSessionSummary,
  type AiWorkspaceStep,
} from '../api/aiWorkspace'
import AiWorkspaceRunPanel from '../components/AiWorkspaceResults'
import { aiWorkspaceCopy as copy, workspaceErrorMessage } from '../i18n/aiWorkspace'

type PeriodPreset = 'today' | '7d' | '30d'
type StreamState = 'live' | 'reconnecting' | 'complete'

function periodRange(preset: PeriodPreset) {
  const to = new Date(); to.setHours(23, 59, 59, 999)
  const from = new Date(); from.setHours(0, 0, 0, 0)
  if (preset === '7d') from.setDate(from.getDate() - 6)
  if (preset === '30d') from.setDate(from.getDate() - 29)
  return { fromMs: from.getTime(), toMs: to.getTime() }
}

function isRunTerminal(status: string) {
  return ['COMPLETED', 'SUCCEEDED', 'FAILED', 'CANCELLED'].includes(status.toUpperCase())
}

function upsertStep(steps: AiWorkspaceStep[], step: AiWorkspaceStep) {
  const index = steps.findIndex(item => item.stepId === step.stepId)
  return index < 0 ? [...steps, step] : steps.map(item => item.stepId === step.stepId ? step : item)
}

function ExpertSelector({ value, onChange, compact = false }: { value: AiWorkspaceExpert; onChange: (value: AiWorkspaceExpert) => void; compact?: boolean }) {
  return (
    <div className="overflow-x-auto pb-1">
      <div className={`flex min-w-max gap-2 ${compact ? '' : 'lg:grid lg:min-w-0 lg:grid-cols-4'}`}>
        {(Object.keys(copy.experts) as AiWorkspaceExpert[]).map(expert => {
          const item = copy.experts[expert]
          const selected = value === expert
          return (
            <button key={expert} type="button" onClick={() => onChange(expert)} className={`${compact ? 'w-40' : 'w-52 lg:w-auto'} rounded-xl border p-3 text-left transition-colors ${selected ? 'border-brand-300 bg-brand-50 ring-1 ring-brand-200' : 'border-gray-200 bg-white hover:border-gray-300'}`}>
              <div className="flex items-center gap-2"><span className={`flex h-7 w-7 items-center justify-center rounded-lg text-sm font-semibold ${selected ? 'bg-brand-500 text-white' : 'bg-gray-100 text-gray-600'}`}>{item.icon}</span><span className="text-sm font-semibold text-gray-900">{item.name}</span></div>
              {!compact && <p className="mt-2 text-xs leading-5 text-gray-500">{item.description}</p>}
            </button>
          )
        })}
      </div>
    </div>
  )
}

function SessionList({ sessions, activeId, loading, onSelect, onNew }: { sessions: AiWorkspaceSessionSummary[]; activeId?: string; loading: boolean; onSelect: (id: string) => void; onNew: () => void }) {
  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex items-center justify-between border-b border-gray-100 px-4 py-4"><div><p className="text-sm font-semibold text-gray-900">{copy.sessions}</p><p className="mt-0.5 text-[11px] text-gray-400">{copy.privateSessions}</p></div><button type="button" onClick={onNew} className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-50 text-xl text-brand-600 hover:bg-brand-100" title={copy.newSession}>+</button></div>
      <div className="flex-1 space-y-1 overflow-y-auto p-2">
        {loading && <p className="p-3 text-xs text-gray-400">{copy.loading}</p>}
        {!loading && sessions.length === 0 && <p className="p-3 text-xs text-gray-400">{copy.emptySessions}</p>}
        {sessions.map(session => <button key={session.sessionId} type="button" onClick={() => onSelect(session.sessionId)} className={`w-full rounded-xl px-3 py-3 text-left transition-colors ${activeId === session.sessionId ? 'bg-brand-50' : 'hover:bg-gray-50'}`}><p className={`truncate text-sm font-medium ${activeId === session.sessionId ? 'text-brand-700' : 'text-gray-800'}`}>{session.title || copy.untitledSession}</p><div className="mt-1 flex items-center justify-between gap-2"><span className="truncate text-[11px] text-gray-400">{copy.experts[session.expert].name}</span><time className="shrink-0 text-[10px] text-gray-300">{new Date(session.updatedAt).toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })}</time></div></button>)}
      </div>
    </div>
  )
}

export default function AiWorkspacePage() {
  const location = useLocation()
  const [sessions, setSessions] = useState<AiWorkspaceSessionSummary[]>([])
  const [activeSession, setActiveSession] = useState<AiWorkspaceSession | null>(null)
  const [expert, setExpert] = useState<AiWorkspaceExpert>('AUTO')
  const [message, setMessage] = useState('')
  const [period, setPeriod] = useState<PeriodPreset>('today')
  const entryState = location.state as { fromMs?: number; toMs?: number; fromRoute?: string } | null
  const hasEntryRange = Number.isFinite(entryState?.fromMs) && Number.isFinite(entryState?.toMs) && entryState!.fromMs! < entryState!.toMs!
  const [entryRangeActive, setEntryRangeActive] = useState(hasEntryRange)
  const [loadingSessions, setLoadingSessions] = useState(true)
  const [loadingSession, setLoadingSession] = useState(false)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [mobileSessionsOpen, setMobileSessionsOpen] = useState(false)
  const [liveRun, setLiveRun] = useState<AiWorkspaceRun | null>(null)
  const [streamState, setStreamState] = useState<StreamState>('complete')
  const [executionByStep, setExecutionByStep] = useState<Record<string, AiPriceExecution>>({})
  const [executingStepId, setExecutingStepId] = useState<string | null>(null)
  const [executeErrorByStep, setExecuteErrorByStep] = useState<Record<string, { code: string; message: string; retryable: boolean }>>({})

  const abortRef = useRef<AbortController | null>(null)
  const activeSessionIdRef = useRef<string | null>(null)
  const cursorByRunRef = useRef<Record<string, number>>({})
  const messagesEndRef = useRef<HTMLDivElement | null>(null)
  const range = useMemo(
    () => entryRangeActive && hasEntryRange
      ? { fromMs: entryState!.fromMs!, toMs: entryState!.toMs! }
      : periodRange(period),
    [entryRangeActive, entryState, hasEntryRange, period],
  )
  const contextRoute = entryState?.fromRoute ?? location.pathname

  useEffect(() => { activeSessionIdRef.current = activeSession?.sessionId ?? null }, [activeSession?.sessionId])
  useEffect(() => () => abortRef.current?.abort(), [])
  useEffect(() => { messagesEndRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' }) }, [activeSession?.messages.length, liveRun?.steps.length])

  async function refreshSessions() {
    const list = await aiWorkspaceApi.listSessions()
    setSessions(list)
    return list
  }

  async function refreshSession(sessionId: string, showLoading = false) {
    if (showLoading) setLoadingSession(true)
    try {
      const detail = await aiWorkspaceApi.getSession(sessionId)
      if (activeSessionIdRef.current === sessionId || activeSessionIdRef.current === null) {
        setActiveSession(detail)
        setExpert(detail.expert)
      }
      return detail
    } finally {
      if (showLoading) setLoadingSession(false)
    }
  }

  async function watchRun(run: AiWorkspaceRun, sessionId: string) {
    abortRef.current?.abort()
    const controller = new AbortController()
    abortRef.current = controller
    setLiveRun(run)
    setStreamState('live')
    let attempts = 0
    while (!controller.signal.aborted) {
      try {
        const result = await streamAiWorkspaceRun(
          run.runId,
          cursorByRunRef.current[run.runId] ?? 0,
          controller.signal,
          (event: AiWorkspaceEvent) => {
            cursorByRunRef.current[run.runId] = Math.max(cursorByRunRef.current[run.runId] ?? 0, event.sequence)
            setStreamState(event.type === 'run.completed' ? 'complete' : 'live')
            setLiveRun(current => {
              const base = current?.runId === run.runId ? current : run
              return {
                ...base,
                status: event.runStatus ?? base.status,
                completedAt: event.type === 'run.completed' ? event.occurredAt : base.completedAt,
                steps: event.step ? upsertStep(base.steps, event.step) : base.steps,
                error: event.error ?? base.error,
              }
            })
          },
        )
        cursorByRunRef.current[run.runId] = result.lastSequence
        if (result.terminal) {
          setStreamState('complete')
          await Promise.all([refreshSession(sessionId), refreshSessions()])
          break
        }
        setStreamState('reconnecting')
      } catch (streamError) {
        if (controller.signal.aborted) break
        const code = aiWorkspaceErrorCode(streamError)
        if (code === 'AI_RUN_NOT_FOUND' || code === 'AI_UNAUTHORIZED') {
          setError(workspaceErrorMessage(code, streamError instanceof Error ? streamError.message : undefined))
          setStreamState('complete')
          break
        }
        setStreamState('reconnecting')
      }
      attempts += 1
      await new Promise(resolve => window.setTimeout(resolve, Math.min(1_000 * Math.pow(2, attempts), 5_000)))
    }
  }

  async function selectSession(sessionId: string) {
    abortRef.current?.abort()
    setError(null)
    setMobileSessionsOpen(false)
    setActiveSession(null)
    activeSessionIdRef.current = sessionId
    try {
      const detail = await refreshSession(sessionId, true)
      const running = [...detail.runs].reverse().find(run => !isRunTerminal(run.status))
      if (running) void watchRun(running, detail.sessionId)
      else { setLiveRun(null); setStreamState('complete') }
    } catch (loadError) {
      setError(workspaceErrorMessage(aiWorkspaceErrorCode(loadError), loadError instanceof Error ? loadError.message : undefined))
    }
  }

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoadingSessions(true)
      try {
        const list = await refreshSessions()
        if (!cancelled && list.length > 0) await selectSession(list[0].sessionId)
      } catch (loadError) {
        if (!cancelled) setError(workspaceErrorMessage(aiWorkspaceErrorCode(loadError), loadError instanceof Error ? loadError.message : undefined))
      } finally {
        if (!cancelled) setLoadingSessions(false)
      }
    }
    void load()
    return () => { cancelled = true; abortRef.current?.abort() }
    // Initial restore only. Session changes are explicit user actions.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function createSession() {
    abortRef.current?.abort()
    setError(null)
    try {
      const created = await aiWorkspaceApi.createSession(expert)
      activeSessionIdRef.current = created.sessionId
      setActiveSession(created)
      setLiveRun(null)
      setStreamState('complete')
      setMobileSessionsOpen(false)
      await refreshSessions()
      return created
    } catch (createError) {
      setError(workspaceErrorMessage(aiWorkspaceErrorCode(createError), createError instanceof Error ? createError.message : undefined))
      return null
    }
  }

  async function sendMessage() {
    const content = message.trim()
    if (!content || sending) return
    setSending(true)
    setError(null)
    try {
      let session = activeSession
      if (!session) session = await createSession()
      if (!session) return
      const accepted = await aiWorkspaceApi.sendMessage(session.sessionId, expert, content, {
        ...range,
        currentRoute: contextRoute,
      })
      const optimisticMessage = { messageId: accepted.messageId, role: 'user', content, createdAt: Date.now() }
      const run: AiWorkspaceRun = { runId: accepted.runId, messageId: accepted.messageId, status: accepted.status, createdAt: Date.now(), completedAt: null, steps: [] }
      setActiveSession(current => current && current.sessionId === session!.sessionId ? {
        ...current,
        updatedAt: Date.now(),
        messages: [...current.messages.filter(item => item.messageId !== accepted.messageId), optimisticMessage],
        runs: [...current.runs.filter(item => item.runId !== accepted.runId), run],
      } : current)
      setMessage('')
      void watchRun(run, session.sessionId)
    } catch (sendError) {
      setError(workspaceErrorMessage(aiWorkspaceErrorCode(sendError), sendError instanceof Error ? sendError.message : copy.sendFailed))
    } finally {
      setSending(false)
    }
  }

  // Execute errors are bound to the originating step (not the global banner) so the
  // operator sees a precise status next to the proposal they tried to apply.
  async function executePrice(step: AiWorkspaceStep, proposal: AiPriceProposal) {
    setExecutingStepId(step.stepId)
    // Clear any prior execute error for this step. Retrying reuses the SAME proposalId
    // as the idempotency key, so a transient retry replays idempotently server-side.
    setExecuteErrorByStep(current => {
      if (!current[step.stepId]) return current
      const next = { ...current }; delete next[step.stepId]; return next
    })
    try {
      const execution = await aiWorkspaceApi.executePriceProposal(proposal.proposalId, proposal.proposalId)
      setExecutionByStep(current => ({ ...current, [step.stepId]: execution }))
      if (activeSession) await refreshSession(activeSession.sessionId)
    } catch (executeError) {
      const code = aiWorkspaceErrorCode(executeError) ?? 'AI_INVALID_REQUEST'
      // Only genuinely transient provider failures may be retried with the same proposal.
      const retryable = code === 'AI_RATE_LIMITED' || code === 'AI_PROVIDER_UNAVAILABLE' || code === 'AI_TIMEOUT'
      setExecuteErrorByStep(current => ({
        ...current,
        [step.stepId]: { code, message: workspaceErrorMessage(code, executeError instanceof Error ? executeError.message : undefined), retryable },
      }))
      // Already executed: the price change is done — best-effort restore the persisted
      // result + audit id. Keep the step error visible if that refresh itself fails.
      if (code === 'AI_PROPOSAL_ALREADY_EXECUTED' && activeSession) {
        await refreshSession(activeSession.sessionId).catch(() => undefined)
      }
    } finally {
      setExecutingStepId(null)
    }
  }

  const runsByMessage = useMemo(() => {
    const result = new Map<string, AiWorkspaceRun[]>()
    for (const run of activeSession?.runs ?? []) result.set(run.messageId, [...(result.get(run.messageId) ?? []), run])
    return result
  }, [activeSession?.runs])

  return (
    <div className="flex h-full min-h-0 bg-[#f6f7f9]">
      <aside className="hidden w-64 shrink-0 border-r border-gray-200 bg-white xl:block"><SessionList sessions={sessions} activeId={activeSession?.sessionId} loading={loadingSessions} onSelect={selectSession} onNew={() => { void createSession() }} /></aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="border-b border-gray-200 bg-white px-4 py-4 sm:px-6">
          <div className="mx-auto flex max-w-5xl items-start justify-between gap-3">
            <div className="min-w-0"><div className="flex items-center gap-2"><button type="button" onClick={() => setMobileSessionsOpen(true)} className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-gray-200 text-gray-500 xl:hidden" aria-label={copy.sessions}>☰</button><div><p className="text-[11px] font-semibold tracking-[0.16em] text-brand-600">RESTAURANT AI</p><h1 className="truncate text-xl font-semibold text-gray-900 sm:text-2xl">{copy.title}</h1></div></div><p className="mt-1 hidden text-sm text-gray-500 sm:block">{copy.subtitle}</p></div>
            <button type="button" onClick={() => { void createSession() }} className="btn-secondary shrink-0 px-3 py-2 text-xs sm:px-4">+ {copy.newSession}</button>
          </div>
        </header>

        <div className="flex-1 overflow-y-auto">
          <div className="mx-auto flex min-h-full max-w-5xl flex-col px-3 py-4 sm:px-6 sm:py-6">
            <section className="mb-5"><div className="mb-2 flex items-end justify-between gap-3"><div><p className="text-sm font-semibold text-gray-900">{copy.expertLabel}</p><p className="hidden text-xs text-gray-400 sm:block">{copy.expertDescription}</p></div><div className="flex rounded-lg bg-gray-100 p-1 text-[11px]">{([['today', copy.today], ['7d', copy.last7Days], ['30d', copy.last30Days]] as [PeriodPreset, string][]).map(([key, label]) => <button key={key} type="button" onClick={() => { setEntryRangeActive(false); setPeriod(key) }} className={`rounded-md px-2 py-1.5 sm:px-3 ${!entryRangeActive && period === key ? 'bg-white font-medium text-gray-800 shadow-sm' : 'text-gray-500'}`}>{label}</button>)}</div></div><ExpertSelector value={expert} onChange={setExpert} /></section>

            {error && <div role="alert" className="mb-4 flex items-start justify-between gap-3 rounded-xl border border-red-100 bg-red-50 p-4 text-sm text-red-700"><span>{error}</span><button type="button" className="shrink-0 font-semibold underline" onClick={() => setError(null)}>{copy.close}</button></div>}

            <section className="min-h-[260px] flex-1">
              {loadingSession && <div className="flex h-40 items-center justify-center text-sm text-gray-400">{copy.restoring}</div>}
              {!loadingSession && (!activeSession || activeSession.messages.length === 0) && <div className="flex min-h-[280px] items-center justify-center"><div className="max-w-lg text-center"><div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-brand-100 text-2xl text-brand-600">✦</div><h2 className="mt-4 text-xl font-semibold text-gray-900">{copy.emptyTitle}</h2><p className="mt-2 text-sm leading-6 text-gray-500">{copy.emptyBody}</p><div className="mt-5 flex flex-wrap justify-center gap-2">{copy.examples.map(example => <button key={example} type="button" onClick={() => setMessage(example)} className="rounded-full border border-gray-200 bg-white px-3 py-2 text-xs text-gray-600 hover:border-brand-300 hover:text-brand-600">{example}</button>)}</div></div></div>}
              {!loadingSession && activeSession && activeSession.messages.length > 0 && <div className="space-y-5">{activeSession.messages.map(item => {
                const user = item.role.toLowerCase() === 'user'
                const messageRuns = runsByMessage.get(item.messageId) ?? []
                return <div key={item.messageId} className={user ? 'ml-auto max-w-3xl' : 'mr-auto max-w-3xl'}><div className={`mb-1 text-[11px] text-gray-400 ${user ? 'text-right' : ''}`}>{user ? copy.user : copy.assistant} · {new Date(item.createdAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}</div><div className={`whitespace-pre-wrap rounded-2xl px-4 py-3 text-sm leading-6 ${user ? 'rounded-tr-sm bg-brand-500 text-white' : 'rounded-tl-sm border border-gray-200 bg-white text-gray-700'}`}>{item.content}</div>{messageRuns.map(run => { const renderedRun = liveRun?.runId === run.runId ? liveRun : run; return <AiWorkspaceRunPanel key={run.runId} run={renderedRun} streamState={liveRun?.runId === run.runId ? streamState : isRunTerminal(run.status) ? 'complete' : undefined} executionByStep={executionByStep} executingStepId={executingStepId} executeErrorByStep={executeErrorByStep} onExecute={executePrice} /> })}</div>
              })}<div ref={messagesEndRef} /></div>}
            </section>
          </div>
        </div>

        <footer className="border-t border-gray-200 bg-white px-3 py-3 sm:px-6 sm:py-4"><div className="mx-auto max-w-5xl"><div className="flex items-end gap-2 rounded-2xl border border-gray-200 bg-white p-2 shadow-sm focus-within:border-brand-300 focus-within:ring-2 focus-within:ring-brand-100"><textarea value={message} onChange={event => setMessage(event.target.value)} onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); void sendMessage() } }} rows={1} placeholder={copy.inputPlaceholder} className="max-h-32 min-h-[42px] flex-1 resize-none border-0 bg-transparent px-2 py-2.5 text-sm leading-5 text-gray-900 outline-none placeholder:text-gray-400" disabled={sending} /><button type="button" className="btn-primary h-[42px] shrink-0 px-3 sm:px-5" disabled={!message.trim() || sending || streamState === 'live' || streamState === 'reconnecting'} onClick={() => { void sendMessage() }}><span className="hidden sm:inline">{sending ? copy.sending : copy.send}</span><span className="sm:hidden">↑</span></button></div><div className="mt-2 flex items-center justify-between gap-3 text-[10px] text-gray-400"><span>{copy.disclaimer}</span><span className="hidden shrink-0 sm:inline">{copy.period}：{entryRangeActive ? copy.dashboardRange : period === 'today' ? copy.today : period === '7d' ? copy.last7Days : copy.last30Days}</span></div></div></footer>
      </div>

      {mobileSessionsOpen && <div className="fixed inset-0 z-50 bg-black/30 xl:hidden" onClick={() => setMobileSessionsOpen(false)}><aside className="h-full w-[min(320px,86vw)] bg-white shadow-xl" onClick={event => event.stopPropagation()}><SessionList sessions={sessions} activeId={activeSession?.sessionId} loading={loadingSessions} onSelect={selectSession} onNew={() => { void createSession() }} /></aside></div>}
    </div>
  )
}
