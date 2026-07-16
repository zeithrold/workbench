<script lang='ts'>
  import type { PermissionConditionValue } from './permission-document.js'
  import type { PermissionValueOption } from './permission-editor-model.js'
  import { m } from '$lib/paraglide/messages.js'
  import { SearchableMultiSelect, SearchableSelect } from '$lib/shared/ui'
  import { permissionValueForOptionId, permissionValueOptionId, permissionValueOptionIds, permissionValuesForOptionIds } from './permission-editor-model.js'

  const { value, options, multiple = false, disabled = false, onChange }: {
    value?: PermissionConditionValue
    options: PermissionValueOption[]
    multiple?: boolean
    disabled?: boolean
    onChange: (value: PermissionConditionValue) => void
  } = $props()

  function selectValue(id: string | null) {
    const selected = permissionValueForOptionId(options, id)
    if (selected !== undefined)
      onChange(selected)
  }

  function selectValues(ids: string[]) {
    onChange(permissionValuesForOptionIds(options, ids))
  }
</script>

{#if multiple}
  <SearchableMultiSelect values={permissionValueOptionIds(options, value)} {options} {disabled} onValuesChange={selectValues} placeholder={m.permission_choose_values()} />
{:else}
  <SearchableSelect value={permissionValueOptionId(options, value)} {options} {disabled} required clearable={false} placeholder={m.permission_choose_value()} onValueChange={selectValue} />
{/if}
