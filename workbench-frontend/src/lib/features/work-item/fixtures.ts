import type { SelectorOption } from '$lib/shared/ui'
import type { WorkItemPropertyDefinition } from './selector-types.js'

export const workItemTypes: SelectorOption[] = [
  { id: 'story', label: 'Story', description: 'User-facing product capability', color: '#8b5cf6' },
  { id: 'bug', label: 'Bug', description: 'Unexpected product behavior', color: '#ef4444' },
  { id: 'task', label: 'Task', description: 'A concrete unit of work', color: '#3b82f6' },
]
export const statuses: SelectorOption[] = [
  { id: 'todo', label: 'To do', group: 'Todo', color: '#94a3b8' },
  { id: 'progress', label: 'In progress', group: 'In progress', color: '#f59e0b' },
  { id: 'review', label: 'In review', group: 'In progress', color: '#8b5cf6' },
  { id: 'done', label: 'Done', group: 'Done', color: '#22c55e' },
]
export const users: SelectorOption[] = [
  { id: 'ada', label: 'Ada Lovelace', description: 'ada@northstar.example' },
  { id: 'grace', label: 'Grace Hopper', description: 'grace@northstar.example' },
  { id: 'linus', label: 'Linus Torvalds', description: 'linus@northstar.example' },
  { id: 'margaret', label: 'Margaret Hamilton with an exceptionally long display name', description: 'margaret@northstar.example' },
]
export const priorities: SelectorOption[] = [
  { id: 'urgent', label: 'Urgent', color: '#ef4444' },
  { id: 'high', label: 'High', color: '#f97316' },
  { id: 'medium', label: 'Medium', color: '#eab308' },
  { id: 'low', label: 'Low', color: '#60a5fa' },
]
export const sprints: SelectorOption[] = [
  { id: 's24', label: 'Sprint 24', description: 'Jul 8 – Jul 19 · Active', color: '#22c55e' },
  { id: 's25', label: 'Sprint 25', description: 'Jul 22 – Aug 2 · Planned', color: '#94a3b8' },
]
export const projects: SelectorOption[] = [
  { id: 'web', label: 'Workbench Web', description: 'WEB' },
  { id: 'platform', label: 'Platform Services', description: 'PLAT' },
]
export const issues: SelectorOption[] = [
  { id: 'WEB-128', label: 'WEB-128', description: 'Build the work item detail sidebar' },
  { id: 'WEB-131', label: 'WEB-131', description: 'Add keyboard navigation to selectors' },
  { id: 'PLAT-42', label: 'PLAT-42', description: 'Expose project member search' },
]
export const manyIssues: SelectorOption[] = Array.from({ length: 14 }, (_, index) => ({
  id: `WEB-${150 + index}`,
  label: `WEB-${150 + index}`,
  description: [
    'Improve keyboard navigation',
    'Review configurable field behavior',
    'Document the work item API contract',
  ][index % 3],
}))
export const customOptions: SelectorOption[] = [
  { id: 'customer', label: 'Customer request', color: '#ec4899' },
  { id: 'quality', label: 'Quality', color: '#14b8a6' },
  { id: 'security', label: 'Security', color: '#ef4444' },
  { id: 'performance', label: 'Performance', color: '#f59e0b' },
]

function property(id: string, name: string, dataType: WorkItemPropertyDefinition['dataType'], description: string): WorkItemPropertyDefinition {
  return {
    id,
    code: id,
    name,
    description,
    dataType,
    isArray: dataType.startsWith('multi_'),
    validationSchema: {},
    searchConfig: {},
  }
}
export const dynamicProperties = {
  category: property('category', 'Category', 'single_select', 'Primary classification'),
  labels: property('labels', 'Labels', 'multi_select', 'Additional classifications'),
  reviewer: property('reviewer', 'Reviewer', 'user', 'Responsible reviewer'),
  watchers: property('watchers', 'Watchers', 'multi_user', 'People following updates'),
  project: property('related_project', 'Related project', 'project', 'Owning or related project'),
  issue: property('related_issue', 'Related work item', 'issue', 'Linked dependency'),
  title: { ...property('customer_reference', 'Customer reference', 'text', 'External tracking reference'), validationSchema: { minLength: 3, maxLength: 40 } },
  estimate: { ...property('estimate', 'Estimate', 'number', 'Estimated effort'), validationSchema: { minimum: 0, maximum: 100, multipleOf: 0.5 } },
  billable: property('billable', 'Billable', 'boolean', 'Whether work is billable'),
  targetDate: property('target_date', 'Target date', 'date', 'Expected completion date'),
  releaseAt: property('release_at', 'Release at', 'datetime', 'Scheduled release time'),
  referenceUrl: property('reference_url', 'Reference URL', 'url', 'Related external resource'),
  notes: property('notes', 'Notes', 'long_text', 'Rich text notes'),
  metadata: property('metadata', 'Metadata', 'json', 'Structured metadata'),
} as const
