<script lang='ts'>
  import type { PermissionPolicyDocument, PermissionPolicyRuleDocument } from './permission-document.js'
  import type { TenantPermissionActionOption } from './tenant-permission-document.js'
  import * as Dialog from '$lib/components/ui/dialog'
  import { Textarea } from '$lib/components/ui/textarea'
  import { Badge, Button, Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle, Input, JsonEditor, Label, SearchableSelect } from '$lib/shared/ui'
  import CopyIcon from '@lucide/svelte/icons/copy'
  import FileJsonIcon from '@lucide/svelte/icons/file-json'
  import TrashIcon from '@lucide/svelte/icons/trash-2'
  import { emptyPermissionPolicyDocument, serializePermissionDocument } from './permission-document.js'
  import { emptyTenantPermissionRule, parseTenantPermissionDocument, tenantPermissionPolicyJsonSchema, validateTenantPermissionDocument } from './tenant-permission-document.js'

  const { value, actions, editable = true, codeEditable = false, modelId, onChange, onValidityChange }: {
    value: PermissionPolicyDocument
    actions: TenantPermissionActionOption[]
    editable?: boolean
    codeEditable?: boolean
    modelId: string
    onChange: (value: PermissionPolicyDocument) => void
    onValidityChange?: (valid: boolean) => void
  } = $props()

  let document = $state<PermissionPolicyDocument>(emptyPermissionPolicyDocument())
  let source = $state('')
  let sourceOpen = $state(false)
  let errors = $state<string[]>([])
  const actionOptions = $derived(actions.map(action => ({ id: action.code, label: action.name, description: action.description ?? action.code, group: action.resourcePattern === 'tenant:*' ? 'Tenant' : 'Access control' })))

  function commit(next: PermissionPolicyDocument) {
    document = next
    source = serializePermissionDocument(next)
    errors = validateTenantPermissionDocument(next, actions)
    onValidityChange?.(errors.length === 0)
    onChange(next)
  }

  function updateRule(index: number, next: PermissionPolicyRuleDocument) {
    commit({ ...document, rules: document.rules.map((rule, ruleIndex) => ruleIndex === index ? next : rule) })
  }

  function selectRuleAction(index: number, rule: PermissionPolicyRuleDocument, code: string | null) {
    const selected = actions.find(option => option.code === code)
    if (selected)
      updateRule(index, { ...rule, action: selected.code, resourcePattern: selected.resourcePattern, condition: null })
  }

  function updateJsonValidity(valid: boolean) {
    if (!valid && errors.length === 0) {
      errors = ['JSON Schema validation failed.']
      onValidityChange?.(false)
    }
  }

  function updateSource(next: string) {
    source = next
    const result = parseTenantPermissionDocument(next, actions)
    errors = result.errors
    if (result.document && !codeEditable && result.document.code !== value.code)
      errors = ['Policy code cannot be changed after creation.', ...errors]
    onValidityChange?.(errors.length === 0)
    if (result.document && errors.length === 0) {
      document = result.document
      onChange(result.document)
    }
  }

  $effect(() => {
    const incoming = serializePermissionDocument(value)
    if (incoming !== serializePermissionDocument(document)) {
      document = structuredClone(value)
      source = incoming
      errors = validateTenantPermissionDocument(value, actions)
      onValidityChange?.(errors.length === 0)
    }
  })
</script>

<div class='space-y-5' data-slot='tenant-permission-editor'>
  <Card>
    <CardHeader>
      <CardTitle>Policy details</CardTitle>
      <CardDescription>Name this reusable tenant-level policy.</CardDescription>
      <CardAction>
        <Button type='button' variant='outline' size='sm' onclick={() => sourceOpen = true}>
          <FileJsonIcon class='size-4' /> Advanced JSON
        </Button>
      </CardAction>
    </CardHeader>
    <CardContent class='grid gap-4 md:grid-cols-2'>
      <div class='space-y-1.5'><Label for='policy-code'>Code</Label><Input id='policy-code' value={document.code} disabled={!editable || !codeEditable} oninput={event => commit({ ...document, code: event.currentTarget.value })} /></div>
      <div class='space-y-1.5'><Label for='policy-name'>Name</Label><Input id='policy-name' value={document.name} disabled={!editable} oninput={event => commit({ ...document, name: event.currentTarget.value })} /></div>
      <div class='space-y-1.5 md:col-span-2'><Label for='policy-description'>Description</Label><Textarea id='policy-description' value={document.description ?? ''} disabled={!editable} oninput={event => commit({ ...document, description: event.currentTarget.value || null })} /></div>
    </CardContent>
  </Card>

  <section class='space-y-3' aria-labelledby='tenant-policy-rules'>
    <div class='flex flex-wrap items-end justify-between gap-3'>
      <div><h2 id='tenant-policy-rules' class='text-lg font-semibold'>Tenant permissions</h2><p class='text-sm text-muted-foreground'>Deny rules take precedence when the same capability is allowed and denied.</p></div>
      {#if editable}<Button type='button' disabled={actions.length === 0} onclick={() => actions[0] && commit({ ...document, rules: [...document.rules, emptyTenantPermissionRule(actions[0])] })}>Add permission</Button>{/if}
    </div>

    {#each document.rules as rule, index (rule.id ?? index)}
      {@const action = actions.find(option => option.code === rule.action)}
      <Card class={rule.effect === 'DENY' ? 'border-destructive' : undefined}>
        <CardHeader>
          <div class='flex items-center gap-2'><Badge variant={rule.effect === 'DENY' ? 'destructive' : 'secondary'}>{rule.effect === 'DENY' ? 'Deny' : 'Allow'}</Badge><CardTitle class='text-base'>{action?.name ?? rule.action}</CardTitle></div>
          <CardDescription>{action?.description ?? 'This capability is not available in tenant management.'}</CardDescription>
          {#if editable}<CardAction class='flex gap-1'><Button type='button' variant='ghost' size='icon-sm' aria-label='Duplicate permission' onclick={() => commit({ ...document, rules: [...document.rules.slice(0, index + 1), { ...structuredClone(rule), id: undefined }, ...document.rules.slice(index + 1)] })}><CopyIcon class='size-4' /></Button><Button type='button' variant='ghost' size='icon-sm' aria-label='Delete permission' onclick={() => commit({ ...document, rules: document.rules.filter((_, ruleIndex) => ruleIndex !== index) })}><TrashIcon class='size-4' /></Button></CardAction>{/if}
        </CardHeader>
        <CardContent class='grid gap-4 md:grid-cols-[1fr_auto]'>
          <div class='space-y-1.5'><Label>Capability</Label><SearchableSelect value={rule.action} options={actionOptions} disabled={!editable} required clearable={false} onValueChange={code => selectRuleAction(index, rule, code)} /></div>
          <div class='space-y-1.5'><Label>Effect</Label><div class='inline-flex rounded-lg border bg-muted/30 p-1'><Button type='button' size='sm' variant={rule.effect === 'ALLOW' ? 'default' : 'ghost'} disabled={!editable} onclick={() => updateRule(index, { ...rule, effect: 'ALLOW' })}>Allow</Button><Button type='button' size='sm' variant={rule.effect === 'DENY' ? 'destructive' : 'ghost'} disabled={!editable} onclick={() => updateRule(index, { ...rule, effect: 'DENY' })}>Deny</Button></div></div>
          <div class='rounded-md border bg-muted/30 px-3 py-2 text-sm md:col-span-2'><span class='font-medium'>Scope:</span> {rule.resourcePattern === 'tenant:*' ? 'Entire tenant' : 'Tenant access control'}</div>
        </CardContent>
      </Card>
    {:else}
      <div class='rounded-lg border border-dashed p-8 text-center'><p class='font-medium'>No tenant permissions yet</p><p class='mt-1 text-sm text-muted-foreground'>Add at least one permission before saving this policy.</p></div>
    {/each}
  </section>

  {#if errors.length > 0}<div class='rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive' role='alert'><p class='font-medium'>Resolve these policy errors before saving:</p><ul class='mt-2 list-disc pl-5'>{#each errors as error (error)}<li>{error}</li>{/each}</ul></div>{/if}
</div>

<Dialog.Root bind:open={sourceOpen}>
  <Dialog.Content class='max-h-[90vh] overflow-y-auto sm:max-w-4xl'>
    <Dialog.Header><Dialog.Title>Advanced tenant policy JSON</Dialog.Title><Dialog.Description>Only tenant-management capabilities are accepted. Project, Agile, resource, and condition overrides are rejected.</Dialog.Description></Dialog.Header>
    <JsonEditor value={source} schema={tenantPermissionPolicyJsonSchema(actions)} {modelId} readOnly={!editable} ariaLabel='Tenant permission policy JSON' onChange={updateSource} onValidityChange={updateJsonValidity} />
    {#if errors.length > 0}<div class='rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive' role='alert'><ul class='list-disc pl-5'>{#each errors as error (error)}<li>{error}</li>{/each}</ul></div>{/if}
    <Dialog.Footer><Button type='button' variant='outline' onclick={() => sourceOpen = false}>Close</Button></Dialog.Footer>
  </Dialog.Content>
</Dialog.Root>
