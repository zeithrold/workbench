import type { SelectorOption } from '$lib/shared/ui'

export type WorkItemSelectorKind = 'type' | 'status' | 'assignee' | 'priority' | 'sprint'
export type WorkItemScalarValue = string | number | boolean | null

export type WorkItemPropertyDataType
  = | 'text' | 'long_text' | 'number' | 'boolean' | 'date' | 'datetime'
    | 'single_select' | 'multi_select' | 'user' | 'multi_user'
    | 'project' | 'issue' | 'url' | 'json'

export interface WorkItemPropertyDefinition {
  id: string
  code: string
  name: string
  description?: string
  dataType: WorkItemPropertyDataType
  isArray: boolean
  validationSchema: Record<string, unknown>
  searchConfig: Record<string, unknown>
}

export interface WorkItemSelectorData {
  options: SelectorOption[]
  loading?: boolean
  onSearchChange?: (query: string) => void
}

export const supportedSelectorTypes = new Set<WorkItemPropertyDataType>([
  'single_select',
  'multi_select',
  'user',
  'multi_user',
  'project',
  'issue',
])
