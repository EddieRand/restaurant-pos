import axios from 'axios'
import { apiClient } from './client'

/**
 * Controlled AI menu-price write plane.
 * Source of truth: server DTOs in `server/.../model/AiPriceAgentModels.kt`
 * and `docs/AI_PRICE_AGENT_API.md` (contract commit c17cb18).
 *
 * The server resolves items, computes ALL money fields, and re-validates
 * permissions/versions at execution time. The client never calculates or
 * resends an executable price — execute references the proposal by id only.
 */

export interface AiPriceProposalRequest {
  instruction: string
  locale: string
}

export interface AiPriceChange {
  itemId: string
  itemName: string
  oldPriceMinorUnit: number
  newPriceMinorUnit: number
  deltaMinorUnit: number
  /** Percentage change in basis points (1316 = 13.16%); null when old price is zero. */
  deltaPercentBasisPoints: number | null
}

export interface AiPriceProposalWarning {
  code: string
  message: string
}

export interface AiPriceProposalResponse {
  proposalId: string
  status: string
  tool: string
  createdAt: number
  expiresAt: number
  requiresConfirmation: boolean
  currencyCode: string
  minorUnitDigits: number
  changes: AiPriceChange[]
  warnings: AiPriceProposalWarning[]
}

export interface ExecuteAiPriceProposalRequest {
  confirmed: boolean
  idempotencyKey: string
}

export interface ExecuteAiPriceProposalResponse {
  proposalId: string
  status: string
  executedAt: number
  auditId: string
  idempotentReplay: boolean
}

export interface AiAgentError {
  code: string
  message: string
  retryable: boolean
}

/** Extract the server's stable error code from an axios failure, if present. */
export function aiAgentErrorCode(error: unknown): string | undefined {
  if (!axios.isAxiosError(error)) return undefined
  const data = error.response?.data
  if (typeof data === 'object' && data !== null && 'code' in data) {
    return String((data as { code: unknown }).code)
  }
  return undefined
}

export const aiPriceApi = {
  /** Create a price proposal from a natural-language instruction. Never mutates data. */
  createProposal: (request: AiPriceProposalRequest) =>
    apiClient
      .post<AiPriceProposalResponse>('/admin/ai/price-proposals', request, { timeout: 25_000 })
      .then(response => response.data),

  /**
   * Execute a previously-created proposal. Body carries only the confirmation flag
   * and an idempotency key — no item ids or prices. Reuse the SAME idempotencyKey
   * when retrying the same proposal so the server can replay idempotently.
   */
  executeProposal: (proposalId: string, request: ExecuteAiPriceProposalRequest) =>
    apiClient
      .post<ExecuteAiPriceProposalResponse>(
        `/admin/ai/price-proposals/${encodeURIComponent(proposalId)}/execute`,
        request,
      )
      .then(response => response.data),
}
