<script lang='ts'>
  import type { WorkItemPropertyDefinition, WorkItemScalarValue, WorkItemSelectorData } from './selector-types.js'
  import { m } from '$lib/paraglide/messages.js'
  import { SearchableMultiSelect, SearchableSelect } from '$lib/shared/ui'
  import ScalarPropertyEditor from './scalar-property-editor.svelte'
  import WorkItemField from './work-item-field.svelte'

  const { definition, data = { options: [] }, value = null, values = [], scalarValue = null, onValueChange = () => {}, onValuesChange = () => {}, onScalarValueChange = () => {}, disabled = false, required = false }: {
    definition: WorkItemPropertyDefinition
    data?: WorkItemSelectorData
    value?: string | null
    values?: string[]
    scalarValue?: WorkItemScalarValue
    onValueChange?: (value: string | null) => void
    onValuesChange?: (values: string[]) => void
    onScalarValueChange?: (value: WorkItemScalarValue) => void
    disabled?: boolean
    required?: boolean
  } = $props()

  const selector = $derived(['single_select', 'multi_select', 'user', 'multi_user', 'project', 'issue'].includes(definition.dataType))
  const scalar = $derived(['text', 'number', 'boolean', 'date', 'datetime', 'url'].includes(definition.dataType))
  const multi = $derived(definition.isArray || definition.dataType === 'multi_select' || definition.dataType === 'multi_user')
</script>

<WorkItemField label={definition.name} description={definition.description} {required}>
  {#if scalar}
    <ScalarPropertyEditor {definition} value={scalarValue} onValueChange={onScalarValueChange} {disabled} {required} />
  {:else if !selector}
    <div class='rounded-md border border-dashed px-3 py-2 text-sm text-muted-foreground' data-slot='unsupported-property'>
      {m.work_item_editor_unavailable({ type: definition.dataType.replace('_', ' ') })}
    </div>
  {:else if multi}
    <SearchableMultiSelect {values} options={data.options} {onValuesChange} {disabled} {required} loading={data.loading} onSearchChange={data.onSearchChange} placeholder={m.work_item_select_property({ property: definition.name.toLowerCase() })} />
  {:else}
    <SearchableSelect {value} options={data.options} {onValueChange} {disabled} {required} loading={data.loading} onSearchChange={data.onSearchChange} placeholder={m.work_item_select_property({ property: definition.name.toLowerCase() })} />
  {/if}
</WorkItemField>
