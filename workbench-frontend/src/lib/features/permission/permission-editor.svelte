<script lang='ts'>
  import type { PermissionPolicyDocument, PermissionPolicyRuleDocument } from './permission-document.js'
  import { Textarea } from '$lib/components/ui/textarea'
  import { Badge, Button, Card, CardContent, CardHeader, CardTitle, Input, JsonEditor, Label } from '$lib/shared/ui'
  import ConditionNodeEditor from './condition-node-editor.svelte'
  import { emptyPermissionPolicyDocument, emptyPermissionPredicate, emptyPermissionRule, parsePermissionDocument, permissionPolicyJsonSchema, serializePermissionDocument } from './permission-document.js'

  export interface PermissionActionOption { code: string, description?: string | null }

  const { value, actions = [], editable = true, codeEditable = false, modelId, onChange, onValidityChange }: {
    value: PermissionPolicyDocument
    actions?: PermissionActionOption[]
    editable?: boolean
    codeEditable?: boolean
    modelId: string
    onChange: (value: PermissionPolicyDocument) => void
    onValidityChange?: (valid: boolean) => void
  } = $props()

  let document = $state<PermissionPolicyDocument>(emptyPermissionPolicyDocument())
  let source = $state(serializePermissionDocument(emptyPermissionPolicyDocument()))
  let mode = $state<'visual' | 'source'>('visual')
  let errors = $state<string[]>([])

  function commit(next: PermissionPolicyDocument) {
    document = next
    source = serializePermissionDocument(next)
    errors = []
    onValidityChange?.(true)
    onChange(next)
  }

  function updateRule(index: number, next: PermissionPolicyRuleDocument) {
    commit({ ...document, rules: document.rules.map((rule, ruleIndex) => ruleIndex === index ? next : rule) })
  }

  function updateSource(next: string) {
    source = next
    const result = parsePermissionDocument(next)
    errors = result.errors
    onValidityChange?.(errors.length === 0)
    if (result.document) {
      document = result.document
      onChange(result.document)
    }
  }

  function selectMode(next: 'visual' | 'source') {
    if (next === 'visual' && errors.length > 0)
      return
    mode = next
    if (next === 'source')
      source = serializePermissionDocument(document)
  }

  function handleSchemaValidity(valid: boolean) {
    if (!valid && errors.length === 0) {
      errors = ['JSON Schema validation failed.']
      onValidityChange?.(false)
    }
  }

  $effect(() => {
    const incoming = serializePermissionDocument(value)
    if (incoming !== serializePermissionDocument(document)) {
      document = structuredClone(value)
      source = incoming
      errors = []
    }
  })
</script>

<div class='space-y-4' data-slot='permission-editor'>
  <div class='flex flex-wrap items-center justify-between gap-3'>
    <div class='inline-flex rounded-lg border bg-muted/30 p-1' role='tablist' aria-label='Permission editor mode'>
      <Button type='button' size='sm' variant={mode === 'visual' ? 'default' : 'ghost'} onclick={() => selectMode('visual')} disabled={errors.length > 0}>Visual</Button>
      <Button type='button' size='sm' variant={mode === 'source' ? 'default' : 'ghost'} onclick={() => selectMode('source')}>JSON</Button>
    </div>
    {#if !editable}<Badge variant='secondary'>Read only</Badge>{/if}
  </div>

  {#if mode === 'source'}
    <JsonEditor value={source} schema={permissionPolicyJsonSchema} {modelId} readOnly={!editable} onChange={updateSource} onValidityChange={handleSchemaValidity} />
    {#if errors.length > 0}<div class='rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive' role='alert'><p class='font-medium'>Fix the JSON before switching to Visual mode or saving.</p><ul class='mt-2 list-disc pl-5'>{#each errors.slice(0, 8) as error (error)}<li>{error}</li>{/each}</ul></div>{/if}
  {:else}
    <Card>
      <CardHeader><CardTitle>Policy details</CardTitle></CardHeader>
      <CardContent class='grid gap-4 md:grid-cols-2'>
        <div class='space-y-1'><Label for='policy-code'>Code</Label><Input id='policy-code' value={document.code} disabled={!editable || !codeEditable} oninput={event => commit({ ...document, code: event.currentTarget.value })} /></div>
        <div class='space-y-1'><Label for='policy-name'>Name</Label><Input id='policy-name' value={document.name} disabled={!editable} oninput={event => commit({ ...document, name: event.currentTarget.value })} /></div>
        <div class='space-y-1 md:col-span-2'><Label for='policy-description'>Description</Label><Textarea id='policy-description' value={document.description ?? ''} disabled={!editable} oninput={event => commit({ ...document, description: event.currentTarget.value || null })} /></div>
      </CardContent>
    </Card>

    <div class='space-y-3'>
      <div class='flex items-center justify-between'><div><h2 class='text-lg font-semibold'>Rules</h2><p class='text-sm text-muted-foreground'>Deny rules take precedence at runtime.</p></div>{#if editable}<Button type='button' onclick={() => commit({ ...document, rules: [...document.rules, emptyPermissionRule()] })}>Add rule</Button>{/if}</div>
      {#if document.rules.length === 0}<div class='rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground'>This policy has no rules.</div>{/if}
      {#each document.rules as rule, index (rule.id ?? index)}
        <Card>
          <CardHeader class='flex-row items-center justify-between'><CardTitle class='text-base'>Rule {index + 1}</CardTitle>{#if editable}<div class='flex gap-1'><Button type='button' variant='ghost' size='sm' onclick={() => commit({ ...document, rules: [...document.rules.slice(0, index + 1), { ...structuredClone(rule), id: undefined }, ...document.rules.slice(index + 1)] })}>Duplicate</Button><Button type='button' variant='ghost' size='sm' onclick={() => commit({ ...document, rules: document.rules.filter((_, ruleIndex) => ruleIndex !== index) })}>Delete</Button></div>{/if}</CardHeader>
          <CardContent class='space-y-4'>
            <div class='grid gap-3 md:grid-cols-3'>
              <div class='space-y-1'><Label>Action</Label><Input list='permission-actions' value={rule.action} disabled={!editable} oninput={event => updateRule(index, { ...rule, action: event.currentTarget.value })} /><datalist id='permission-actions'>{#each actions as action (action.code)}<option value={action.code}>{action.description}</option>{/each}</datalist></div>
              <div class='space-y-1'><Label>Resource</Label><Input list='permission-resources' value={rule.resourcePattern} disabled={!editable} oninput={event => updateRule(index, { ...rule, resourcePattern: event.currentTarget.value })} /><datalist id='permission-resources'><option value='*'></option><option value='tenant'></option><option value='permission'></option><option value='project:*'></option><option value='issue:*'></option><option value='view:*'></option><option value='sprint:*'></option></datalist></div>
              <div class='space-y-1'><Label>Effect</Label><select class='h-9 w-full rounded-md border bg-background px-3 text-sm' value={rule.effect} disabled={!editable} onchange={event => updateRule(index, { ...rule, effect: event.currentTarget.value as 'ALLOW' | 'DENY' })}><option value='ALLOW'>Allow</option><option value='DENY'>Deny</option></select></div>
            </div>
            {#if rule.condition}
              <div class='space-y-2'><div class='flex items-center justify-between'><Label>Condition</Label>{#if editable}<Button type='button' variant='ghost' size='sm' onclick={() => updateRule(index, { ...rule, condition: null })}>Remove condition</Button>{/if}</div><ConditionNodeEditor node={rule.condition} {editable} onChange={condition => updateRule(index, { ...rule, condition })} /></div>
            {:else if editable}<Button type='button' variant='outline' size='sm' onclick={() => updateRule(index, { ...rule, condition: emptyPermissionPredicate() })}>Add condition</Button>{/if}
          </CardContent>
        </Card>
      {/each}
    </div>
  {/if}
</div>
