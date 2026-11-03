import type { Category, MessageStatus } from '../api/messages'

export const STATUS_LABELS: Record<MessageStatus, string> = {
  PENDING_AI: 'Processing',
  DRAFT_READY: 'Draft ready',
  AI_FAILED: 'AI failed',
  APPROVED: 'Approved',
}

export const CATEGORY_LABELS: Record<Category, string> = {
  QUESTION: 'Question',
  COMPLAINT: 'Complaint',
  PRAISE: 'Praise',
  OTHER: 'Other',
}

export function statusLabel(status: MessageStatus): string {
  return STATUS_LABELS[status]
}

export function categoryLabel(category: Category): string {
  return CATEGORY_LABELS[category]
}

const AI_ERROR_MESSAGES: Record<string, string> = {
  TIMEOUT: 'The AI provider took too long to respond.',
  RATE_LIMITED: 'The AI provider is rate limiting requests. Try again in a moment.',
  AUTH_ERROR: 'The server could not authenticate with the AI provider.',
  INSUFFICIENT_CREDITS: 'The AI provider account is out of credits.',
  PROVIDER_ERROR: 'The AI provider returned an error.',
  INVALID_RESPONSE: 'The AI provider returned a response that could not be used.',
}

export function aiErrorMessage(code: string | null): string {
  return (code && AI_ERROR_MESSAGES[code]) || 'The AI could not process this message.'
}
