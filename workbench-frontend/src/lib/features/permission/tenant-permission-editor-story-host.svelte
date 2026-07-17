<script lang='ts'>
  import type { PermissionPolicyDocument } from './permission-document.js'
  import type { TenantPermissionActionOption } from './tenant-permission-document.js'
  import { untrack } from 'svelte'
  import TenantPermissionEditor from './tenant-permission-editor.svelte'

  const { initialValue, actions, editable = true, codeEditable = false, modelId, showDocument = true, onChange = () => {}, onValidityChange = () => {} }: {
    initialValue: PermissionPolicyDocument
    actions: TenantPermissionActionOption[]
    editable?: boolean
    codeEditable?: boolean
    modelId: string
    showDocument?: boolean
    onChange?: (value: PermissionPolicyDocument) => void
    onValidityChange?: (valid: boolean) => void
  } = $props()
  let value = $state.raw(untrack(() => JSON.parse(JSON.stringify(initialValue)) as PermissionPolicyDocument))

  function update(nextValue: PermissionPolicyDocument) {
    value = nextValue
    onChange(nextValue)
  }
</script>

<div class='mx-auto w-[min(64rem,calc(100vw-2rem))] space-y-4 rounded-xl bg-background p-4 text-foreground'>
  <TenantPermissionEditor {value} {actions} {editable} {codeEditable} {modelId} onChange={update} {onValidityChange} />
  {#if showDocument}<details open class='max-h-72 overflow-auto rounded-lg border bg-muted p-4'><summary class='sr-only'>Permission document JSON</summary><pre data-testid='permission-document-json' class='text-xs'>{JSON.stringify(value, null, 2)}</pre></details>{/if}
</div>
