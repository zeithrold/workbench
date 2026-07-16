<script lang='ts'>
  import type { SelectorOption } from '$lib/shared/ui'
  import type { PermissionPolicyDocument, PermissionPolicyRuleDocument } from './permission-document.js'
  import type { TenantPermissionActionOption } from './tenant-permission-document.js'
  import * as ButtonGroup from '$lib/components/ui/button-group'
  import * as Dialog from '$lib/components/ui/dialog'
  import * as Field from '$lib/components/ui/field'
  import { Textarea } from '$lib/components/ui/textarea'
  import * as Tooltip from '$lib/components/ui/tooltip'
  import { m } from '$lib/paraglide/messages.js'
  import { Badge, Button, Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle, Input, JsonEditor, SearchableSelect } from '$lib/shared/ui'
  import CopyIcon from '@lucide/svelte/icons/copy'
  import FileJsonIcon from '@lucide/svelte/icons/file-json'
  import TrashIcon from '@lucide/svelte/icons/trash-2'
  import { emptyPermissionPolicyDocument, serializePermissionDocument } from './permission-document.js'
  import PermissionEffectToggle from './permission-effect-toggle.svelte'
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
  const actionOptions = $derived(actions.map(action => ({ id: action.code, label: action.name, description: action.description ?? action.code, group: action.resourcePattern === 'tenant:*' ? m.permission_tenant() : m.permission_tenant_access_control() })))

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

{#snippet actionTriggerContent(option: SelectorOption)}
  <span class='truncate' data-slot='permission-action-content'>{option.label}</span>
{/snippet}

{#snippet actionOptionContent(option: SelectorOption)}
  <span class='min-w-0 text-left' data-slot='permission-action-content'><span class='block truncate'>{option.label}</span>{#if option.description}<span class='block truncate text-xs text-muted-foreground'>{option.description}</span>{/if}</span>
{/snippet}

<div class='space-y-5' data-slot='tenant-permission-editor'>
  <Card>
    <CardHeader>
      <CardTitle>{m.permission_policy_details()}</CardTitle>
      <CardDescription>{m.permission_tenant_details_description()}</CardDescription>
      <CardAction>
        <Button type='button' variant='outline' size='sm' onclick={() => sourceOpen = true}>
          <FileJsonIcon class='size-3.5' /> {m.permission_advanced_json()}
        </Button>
      </CardAction>
    </CardHeader>
    <CardContent><Field.Group class='grid gap-4 md:grid-cols-2'>
      <Field.Field><Field.Label for='policy-code'>{m.permission_code()}</Field.Label><Input id='policy-code' value={document.code} disabled={!editable || !codeEditable} oninput={event => commit({ ...document, code: event.currentTarget.value })} /></Field.Field>
      <Field.Field><Field.Label for='policy-name'>{m.permission_name()}</Field.Label><Input id='policy-name' value={document.name} disabled={!editable} oninput={event => commit({ ...document, name: event.currentTarget.value })} /></Field.Field>
      <Field.Field class='md:col-span-2'><Field.Label for='policy-description'>{m.permission_description()}</Field.Label><Textarea id='policy-description' value={document.description ?? ''} disabled={!editable} oninput={event => commit({ ...document, description: event.currentTarget.value || null })} /></Field.Field>
    </Field.Group></CardContent>
  </Card>

  <section class='space-y-3' aria-labelledby='tenant-policy-rules'>
    <div class='flex flex-wrap items-end justify-between gap-3'>
      <div><h2 id='tenant-policy-rules' class='text-lg font-semibold'>{m.permission_tenant_rules()}</h2><p class='text-sm text-muted-foreground'>{m.permission_tenant_rules_description()}</p></div>
      {#if editable}<Button type='button' disabled={actions.length === 0} onclick={() => actions[0] && commit({ ...document, rules: [...document.rules, emptyTenantPermissionRule(actions[0])] })}>{m.permission_add_tenant_permission()}</Button>{/if}
    </div>

    {#each document.rules as rule, index (rule.id ?? index)}
      {@const action = actions.find(option => option.code === rule.action)}
      <Card
        class={rule.effect === 'DENY' ? 'overflow-visible border-destructive' : 'overflow-visible'}
      >
        <CardHeader>
          <div class='flex items-center gap-2'><Badge variant={rule.effect === 'DENY' ? 'destructive' : 'secondary'}>{rule.effect === 'DENY' ? m.permission_deny() : m.permission_allow()}</Badge><CardTitle class='text-base'>{action?.name ?? rule.action}</CardTitle></div>
          <CardDescription>{action?.description ?? m.permission_unavailable_capability()}</CardDescription>
          {#if editable}<CardAction><ButtonGroup.Root><Tooltip.Root><Tooltip.Trigger>{#snippet child({ props })}<Button {...props} type='button' variant='outline' size='icon-xs' aria-label={m.permission_duplicate_tenant_permission()} onclick={() => commit({ ...document, rules: [...document.rules.slice(0, index + 1), { ...JSON.parse(JSON.stringify(rule)) as PermissionPolicyRuleDocument, id: undefined }, ...document.rules.slice(index + 1)] })}><CopyIcon class='size-3.5' /></Button>{/snippet}</Tooltip.Trigger><Tooltip.Content>{m.permission_duplicate_tenant_permission()}</Tooltip.Content></Tooltip.Root><Tooltip.Root><Tooltip.Trigger>{#snippet child({ props })}<Button {...props} type='button' variant='outline' size='icon-xs' aria-label={m.permission_delete_tenant_permission()} onclick={() => commit({ ...document, rules: document.rules.filter((_, ruleIndex) => ruleIndex !== index) })}><TrashIcon class='size-3.5' /></Button>{/snippet}</Tooltip.Trigger><Tooltip.Content>{m.permission_delete_tenant_permission()}</Tooltip.Content></Tooltip.Root></ButtonGroup.Root></CardAction>{/if}
        </CardHeader>
        <CardContent class='grid gap-4 md:grid-cols-[1fr_auto]'>
          <Field.Field><Field.Label>{m.permission_capability()}</Field.Label><SearchableSelect value={rule.action} options={actionOptions} disabled={!editable} required clearable={false} triggerContent={actionTriggerContent} optionContent={actionOptionContent} onValueChange={code => selectRuleAction(index, rule, code)} /></Field.Field>
          <Field.Field><Field.Label>{m.permission_effect()}</Field.Label><PermissionEffectToggle value={rule.effect} disabled={!editable} onChange={effect => updateRule(index, { ...rule, effect })} /></Field.Field>
          <div class='rounded-md border bg-muted/30 px-3 py-2 text-sm md:col-span-2'><span class='font-medium'>{m.permission_scope()}</span> {rule.resourcePattern === 'tenant:*' ? m.permission_entire_tenant() : m.permission_tenant_access_control()}</div>
        </CardContent>
      </Card>
    {:else}
      <div class='rounded-lg border border-dashed p-8 text-center'><p class='font-medium'>{m.permission_no_tenant_rules()}</p><p class='mt-1 text-sm text-muted-foreground'>{m.permission_no_tenant_rules_description()}</p></div>
    {/each}
  </section>

  {#if errors.length > 0}<div class='rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive' role='alert'><p class='font-medium'>{m.permission_resolve_errors()}</p><ul class='mt-2 list-disc pl-5'>{#each errors as error (error)}<li>{error}</li>{/each}</ul></div>{/if}
</div>

<Dialog.Root bind:open={sourceOpen}>
  <Dialog.Content class='max-h-[90vh] overflow-y-auto sm:max-w-4xl'>
    <Dialog.Header><Dialog.Title>{m.permission_tenant_json_title()}</Dialog.Title><Dialog.Description>{m.permission_tenant_json_description()}</Dialog.Description></Dialog.Header>
    <JsonEditor value={source} schema={tenantPermissionPolicyJsonSchema(actions)} {modelId} readOnly={!editable} ariaLabel='Tenant permission policy JSON' onChange={updateSource} onValidityChange={updateJsonValidity} />
    {#if errors.length > 0}<div class='rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive' role='alert'><ul class='list-disc pl-5'>{#each errors as error (error)}<li>{error}</li>{/each}</ul></div>{/if}
    <Dialog.Footer><Button type='button' variant='outline' onclick={() => sourceOpen = false}>{m.close()}</Button></Dialog.Footer>
  </Dialog.Content>
</Dialog.Root>
