<script lang='ts'>
  import type { SelectorOption } from '$lib/shared/ui'
  import type { PermissionCondition, PermissionPolicyDocument, PermissionPolicyRuleDocument } from './permission-document.js'
  import type { PermissionFieldOption, PermissionResourceOption } from './permission-editor-model.js'
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
  import { untrack } from 'svelte'
  import ConditionNodeEditor from './condition-node-editor.svelte'
  import { clonePermissionCondition, emptyPermissionPolicyDocument, emptyPermissionPredicate, emptyPermissionRule, parsePermissionDocument, permissionPolicyJsonSchema, serializePermissionDocument, stripPermissionUiIds, withPermissionUiIds } from './permission-document.js'
  import PermissionEffectToggle from './permission-effect-toggle.svelte'

  export interface PermissionActionOption { code: string, name?: string, description?: string | null }

  const defaultResources: PermissionResourceOption[] = [
    { id: '*', label: m.permission_all_resources() },
    { id: 'tenant', label: m.permission_tenant() },
    { id: 'permission', label: m.permission_permissions() },
    { id: 'project:*', label: m.permission_all_projects() },
    { id: 'issue:*', label: m.permission_all_work_items() },
    { id: 'view:*', label: m.permission_all_views() },
    { id: 'sprint:*', label: m.permission_all_sprints() },
  ]
  const { value, actions = [], resources = defaultResources, fields = [], editable = true, codeEditable = false, modelId, onChange, onValidityChange }: {
    value: PermissionPolicyDocument
    actions?: PermissionActionOption[]
    resources?: PermissionResourceOption[]
    fields?: PermissionFieldOption[]
    editable?: boolean
    codeEditable?: boolean
    modelId: string
    onChange: (value: PermissionPolicyDocument) => void
    onValidityChange?: (valid: boolean) => void
  } = $props()

  let document = $state<PermissionPolicyDocument>(emptyPermissionPolicyDocument())
  let source = $state(serializePermissionDocument(emptyPermissionPolicyDocument()))
  let sourceOpen = $state(false)
  let errors = $state<string[]>([])
  const actionOptions = $derived(actions.map(action => ({ id: action.code, label: action.name ?? action.description ?? action.code, description: action.description ?? action.code })))

  function hydrate(next: PermissionPolicyDocument): PermissionPolicyDocument {
    return { ...structuredClone(next), rules: next.rules.map(rule => ({ ...structuredClone(rule), condition: rule.condition ? withPermissionUiIds(structuredClone(rule.condition)) : rule.condition })) }
  }

  function commit(next: PermissionPolicyDocument) {
    document = next
    const clean = stripPermissionUiIds(next)
    source = serializePermissionDocument(clean)
    errors = []
    onValidityChange?.(true)
    onChange(clean)
  }

  function updateRule(index: number, next: PermissionPolicyRuleDocument) {
    commit({ ...document, rules: document.rules.map((rule, ruleIndex) => ruleIndex === index ? next : rule) })
  }

  function updateRuleCondition(index: number, condition: PermissionCondition) {
    commit({
      ...document,
      rules: document.rules.map((rule, ruleIndex) => ruleIndex === index ? { ...rule, condition } : rule),
    })
  }

  function updateSource(next: string) {
    source = next
    const result = parsePermissionDocument(next)
    errors = result.errors
    onValidityChange?.(errors.length === 0)
    if (result.document) {
      document = hydrate(result.document)
      onChange(result.document)
    }
  }

  function updateJsonValidity(valid: boolean) {
    if (!valid && errors.length === 0) {
      errors = ['JSON Schema validation failed.']
      onValidityChange?.(false)
    }
  }

  function selectAction(index: number, rule: PermissionPolicyRuleDocument, code: string | null) {
    if (code)
      updateRule(index, { ...rule, action: code })
  }

  function addRule() {
    const rule = emptyPermissionRule()
    if (actions[0])
      rule.action = actions[0].code
    if (resources[0])
      rule.resourcePattern = resources[0].id
    commit({ ...document, rules: [...document.rules, rule] })
  }

  $effect(() => {
    const incoming = serializePermissionDocument(value)
    untrack(() => {
      if (incoming === serializePermissionDocument(document))
        return
      document = hydrate(value)
      source = incoming
      errors = []
    })
  })
</script>

{#snippet resourceContent(option: SelectorOption)}
  {@const resource = resources.find(item => item.id === option.id)}
  <span class='flex min-w-0 items-center gap-2'>{#if resource?.id === 'issue:*'}<Badge class='shrink-0 bg-foreground text-background'>{m.permission_all_badge()}</Badge>{:else if resource?.badge}<Badge variant='outline' class='shrink-0'>{resource.badge}</Badge>{/if}<span class='truncate'>{option.label}</span>{#if resource?.resourceKey}<span class='shrink-0 text-xs text-muted-foreground'>{resource.resourceKey}</span>{/if}</span>
{/snippet}

{#snippet actionTriggerContent(option: SelectorOption)}
  <span class='truncate' data-slot='permission-action-content'>{option.label}</span>
{/snippet}

{#snippet actionOptionContent(option: SelectorOption)}
  <span class='min-w-0 text-left' data-slot='permission-action-content'><span class='block truncate'>{option.label}</span>{#if option.description}<span class='block truncate text-xs text-muted-foreground'>{option.description}</span>{/if}</span>
{/snippet}

<Tooltip.Provider>
  <div class='space-y-5' data-slot='permission-editor'>
    <Card>
      <CardHeader><CardTitle>{m.permission_policy_details()}</CardTitle><CardDescription>{m.permission_policy_details_description()}</CardDescription><CardAction><Button type='button' variant='outline' size='sm' onclick={() => sourceOpen = true}><FileJsonIcon class='size-3.5' />{m.permission_advanced_json()}</Button></CardAction></CardHeader>
      <CardContent><Field.Group class='grid gap-4 md:grid-cols-2'>
        <Field.Field><Field.Label for='policy-code'>{m.permission_code()}</Field.Label><Input id='policy-code' value={document.code} disabled={!editable || !codeEditable} oninput={event => commit({ ...document, code: event.currentTarget.value })} /></Field.Field>
        <Field.Field><Field.Label for='policy-name'>{m.permission_name()}</Field.Label><Input id='policy-name' value={document.name} disabled={!editable} oninput={event => commit({ ...document, name: event.currentTarget.value })} /></Field.Field>
        <Field.Field class='md:col-span-2'><Field.Label for='policy-description'>{m.permission_description()}</Field.Label><Textarea id='policy-description' value={document.description ?? ''} disabled={!editable} oninput={event => commit({ ...document, description: event.currentTarget.value || null })} /></Field.Field>
      </Field.Group></CardContent>
    </Card>

    <section class='space-y-3' aria-labelledby='permission-rules-title'>
      <div class='flex flex-wrap items-end justify-between gap-3'><div><h2 id='permission-rules-title' class='text-lg font-semibold'>{m.permission_rules()}</h2><p class='text-sm text-muted-foreground'>{m.permission_rules_description()}</p></div>{#if !editable}<Badge variant='secondary'>{m.permission_read_only()}</Badge>{:else}<Button type='button' onclick={addRule}>{m.permission_add_rule()}</Button>{/if}</div>
      {#each document.rules as rule, index (rule.id ?? index)}
        <Card
          class={rule.effect === 'DENY'
            ? 'overflow-visible border-destructive/50'
            : 'overflow-visible'}
        >
          <CardHeader class='flex-row items-center justify-between gap-3'><div class='min-w-0'><CardTitle class='text-base'>{m.permission_rule_number({ number: index + 1 })}</CardTitle><CardDescription>{rule.effect === 'DENY' ? m.permission_deny_description() : m.permission_allow_description()}</CardDescription></div>{#if editable}<CardAction><ButtonGroup.Root><Tooltip.Root><Tooltip.Trigger>{#snippet child({ props })}<Button {...props} type='button' variant='outline' size='icon-xs' aria-label={m.permission_duplicate()} onclick={() => commit({ ...document, rules: [...document.rules.slice(0, index + 1), { ...JSON.parse(JSON.stringify(rule)) as PermissionPolicyRuleDocument, id: undefined, condition: rule.condition ? clonePermissionCondition(rule.condition) : rule.condition }, ...document.rules.slice(index + 1)] })}><CopyIcon class='size-3.5' /></Button>{/snippet}</Tooltip.Trigger><Tooltip.Content>{m.permission_duplicate()}</Tooltip.Content></Tooltip.Root><Tooltip.Root><Tooltip.Trigger>{#snippet child({ props })}<Button {...props} type='button' variant='outline' size='icon-xs' aria-label={m.permission_delete()} onclick={() => commit({ ...document, rules: document.rules.filter((_, ruleIndex) => ruleIndex !== index) })}><TrashIcon class='size-3.5' /></Button>{/snippet}</Tooltip.Trigger><Tooltip.Content>{m.permission_delete()}</Tooltip.Content></Tooltip.Root></ButtonGroup.Root></CardAction>{/if}</CardHeader>
          <CardContent class='space-y-5'>
            <Field.Group class='grid gap-3 md:grid-cols-3'>
              <Field.Field><Field.Label>{m.permission_action()}</Field.Label><SearchableSelect value={rule.action} options={actionOptions} disabled={!editable} required clearable={false} placeholder={m.permission_choose_action()} triggerContent={actionTriggerContent} optionContent={actionOptionContent} onValueChange={code => selectAction(index, rule, code)} /></Field.Field>
              <Field.Field><Field.Label>{m.permission_resource()}</Field.Label><SearchableSelect value={rule.resourcePattern} options={resources} disabled={!editable} required clearable={false} placeholder={m.permission_choose_resource()} triggerContent={resourceContent} optionContent={resourceContent} onValueChange={resource => resource && updateRule(index, { ...rule, resourcePattern: resource })} /></Field.Field>
              <Field.Field><Field.Label>{m.permission_effect()}</Field.Label><PermissionEffectToggle value={rule.effect} disabled={!editable} onChange={effect => updateRule(index, { ...rule, effect })} /></Field.Field>
            </Field.Group>
            {#if rule.condition}
              <div class='space-y-2'><div class='flex items-center justify-between'><div><h3 class='text-sm font-medium'>{m.permission_condition()}</h3><p class='text-xs text-muted-foreground'>{m.permission_condition_description()}</p></div>{#if editable}<Button type='button' variant='ghost' size='sm' onclick={() => updateRule(index, { ...rule, condition: null })}>{m.permission_remove_condition()}</Button>{/if}</div><ConditionNodeEditor node={rule.condition} {fields} {editable} onChange={condition => updateRuleCondition(index, condition)} /></div>
            {:else if editable}
              <ButtonGroup.Root>
                <Button type='button' variant='outline' size='sm' disabled={fields.length === 0} onclick={() => updateRule(index, { ...rule, condition: withPermissionUiIds(emptyPermissionPredicate()) })}>{m.permission_add_condition()}</Button>
                <Button type='button' variant='outline' size='sm' disabled={fields.length === 0} onclick={() => updateRule(index, { ...rule, condition: withPermissionUiIds({ op: 'and', args: [emptyPermissionPredicate()] }) })}>{m.permission_add_group()}</Button>
              </ButtonGroup.Root>
            {/if}
          </CardContent>
        </Card>
      {:else}<div class='rounded-lg border border-dashed p-8 text-center'><p class='font-medium'>{m.permission_no_rules()}</p><p class='mt-1 text-sm text-muted-foreground'>{m.permission_no_rules_description()}</p></div>
      {/each}
    </section>
  </div>
</Tooltip.Provider>

<Dialog.Root bind:open={sourceOpen}>
  <Dialog.Content class='max-h-[90vh] overflow-y-auto sm:max-w-4xl'><Dialog.Header><Dialog.Title>{m.permission_advanced_json_title()}</Dialog.Title><Dialog.Description>{m.permission_advanced_json_description()}</Dialog.Description></Dialog.Header><JsonEditor value={source} schema={permissionPolicyJsonSchema} {modelId} readOnly={!editable} ariaLabel='Permission policy JSON' onChange={updateSource} onValidityChange={updateJsonValidity} />{#if errors.length > 0}<div class='rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive' role='alert'><ul class='list-disc pl-5'>{#each errors as error (error)}<li>{error}</li>{/each}</ul></div>{/if}<Dialog.Footer><Button type='button' variant='outline' onclick={() => sourceOpen = false}>{m.close()}</Button></Dialog.Footer></Dialog.Content>
</Dialog.Root>
