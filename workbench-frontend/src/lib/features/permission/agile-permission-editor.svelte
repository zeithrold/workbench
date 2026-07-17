<script lang='ts'>
  import type { SelectorOption } from '$lib/shared/ui'
  import type { AgilePermissionActionOption } from './agile-permission-document.js'
  import type { PermissionCondition, PermissionPolicyDocument, PermissionPolicyRuleDocument } from './permission-document.js'
  import type { PermissionFieldOption, PermissionResourceOption } from './permission-editor-model.js'
  import type { PermissionPolicyEditorContext } from './permission-policy-editor-core.js'
  import * as ButtonGroup from '$lib/components/ui/button-group'
  import * as Field from '$lib/components/ui/field'
  import * as Tooltip from '$lib/components/ui/tooltip'
  import { m } from '$lib/paraglide/messages.js'
  import { Badge, Button, Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle, SearchableSelect } from '$lib/shared/ui'
  import CopyIcon from '@lucide/svelte/icons/copy'
  import TrashIcon from '@lucide/svelte/icons/trash-2'
  import { agilePermissionPolicyJsonSchema, emptyAgilePermissionRule, parseAgilePermissionDocument, validateAgilePermissionDocument } from './agile-permission-document.js'
  import ConditionNodeEditor from './condition-node-editor.svelte'
  import { clonePermissionCondition, emptyPermissionPredicate, stripPermissionUiIds, withPermissionUiIds } from './permission-document.js'
  import PermissionEffectToggle from './permission-effect-toggle.svelte'
  import PermissionPolicyEditorCore from './permission-policy-editor-core.svelte'

  const defaultResources: PermissionResourceOption[] = [
    { id: 'project:*', label: m.permission_all_projects() },
    { id: 'issue:*', label: m.permission_all_work_items() },
    { id: 'view:*', label: m.permission_all_views() },
    { id: 'sprint:*', label: m.permission_all_sprints() },
  ]
  const { value, actions = [], resources = defaultResources, fields = [], editable = true, codeEditable = false, modelId, onChange, onValidityChange }: {
    value: PermissionPolicyDocument
    actions?: AgilePermissionActionOption[]
    resources?: PermissionResourceOption[]
    fields?: PermissionFieldOption[]
    editable?: boolean
    codeEditable?: boolean
    modelId: string
    onChange: (value: PermissionPolicyDocument) => void
    onValidityChange?: (valid: boolean) => void
  } = $props()

  const actionOptions = $derived(actions.map(action => ({ id: action.code, label: action.name ?? action.description ?? action.code, description: action.description ?? action.code })))

  function hydrate(next: PermissionPolicyDocument): PermissionPolicyDocument {
    return { ...structuredClone(next), rules: next.rules.map(rule => ({ ...structuredClone(rule), condition: rule.condition ? withPermissionUiIds(structuredClone(rule.condition)) : rule.condition })) }
  }
</script>

{#snippet resourceContent(option: SelectorOption)}
  {@const resource = resources.find(item => item.id === option.id)}
  <span class='flex min-w-0 items-center gap-2'>{#if resource?.id === 'issue:*'}<Badge class='shrink-0 bg-foreground text-background'>{m.permission_all_badge()}</Badge>{:else if resource?.badge}<Badge variant='outline' class='shrink-0'>{resource.badge}</Badge>{/if}<span class='truncate'>{option.label}</span>{#if resource?.resourceKey}<span class='shrink-0 text-xs text-muted-foreground'>{resource.resourceKey}</span>{/if}</span>
{/snippet}

{#snippet actionTriggerContent(option: SelectorOption)}<span class='truncate' data-slot='permission-action-content'>{option.label}</span>{/snippet}
{#snippet actionOptionContent(option: SelectorOption)}<span class='min-w-0 text-left' data-slot='permission-action-content'><span class='block truncate'>{option.label}</span>{#if option.description}<span class='block truncate text-xs text-muted-foreground'>{option.description}</span>{/if}</span>{/snippet}

{#snippet rules(context: PermissionPolicyEditorContext)}
  {@const document = context.document}
  {@const commit = context.commit}
  <section class='space-y-3' aria-labelledby={`${modelId}-rules-title`}>
    <div class='flex flex-wrap items-end justify-between gap-3'><div><h2 id={`${modelId}-rules-title`} class='text-lg font-semibold'>{m.permission_rules()}</h2><p class='text-sm text-muted-foreground'>{m.permission_rules_description()}</p></div>{#if !editable}<Badge variant='secondary'>{m.permission_read_only()}</Badge>{:else}<Button type='button' onclick={() => commit({ ...document, rules: [...document.rules, emptyAgilePermissionRule(actions, resources)] })}>{m.permission_add_rule()}</Button>{/if}</div>
    {#each document.rules as rule, index (rule.id ?? index)}
      <Card class={rule.effect === 'DENY' ? 'overflow-visible border-destructive/50' : 'overflow-visible'}>
        <CardHeader class='flex-row items-center justify-between gap-3'><div class='min-w-0'><CardTitle class='text-base'>{m.permission_rule_number({ number: index + 1 })}</CardTitle><CardDescription>{rule.effect === 'DENY' ? m.permission_deny_description() : m.permission_allow_description()}</CardDescription></div>{#if editable}<CardAction><ButtonGroup.Root><Tooltip.Root><Tooltip.Trigger>{#snippet child({ props })}<Button {...props} type='button' variant='outline' size='icon-xs' aria-label={m.permission_duplicate()} onclick={() => commit({ ...document, rules: [...document.rules.slice(0, index + 1), { ...JSON.parse(JSON.stringify(rule)) as PermissionPolicyRuleDocument, id: undefined, condition: rule.condition ? clonePermissionCondition(rule.condition) : rule.condition }, ...document.rules.slice(index + 1)] })}><CopyIcon class='size-3.5' /></Button>{/snippet}</Tooltip.Trigger><Tooltip.Content>{m.permission_duplicate()}</Tooltip.Content></Tooltip.Root><Button type='button' variant='outline' size='icon-xs' aria-label={m.permission_delete()} onclick={() => commit({ ...document, rules: document.rules.filter((_, ruleIndex) => ruleIndex !== index) })}><TrashIcon class='size-3.5' /></Button></ButtonGroup.Root></CardAction>{/if}</CardHeader>
        <CardContent class='space-y-5'>
          <Field.Group class='grid gap-3 md:grid-cols-3'>
            <Field.Field><Field.Label>{m.permission_action()}</Field.Label><SearchableSelect value={rule.action} options={actionOptions} disabled={!editable} required clearable={false} placeholder={m.permission_choose_action()} triggerContent={actionTriggerContent} optionContent={actionOptionContent} onValueChange={code => code && commit({ ...document, rules: document.rules.map((item, itemIndex) => itemIndex === index ? { ...rule, action: code } : item) })} /></Field.Field>
            <Field.Field><Field.Label>{m.permission_resource()}</Field.Label><SearchableSelect value={rule.resourcePattern} options={resources} disabled={!editable} required clearable={false} placeholder={m.permission_choose_resource()} triggerContent={resourceContent} optionContent={resourceContent} onValueChange={resource => resource && commit({ ...document, rules: document.rules.map((item, itemIndex) => itemIndex === index ? { ...rule, resourcePattern: resource } : item) })} /></Field.Field>
            <Field.Field><Field.Label>{m.permission_effect()}</Field.Label><PermissionEffectToggle value={rule.effect} disabled={!editable} onChange={effect => commit({ ...document, rules: document.rules.map((item, itemIndex) => itemIndex === index ? { ...rule, effect } : item) })} /></Field.Field>
          </Field.Group>
          {#if rule.condition}
            <div class='space-y-2'><div class='flex items-center justify-between gap-3'><div><h3 class='text-sm font-medium'>{m.permission_condition()}</h3><p class='text-xs text-muted-foreground'>{m.permission_condition_description()}</p></div>{#if editable}<Button type='button' variant='ghost' size='sm' onclick={() => commit({ ...document, rules: document.rules.map((item, itemIndex) => itemIndex === index ? { ...rule, condition: null } : item) })}>{m.permission_remove_condition()}</Button>{/if}</div><ConditionNodeEditor node={rule.condition} {fields} {editable} onChange={(condition: PermissionCondition) => commit({ ...document, rules: document.rules.map((item, itemIndex) => itemIndex === index ? { ...rule, condition } : item) })} /></div>
          {:else if editable}
            <ButtonGroup.Root><Button type='button' variant='outline' size='sm' disabled={fields.length === 0} onclick={() => commit({ ...document, rules: document.rules.map((item, itemIndex) => itemIndex === index ? { ...rule, condition: withPermissionUiIds(emptyPermissionPredicate()) } : item) })}>{m.permission_add_condition()}</Button><Button type='button' variant='outline' size='sm' disabled={fields.length === 0} onclick={() => commit({ ...document, rules: document.rules.map((item, itemIndex) => itemIndex === index ? { ...rule, condition: withPermissionUiIds({ op: 'and', args: [emptyPermissionPredicate()] }) } : item) })}>{m.permission_add_group()}</Button></ButtonGroup.Root>
          {/if}
        </CardContent>
      </Card>
    {:else}<div class='rounded-lg border border-dashed p-8 text-center'><p class='font-medium'>{m.permission_no_rules()}</p><p class='mt-1 text-sm text-muted-foreground'>{m.permission_no_rules_description()}</p></div>{/each}
  </section>
{/snippet}

<Tooltip.Provider>
  <div data-slot='agile-permission-editor'>
    <PermissionPolicyEditorCore
      {value}
      {editable}
      {codeEditable}
      {modelId}
      detailsDescription={m.permission_policy_details_description()}
      jsonTitle={m.permission_advanced_json_title()}
      jsonDescription={m.permission_advanced_json_description()}
      jsonAriaLabel='Agile permission policy JSON'
      schema={agilePermissionPolicyJsonSchema(actions, resources)}
      validateDocument={document => validateAgilePermissionDocument(document, actions, resources, fields)}
      parseDocument={source => parseAgilePermissionDocument(source, actions, resources, fields)}
      hydrateDocument={hydrate}
      cleanDocument={stripPermissionUiIds}
      {onChange}
      {onValidityChange}
      {rules}
    />
  </div>
</Tooltip.Provider>
