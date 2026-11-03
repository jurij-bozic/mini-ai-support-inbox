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
