export type WorkItemSystemField
  = | 'key'
    | 'title'
    | 'issueType'
    | 'status'
    | 'priority'
    | 'assignee'
    | 'sprint'
    | 'updatedAt'

export interface WorkItemPropertyField {
  kind: 'property'
  apiId?: string
  code?: string
}

export type WorkItemField = WorkItemSystemField | WorkItemPropertyField

export interface WorkItemDisplayField {
  field: WorkItemField
  width?: number
  pinned?: boolean
}

export interface WorkItemNamedSummary {
  id: string
  code: string
  name: string
  color: string | null
  icon?: string | null
}

export interface WorkItemStatusSummary extends WorkItemNamedSummary {
  group: string
  terminal: boolean
}

export interface WorkItemUserSummary {
  id: string
  displayName: string
}

export interface WorkItemSprintSummary {
  id: string
  name: string
  status: string
}

export interface WorkItemListItem {
  id: string
  key: string
  title: string
  projectId: string
  issueTypeConfigId: string
  issueType: WorkItemNamedSummary & { icon: string | null }
  status: WorkItemStatusSummary
  priority: WorkItemNamedSummary | null
  reporter: WorkItemUserSummary
  assignee: WorkItemUserSummary | null
  sprint: WorkItemSprintSummary | null
  properties: Record<string, WorkItemPropertyPresentation>
  fieldCapabilities: Record<string, WorkItemFieldCapability>
  createdAt: string
  updatedAt: string
}

export type WorkItemFieldCapabilityState = 'EDITABLE' | 'READ_ONLY' | 'UNAVAILABLE'

export interface WorkItemFieldCapability {
  state: WorkItemFieldCapabilityState
  reason: string | null
}

export interface WorkItemPropertyPresentation {
  property: {
    id: string
    code: string
    name: string
    dataType: string
    array: boolean
  }
  value: unknown
  displayValue: unknown
}

export interface WorkItemDisplayFieldDefinition {
  key: string
  name: string
  dataType: string
  array: boolean
  propertyId: string | null
  validation: Record<string, unknown>
}

export interface WorkItemFieldOption {
  id: string
  label: string
  description: string | null
  color: string | null
  icon: string | null
  status: string | null
}

export interface WorkItemTransitionOption {
  id: string
  name: string
  enabled: boolean
  reason: string | null
  targetStatus: WorkItemStatusSummary | null
}

export interface WorkItemPatch {
  assigneeId?: string
  priorityId?: string
  sprintId?: string
  clearSprint?: boolean
  properties?: Record<string, unknown>
}

export interface WorkItemSearchInput {
  projectId: string
  query: Record<string, unknown>
  scope?: Record<string, unknown>
  limit?: number
}

export interface WorkItemSearchPage {
  items: WorkItemListItem[]
  nextCursor?: string
}
