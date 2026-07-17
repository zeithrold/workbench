<script lang='ts'>
  import type { SelectorOption } from '$lib/shared/ui'
  import type { PermissionPolicyDocument, PermissionPolicyRuleDocument } from './permission-document.js'
  import type { PermissionPolicyEditorContext } from './permission-policy-editor-core.js'
  import type { TenantPermissionActionOption } from './tenant-permission-document.js'
  import * as ButtonGroup from '$lib/components/ui/button-group'
  import * as Field from '$lib/components/ui/field'
  import { m } from '$lib/paraglide/messages.js'
  import { Badge, Button, Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle, SearchableSelect } from '$lib/shared/ui'
  import CopyIcon from '@lucide/svelte/icons/copy'
  import TrashIcon from '@lucide/svelte/icons/trash-2'
  import PermissionEffectToggle from './permission-effect-toggle.svelte'
  import PermissionPolicyEditorCore from './permission-policy-editor-core.svelte'
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
  const actionOptions = $derived(actions.map(action => ({ id: action.code, label: action.name, description: action.description ?? action.code, group: action.resourcePattern === 'tenant:*' ? m.permission_tenant() : m.permission_tenant_access_control() })))

  function selectRuleAction(context: PermissionPolicyEditorContext, index: number, rule: PermissionPolicyRuleDocument, code: string | null) {
    const selected = actions.find(option => option.code === code)
    if (selected) {
      context.commit({
        ...context.document,
        rules: context.document.rules.map((item, itemIndex) => itemIndex === index ? { ...rule, action: selected.code, resourcePattern: selected.resourcePattern, condition: null } : item),
      })
    }
  }
</script>

{#snippet actionTriggerContent(option: SelectorOption)}<span class='truncate' data-slot='permission-action-content'>{option.label}</span>{/snippet}
{#snippet actionOptionContent(option: SelectorOption)}<span class='min-w-0 text-left' data-slot='permission-action-content'><span class='block truncate'>{option.label}</span>{#if option.description}<span class='block truncate text-xs text-muted-foreground'>{option.description}</span>{/if}</span>{/snippet}

{#snippet rules(context: PermissionPolicyEditorContext)}
  {@const document = context.document}
  {@const commit = context.commit}
  <section class='space-y-3' aria-labelledby={`${modelId}-rules-title`}>
    <div class='flex flex-wrap items-end justify-between gap-3'><div><h2 id={`${modelId}-rules-title`} class='text-lg font-semibold'>{m.permission_tenant_rules()}</h2><p class='text-sm text-muted-foreground'>{m.permission_tenant_rules_description()}</p></div>{#if !editable}<Badge variant='secondary'>{m.permission_read_only()}</Badge>{:else}<Button type='button' disabled={actions.length === 0} onclick={() => actions[0] && commit({ ...document, rules: [...document.rules, emptyTenantPermissionRule(actions[0])] })}>{m.permission_add_tenant_permission()}</Button>{/if}</div>
    {#each document.rules as rule, index (rule.id ?? index)}
      {@const action = actions.find(option => option.code === rule.action)}
      <Card class={rule.effect === 'DENY' ? 'overflow-visible border-destructive' : 'overflow-visible'}>
        <CardHeader><div class='flex items-center gap-2'><Badge variant={rule.effect === 'DENY' ? 'outline' : 'secondary'} class={rule.effect === 'DENY' ? 'border-destructive/40 text-red-700 dark:text-red-400' : undefined}>{rule.effect === 'DENY' ? m.permission_deny() : m.permission_allow()}</Badge><CardTitle class='text-base'>{action?.name ?? rule.action}</CardTitle></div><CardDescription>{action?.description ?? m.permission_unavailable_capability()}</CardDescription>{#if editable}<CardAction><ButtonGroup.Root><Button type='button' variant='outline' size='icon-xs' aria-label={m.permission_duplicate_tenant_permission()} title={m.permission_duplicate_tenant_permission()} onclick={() => commit({ ...document, rules: [...document.rules.slice(0, index + 1), { ...JSON.parse(JSON.stringify(rule)) as PermissionPolicyRuleDocument, id: undefined }, ...document.rules.slice(index + 1)] })}><CopyIcon class='size-3.5' /></Button><Button type='button' variant='outline' size='icon-xs' aria-label={m.permission_delete_tenant_permission()} title={m.permission_delete_tenant_permission()} onclick={() => commit({ ...document, rules: document.rules.filter((_, ruleIndex) => ruleIndex !== index) })}><TrashIcon class='size-3.5' /></Button></ButtonGroup.Root></CardAction>{/if}</CardHeader>
        <CardContent class='grid gap-4 md:grid-cols-[1fr_auto]'>
          <Field.Field><Field.Label>{m.permission_capability()}</Field.Label><SearchableSelect value={rule.action} options={actionOptions} disabled={!editable} required clearable={false} triggerContent={actionTriggerContent} optionContent={actionOptionContent} onValueChange={code => selectRuleAction(context, index, rule, code)} /></Field.Field>
          <Field.Field><Field.Label>{m.permission_effect()}</Field.Label><PermissionEffectToggle value={rule.effect} disabled={!editable} onChange={effect => commit({ ...document, rules: document.rules.map((item, itemIndex) => itemIndex === index ? { ...rule, effect } : item) })} /></Field.Field>
          <div class='rounded-md border bg-muted/30 px-3 py-2 text-sm md:col-span-2'><span class='font-medium'>{m.permission_scope()}</span> {rule.resourcePattern === 'tenant:*' ? m.permission_entire_tenant() : m.permission_tenant_access_control()}</div>
        </CardContent>
      </Card>
    {:else}<div class='rounded-lg border border-dashed p-8 text-center'><p class='font-medium'>{m.permission_no_tenant_rules()}</p><p class='mt-1 text-sm text-muted-foreground'>{m.permission_no_tenant_rules_description()}</p></div>{/each}
  </section>
{/snippet}

<div data-slot='tenant-permission-editor'>
  <PermissionPolicyEditorCore
    {value}
    {editable}
    {codeEditable}
    {modelId}
    detailsDescription={m.permission_tenant_details_description()}
    jsonTitle={m.permission_tenant_json_title()}
    jsonDescription={m.permission_tenant_json_description()}
    jsonAriaLabel='Tenant permission policy JSON'
    schema={tenantPermissionPolicyJsonSchema(actions)}
    validateDocument={document => validateTenantPermissionDocument(document, actions)}
    parseDocument={source => parseTenantPermissionDocument(source, actions)}
    {onChange}
    {onValidityChange}
    {rules}
  />
</div>
