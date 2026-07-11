<script lang='ts'>
  import type { SelectorOption } from '$lib/shared/ui'
  import type { WorkItemSelectorKind } from './selector-types.js'
  import { SearchableSelect } from '$lib/shared/ui'
  import WorkItemField from './work-item-field.svelte'

  const { kind, value, options, onValueChange, disabled = false, required = false }: {
    kind: WorkItemSelectorKind
    value?: string | null
    options: SelectorOption[]
    onValueChange: (value: string | null) => void
    disabled?: boolean
    required?: boolean
  } = $props()

  const labels = { type: 'Type', status: 'Status', assignee: 'Assignee', priority: 'Priority', sprint: 'Sprint' } satisfies Record<WorkItemSelectorKind, string>
  const placeholders = { type: 'Select type', status: 'Select status', assignee: 'Unassigned', priority: 'Select priority', sprint: 'Backlog' } satisfies Record<WorkItemSelectorKind, string>
</script>

<WorkItemField label={labels[kind]} {required}>
  <SearchableSelect {value} {options} {onValueChange} {disabled} {required} placeholder={placeholders[kind]} searchPlaceholder={`Search ${labels[kind].toLowerCase()}…`} />
</WorkItemField>
