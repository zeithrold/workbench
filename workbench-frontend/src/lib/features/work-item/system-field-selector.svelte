<script lang='ts'>
  import type { SelectorOption } from '$lib/shared/ui'
  import type { WorkItemSelectorKind } from './selector-types.js'
  import { m } from '$lib/paraglide/messages.js'
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

  const labels = $derived({ type: m.field_type(), status: m.field_status(), assignee: m.field_assignee(), priority: m.field_priority(), sprint: m.field_sprint() } satisfies Record<WorkItemSelectorKind, string>)
  const placeholders = $derived({ type: m.select_type(), status: m.select_status(), assignee: m.unassigned(), priority: m.select_priority(), sprint: m.backlog() } satisfies Record<WorkItemSelectorKind, string>)
</script>

<WorkItemField label={labels[kind]} {required}>
  <SearchableSelect {value} {options} {onValueChange} {disabled} {required} placeholder={placeholders[kind]} searchPlaceholder={m.search_field({ field: labels[kind].toLowerCase() })} />
</WorkItemField>
