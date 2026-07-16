<script lang='ts'>
  import type { PermissionPolicy } from '$lib/entities/management/model.js'
  import type { PermissionPolicyDocument } from '$lib/features/permission'
  import { goto } from '$app/navigation'
  import { resolve } from '$app/paths'
  import { page } from '$app/state'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { management } from '$lib/entities/management/management.svelte.js'
  import { emptyPermissionPolicyDocument, PermissionEditor } from '$lib/features/permission'
  import { Alert, Button, LoadingState } from '$lib/shared/ui'

  const policyId = $derived(page.params.id!)
  const creating = $derived(policyId === 'new')
  let policy = $state<PermissionPolicy | null>(null)
  let document = $state<PermissionPolicyDocument>(emptyPermissionPolicyDocument())
  let actions = $state<{ code: string, description?: string | null }[]>([])
  let loading = $state(true)
  let saving = $state(false)
  let valid = $state(true)
  let dirty = $state(false)
  let error = $state<Error | null>(null)
  const canManage = $derived(management.has('TENANT', 'permission.policy.manage'))
  const editable = $derived(canManage && (creating || !policy?.builtin))

  function toDocument(value: PermissionPolicy, clone = false): PermissionPolicyDocument {
    return {
      schemaVersion: 1,
      code: clone ? `${value.code}-copy` : value.code,
      name: clone ? `${value.name} copy` : value.name,
      description: value.description,
      rules: value.rules.map(rule => ({
        id: clone ? undefined : rule.id,
        action: rule.action,
        resourcePattern: rule.resourcePattern,
        effect: rule.effect,
        condition: rule.condition as PermissionPolicyDocument['rules'][number]['condition'],
      })),
    }
  }

  async function load() {
    loading = true
    error = null
    try {
      actions = await managementGateway.actions()
      if (creating) {
        const cloneId = page.url.searchParams.get('clone')
        if (cloneId) {
          const source = (await managementGateway.policies()).find(item => item.id === cloneId)
          if (source)
            document = toDocument(source, true)
        }
      }
      else {
        policy = (await managementGateway.policies()).find(item => item.id === policyId) ?? null
        if (!policy)
          throw new Error('Permission policy not found.')
        document = toDocument(policy)
      }
    }
    catch (reason) {
      error = reason as Error
    }
    finally {
      loading = false
    }
  }

  async function save() {
    if (!valid || !editable)
      return
    saving = true
    error = null
    try {
      const saved = creating
        ? await managementGateway.createPolicy(document)
        : await managementGateway.replacePolicy(policyId, { ...document, revision: policy!.revision })
      policy = saved
      document = toDocument(saved)
      dirty = false
      if (creating)
        await goto(resolve(`/manage/tenant/access/policies/${saved.id}`))
    }
    catch (reason) {
      error = reason as Error
    }
    finally {
      saving = false
    }
  }

  function updateDocument(value: PermissionPolicyDocument) {
    document = value
    dirty = true
  }

  $effect(() => {
    void load()
  })
</script>

<svelte:window onbeforeunload={(event) => {
  if (dirty)
    event.preventDefault()
}} />
{#if loading}<LoadingState label='Loading policy editor' />{:else if error && !policy && !creating}<Alert variant='destructive'>{error.message}</Alert>{:else}
  <div class='space-y-5'>
    <div class='flex flex-wrap items-center justify-between gap-3'><div><a href={resolve('/manage/tenant/access')} class='text-sm text-muted-foreground hover:text-foreground'>← Policies</a><h1 class='mt-1 text-2xl font-semibold'>{creating ? 'New policy' : policy?.name}</h1></div><div class='flex gap-2'>{#if policy?.builtin && canManage}<Button variant='outline' href={resolve(`/manage/tenant/access/policies/new?clone=${policy.id}`)}>Copy policy</Button>{/if}{#if editable}<Button disabled={!valid || saving || !document.code || !document.name} onclick={save}>{saving ? 'Saving...' : 'Save policy'}</Button>{/if}</div></div>
    {#if error}<Alert variant='destructive'>{error.message}</Alert>{/if}
    <PermissionEditor value={document} {actions} {editable} codeEditable={creating} modelId={`permission-policy-${policyId}`} onChange={updateDocument} onValidityChange={value => valid = value} />
  </div>
{/if}
