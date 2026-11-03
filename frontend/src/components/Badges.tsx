import type { Category, MessageStatus } from '../api/messages'
import { CATEGORY_LABELS, STATUS_LABELS } from './labels'

export function StatusBadge({ status }: { status: MessageStatus }) {
  return <span className={`badge badge-status-${status.toLowerCase()}`}>{STATUS_LABELS[status]}</span>
}

export function CategoryBadge({ category }: { category: Category | null }) {
  if (!category) return <span className="badge badge-category-none">Unclassified</span>
  return <span className={`badge badge-category-${category.toLowerCase()}`}>{CATEGORY_LABELS[category]}</span>
}
