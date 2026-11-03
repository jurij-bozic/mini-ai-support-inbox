import { apiFetch } from './client'

export const MESSAGE_STATUSES = ['PENDING_AI', 'DRAFT_READY', 'AI_FAILED', 'APPROVED'] as const
export const CATEGORIES = ['QUESTION', 'COMPLAINT', 'PRAISE', 'OTHER'] as const

export type MessageStatus = (typeof MESSAGE_STATUSES)[number]
export type Category = (typeof CATEGORIES)[number]

export interface MessageSummary {
  id: string
  customerName: string | null
  customerEmail: string | null
  contentPreview: string
  category: Category | null
  status: MessageStatus
  createdAt: string
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ListMessagesParams {
  status?: MessageStatus
  category?: Category
  page?: number
  size?: number
}

export function listMessages(
  { status, category, page = 0, size = 20 }: ListMessagesParams,
  options: { signal?: AbortSignal; onSlow?: () => void } = {},
): Promise<PageResponse<MessageSummary>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  if (status) query.set('status', status)
  if (category) query.set('category', category)
  return apiFetch<PageResponse<MessageSummary>>(`/api/messages?${query}`, options)
}
