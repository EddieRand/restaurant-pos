import axios from 'axios'
import { apiClient } from './client'
import type { OperatingInsightResponse } from './ai'

export type AiWorkspaceExpert = 'AUTO' | 'OPERATIONS' | 'PRODUCT_HELP' | 'MENU'
export type AiWorkspaceStepKind = 'ANALYSIS' | 'HOW_TO' | 'ACTION'
export type AiWorkspaceStepStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'AWAITING_CONFIRMATION' | 'FAILED' | 'SKIPPED' | 'EXECUTED'

export interface AiWorkspaceContext {
  fromMs?: number
  toMs?: number
  currentRoute: string
}

export interface AiWorkspaceMessage {
  messageId: string
  role: string
  content: string
  createdAt: number
}

export interface AiWorkspaceEvidence {
  key: string
  label: string
  numericValue: number
  unit: 'MINOR_UNIT' | 'COUNT' | 'BASIS_POINTS'
  dimensionValue?: string | null
}

export interface AiWorkspaceQueryResult {
  answer: string
  period: { fromMs: number; toMs: number }
  evidence: AiWorkspaceEvidence[]
  sourceTool: string
}

export interface AiWorkspaceHowToSource {
  documentId: string
  title: string
  section: string
  route?: string | null
  lastVerifiedAt: number
}

export interface AiWorkspaceHowToResult {
  answer: string
  steps: string[]
  sources: AiWorkspaceHowToSource[]
}

export interface AiPriceChange {
  itemId: string
  itemName: string
  oldPriceMinorUnit: number
  newPriceMinorUnit: number
  deltaMinorUnit: number
  deltaPercentBasisPoints: number | null
}

export interface AiPriceProposal {
  proposalId: string
  status: string
  tool: string
  createdAt: number
  expiresAt: number
  requiresConfirmation: boolean
  currencyCode: string
  minorUnitDigits: number
  changes: AiPriceChange[]
  warnings: Array<{ code: string; message: string }>
}

export interface AiPriceExecution {
  proposalId: string
  status: string
  executedAt: number
  auditId: string
  idempotentReplay: boolean
}

export interface AiWorkspaceStepResult {
  insight?: OperatingInsightResponse | null
  query?: AiWorkspaceQueryResult | null
  howTo?: AiWorkspaceHowToResult | null
  priceProposal?: AiPriceProposal | null
  execution?: AiPriceExecution | null
}

export interface AiWorkspaceError {
  code: string
  message: string
  retryable: boolean
}

export interface AiWorkspaceStep {
  stepId: string
  tool: string
  kind: AiWorkspaceStepKind
  status: AiWorkspaceStepStatus
  dependsOn: string[]
  displayTitle: string
  result?: AiWorkspaceStepResult | null
  proposalId?: string | null
  error?: AiWorkspaceError | null
}

export interface AiWorkspaceRun {
  runId: string
  messageId: string
  status: string
  createdAt: number
  completedAt?: number | null
  steps: AiWorkspaceStep[]
  error?: AiWorkspaceError | null
}

export interface AiWorkspaceSessionSummary {
  sessionId: string
  title: string
  expert: AiWorkspaceExpert
  locale: string
  createdAt: number
  updatedAt: number
}

export interface AiWorkspaceSession extends AiWorkspaceSessionSummary {
  messages: AiWorkspaceMessage[]
  runs: AiWorkspaceRun[]
}

export interface AiWorkspaceAccepted {
  sessionId: string
  messageId: string
  runId: string
  status: string
}

export interface AiWorkspaceEvent {
  sequence: number
  type: 'message.accepted' | 'plan.created' | 'step.started' | 'step.completed' | 'step.awaiting_confirmation' | 'step.failed' | 'step.executed' | 'run.completed'
  occurredAt: number
  runId: string
  step?: AiWorkspaceStep | null
  runStatus?: string | null
  error?: AiWorkspaceError | null
}

export class AiWorkspaceApiError extends Error {
  constructor(public code: string, message: string, public retryable = false, public status?: number) {
    super(message)
    this.name = 'AiWorkspaceApiError'
  }
}

export function aiWorkspaceErrorCode(error: unknown): string | undefined {
  if (error instanceof AiWorkspaceApiError) return error.code
  if (!axios.isAxiosError(error)) return undefined
  const data = error.response?.data
  return typeof data === 'object' && data !== null && 'code' in data ? String(data.code) : undefined
}

export const aiWorkspaceApi = {
  listSessions: () =>
    apiClient.get<{ sessions: AiWorkspaceSessionSummary[] }>('/admin/ai/workspace/sessions').then(response => response.data.sessions),

  createSession: (expert: AiWorkspaceExpert) =>
    apiClient.post<AiWorkspaceSession>('/admin/ai/workspace/sessions', { expert, locale: 'zh-CN' }).then(response => response.data),

  getSession: (sessionId: string) =>
    apiClient.get<AiWorkspaceSession>(`/admin/ai/workspace/sessions/${encodeURIComponent(sessionId)}`).then(response => response.data),

  sendMessage: (sessionId: string, expert: AiWorkspaceExpert, message: string, context: AiWorkspaceContext) =>
    apiClient.post<AiWorkspaceAccepted>(`/admin/ai/workspace/sessions/${encodeURIComponent(sessionId)}/messages`, {
      sessionId,
      expert,
      message,
      locale: 'zh-CN',
      context,
    }, { timeout: 25_000 }).then(response => response.data),

  executePriceProposal: (proposalId: string, idempotencyKey: string) =>
    apiClient.post<AiPriceExecution>(`/admin/ai/price-proposals/${encodeURIComponent(proposalId)}/execute`, {
      confirmed: true,
      idempotencyKey,
    }).then(response => response.data),
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'

function workspaceUrl(path: string) {
  return `${String(API_BASE_URL).replace(/\/$/, '')}${path}`
}

async function responseError(response: Response): Promise<AiWorkspaceApiError> {
  const body = await response.json().catch(() => null) as { code?: string; message?: string; retryable?: boolean } | null
  return new AiWorkspaceApiError(
    body?.code ?? `HTTP_${response.status}`,
    body?.message ?? `HTTP ${response.status}`,
    body?.retryable ?? response.status >= 500,
    response.status,
  )
}

/** Consumes one authenticated SSE connection. The caller reconnects with the returned cursor. */
export async function streamAiWorkspaceRun(
  runId: string,
  afterSequence: number,
  signal: AbortSignal,
  onEvent: (event: AiWorkspaceEvent) => void,
): Promise<{ lastSequence: number; terminal: boolean }> {
  const token = localStorage.getItem('pos_admin_token')
  const response = await fetch(
    workspaceUrl(`/admin/ai/workspace/runs/${encodeURIComponent(runId)}/events?afterSequence=${afterSequence}`),
    {
      method: 'GET',
      headers: {
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      signal,
    },
  )
  if (!response.ok) throw await responseError(response)
  if (!response.body) throw new AiWorkspaceApiError('AI_STREAM_UNAVAILABLE', '事件流不可用', true)

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let cursor = afterSequence
  let terminal = false

  const consumeBlock = (block: string) => {
    let eventId: number | undefined
    const dataLines: string[] = []
    for (const line of block.split('\n')) {
      if (line.startsWith('id:')) eventId = Number(line.slice(3).trim())
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
    }
    if (dataLines.length === 0) return
    const event = JSON.parse(dataLines.join('\n')) as AiWorkspaceEvent
    cursor = Math.max(cursor, Number.isFinite(eventId) ? eventId! : event.sequence)
    terminal = terminal || event.type === 'run.completed'
    onEvent(event)
  }

  while (!signal.aborted) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value, { stream: !done })
    buffer = buffer.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      const block = buffer.slice(0, boundary)
      buffer = buffer.slice(boundary + 2)
      if (block.trim()) consumeBlock(block)
      boundary = buffer.indexOf('\n\n')
    }
    if (done) break
  }
  if (buffer.trim()) consumeBlock(buffer)
  return { lastSequence: cursor, terminal }
}
