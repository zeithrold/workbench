import type { WorkItemDisplayField, WorkItemSystemField } from '$lib/entities/work-item/index.js'

export interface WorkItemColumnDefinition {
  id: string
  field: WorkItemDisplayField['field']
  label: string
  defaultWidth: number
  minWidth: number
  maxWidth: number
  required?: boolean
  dataType?: string
  array?: boolean
}

export const WORK_ITEM_COLUMNS: readonly WorkItemColumnDefinition[] = [
  { id: 'key', field: 'key', label: 'Key', defaultWidth: 110, minWidth: 80, maxWidth: 600 },
  { id: 'title', field: 'title', label: 'Title', defaultWidth: 360, minWidth: 240, maxWidth: 720, required: true },
  { id: 'issueType', field: 'issueType', label: 'Type', defaultWidth: 140, minWidth: 80, maxWidth: 600 },
  { id: 'status', field: 'status', label: 'Status', defaultWidth: 140, minWidth: 80, maxWidth: 600 },
  { id: 'priority', field: 'priority', label: 'Priority', defaultWidth: 120, minWidth: 80, maxWidth: 600 },
  { id: 'assignee', field: 'assignee', label: 'Assignee', defaultWidth: 180, minWidth: 80, maxWidth: 600 },
  { id: 'sprint', field: 'sprint', label: 'Sprint', defaultWidth: 160, minWidth: 80, maxWidth: 600 },
  { id: 'updatedAt', field: 'updatedAt', label: 'Updated', defaultWidth: 180, minWidth: 80, maxWidth: 600 },
] as const

const columnsById = new Map(WORK_ITEM_COLUMNS.map(column => [column.id, column]))

export const DEFAULT_WORK_ITEM_DISPLAY_FIELDS: WorkItemDisplayField[] = WORK_ITEM_COLUMNS.map(column => ({
  field: column.field,
  width: column.defaultWidth,
  pinned: column.id === 'key',
}))

export function fieldId(field: WorkItemDisplayField['field']): string | null {
  return typeof field === 'string' ? field : field.code ? `property.${field.code}` : field.apiId ? `property.${field.apiId}` : null
}

export function normalizeDisplayFields(
  fields: readonly WorkItemDisplayField[],
  columns: readonly WorkItemColumnDefinition[] = WORK_ITEM_COLUMNS,
): WorkItemDisplayField[] {
  const availableById = new Map(columns.map(column => [column.id, column]))
  const seen = new Set<string>()
  const normalized: WorkItemDisplayField[] = []

  for (const field of fields) {
    const id = fieldId(field.field)
    if (!id || !availableById.has(id) || seen.has(id))
      continue
    const definition = availableById.get(id)!
    seen.add(id)
    normalized.push({
      field: definition.field,
      width: Math.min(definition.maxWidth, Math.max(definition.minWidth, field.width ?? definition.defaultWidth)),
      pinned: Boolean(field.pinned),
    })
  }

  if (!seen.has('title')) {
    const title = availableById.get('title') ?? columnsById.get('title')!
    normalized.splice(Math.min(1, normalized.length), 0, {
      field: title.field,
      width: title.defaultWidth,
      pinned: false,
    })
  }

  return normalized
}

export function propertyColumn(
  code: string,
  label: string,
  apiId?: string,
  dataType?: string,
  array = false,
): WorkItemColumnDefinition {
  return {
    id: `property.${code}`,
    field: { kind: 'property', code, apiId },
    label,
    defaultWidth: 180,
    minWidth: 100,
    maxWidth: 600,
    dataType,
    array,
  }
}

export function updateDisplayField(
  fields: readonly WorkItemDisplayField[],
  id: WorkItemSystemField,
  update: Partial<Omit<WorkItemDisplayField, 'field'>>,
): WorkItemDisplayField[] {
  return normalizeDisplayFields(
    normalizeDisplayFields(fields).map(field => field.field === id ? { ...field, ...update } : field),
  )
}

export function moveDisplayField(
  fields: readonly WorkItemDisplayField[],
  id: WorkItemSystemField,
  direction: -1 | 1,
): WorkItemDisplayField[] {
  const next = normalizeDisplayFields(fields)
  const index = next.findIndex(field => field.field === id)
  const target = index + direction
  if (index < 0 || target < 0 || target >= next.length) {
    return next
  }
  const current = next[index]
  next[index] = next[target]
  next[target] = current
  return next
}

export function setColumnVisible(
  fields: readonly WorkItemDisplayField[],
  id: WorkItemSystemField,
  visible: boolean,
): WorkItemDisplayField[] {
  const normalized = normalizeDisplayFields(fields)
  if (!visible)
    return id === 'title' ? normalized : normalized.filter(field => field.field !== id)
  if (normalized.some(field => field.field === id))
    return normalized
  const definition = columnsById.get(id)!
  return [...normalized, { field: id, width: definition.defaultWidth, pinned: false }]
}
