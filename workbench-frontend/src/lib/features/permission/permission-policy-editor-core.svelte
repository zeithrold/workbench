<script lang='ts'>
  import type { Snippet } from 'svelte'
  import type { PermissionPolicyDocument } from './permission-document.js'
  import type { PermissionPolicyDocumentResult, PermissionPolicyEditorContext } from './permission-policy-editor-core.js'
  import * as Dialog from '$lib/components/ui/dialog'
  import * as Field from '$lib/components/ui/field'
  import { Textarea } from '$lib/components/ui/textarea'
  import { m } from '$lib/paraglide/messages.js'
  import { Button, Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle, Input, JsonEditor } from '$lib/shared/ui'
  import FileJsonIcon from '@lucide/svelte/icons/file-json'
  import { untrack } from 'svelte'
  import { serializePermissionDocument } from './permission-document.js'

  const {
    value,
    editable = true,
    codeEditable = false,
    modelId,
    detailsDescription,
    jsonTitle,
    jsonDescription,
    jsonAriaLabel,
    schema,
    validateDocument,
    parseDocument,
    hydrateDocument = structuredClone,
    cleanDocument = document => document,
    onChange,
    onValidityChange,
    rules,
  }: {
    value: PermissionPolicyDocument
    editable?: boolean
    codeEditable?: boolean
    modelId: string
    detailsDescription: string
    jsonTitle: string
    jsonDescription: string
    jsonAriaLabel: string
    schema: Record<string, unknown>
    validateDocument: (document: PermissionPolicyDocument) => string[]
    parseDocument: (source: string) => PermissionPolicyDocumentResult
    hydrateDocument?: (document: PermissionPolicyDocument) => PermissionPolicyDocument
    cleanDocument?: (document: PermissionPolicyDocument) => PermissionPolicyDocument
    onChange: (document: PermissionPolicyDocument) => void
    onValidityChange?: (valid: boolean) => void
    rules: Snippet<[PermissionPolicyEditorContext]>
  } = $props()

  const initialDocument = untrack(() => hydrateDocument(value))
  let document = $state<PermissionPolicyDocument>(initialDocument)
  let source = $state(untrack(() => serializePermissionDocument(cleanDocument(initialDocument))))
  let sourceOpen = $state(false)
  let errors = $state<string[]>(untrack(() => validateDocument(cleanDocument(initialDocument))))

  function setErrors(next: string[]) {
    errors = next
    onValidityChange?.(next.length === 0)
  }

  function commit(next: PermissionPolicyDocument) {
    document = next
    const clean = cleanDocument(next)
    source = serializePermissionDocument(clean)
    setErrors(validateDocument(clean))
    onChange(clean)
  }

  function updateSource(next: string) {
    source = next
    const result = parseDocument(next)
    const nextErrors = [...result.errors]
    if (result.document && !codeEditable && result.document.code !== value.code)
      nextErrors.unshift('Policy code cannot be changed after creation.')
    setErrors(nextErrors)
    if (result.document && nextErrors.length === 0) {
      document = hydrateDocument(result.document)
      onChange(cleanDocument(result.document))
    }
  }

  function updateJsonValidity(valid: boolean) {
    if (!valid && errors.length === 0)
      setErrors(['JSON Schema validation failed.'])
  }

  $effect(() => {
    const incoming = serializePermissionDocument(value)
    untrack(() => {
      if (incoming === serializePermissionDocument(cleanDocument(document)))
        return
      document = hydrateDocument(value)
      source = incoming
      setErrors(validateDocument(value))
    })
  })
</script>

<div class='space-y-5' data-slot='permission-policy-editor-core'>
  <Card>
    <CardHeader>
      <CardTitle>{m.permission_policy_details()}</CardTitle>
      <CardDescription>{detailsDescription}</CardDescription>
      <CardAction><Button type='button' variant='outline' size='sm' onclick={() => sourceOpen = true}><FileJsonIcon class='size-3.5' />{m.permission_advanced_json()}</Button></CardAction>
    </CardHeader>
    <CardContent><Field.Group class='grid gap-4 md:grid-cols-2'>
      <Field.Field><Field.Label for={`${modelId}-code`}>{m.permission_code()}</Field.Label><Input id={`${modelId}-code`} value={document.code} disabled={!editable || !codeEditable} oninput={event => commit({ ...document, code: event.currentTarget.value })} /></Field.Field>
      <Field.Field><Field.Label for={`${modelId}-name`}>{m.permission_name()}</Field.Label><Input id={`${modelId}-name`} value={document.name} disabled={!editable} oninput={event => commit({ ...document, name: event.currentTarget.value })} /></Field.Field>
      <Field.Field class='md:col-span-2'><Field.Label for={`${modelId}-description`}>{m.permission_description()}</Field.Label><Textarea id={`${modelId}-description`} value={document.description ?? ''} disabled={!editable} oninput={event => commit({ ...document, description: event.currentTarget.value || null })} /></Field.Field>
    </Field.Group></CardContent>
  </Card>

  {@render rules({ document, errors, commit })}

  {#if errors.length > 0}<div class='rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-red-700 dark:text-red-400' role='alert'><p class='font-medium'>{m.permission_resolve_errors()}</p><ul class='mt-2 list-disc pl-5'>{#each errors as error (error)}<li>{error}</li>{/each}</ul></div>{/if}
</div>

<Dialog.Root bind:open={sourceOpen}>
  <Dialog.Content class='max-h-[90vh] overflow-y-auto sm:max-w-4xl'>
    <Dialog.Header><Dialog.Title>{jsonTitle}</Dialog.Title><Dialog.Description>{jsonDescription}</Dialog.Description></Dialog.Header>
    <JsonEditor value={source} {schema} {modelId} readOnly={!editable} ariaLabel={jsonAriaLabel} onChange={updateSource} onValidityChange={updateJsonValidity} />
    {#if errors.length > 0}<div class='rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-red-700 dark:text-red-400' role='alert'><ul class='list-disc pl-5'>{#each errors as error (error)}<li>{error}</li>{/each}</ul></div>{/if}
    <Dialog.Footer><Button type='button' variant='outline' onclick={() => sourceOpen = false}>{m.close()}</Button></Dialog.Footer>
  </Dialog.Content>
</Dialog.Root>
