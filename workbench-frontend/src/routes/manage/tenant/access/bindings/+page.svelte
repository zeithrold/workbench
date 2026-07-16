<script lang='ts'>
  import type { PermissionBinding, PermissionGroup, PermissionPolicy, TenantMember } from '$lib/entities/management/model.js'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { management } from '$lib/entities/management/management.svelte.js'
  import { Badge, Button, Card, CardContent, Input, Label, LoadingState, SearchableSelect } from '$lib/shared/ui'

  let bindings = $state<PermissionBinding[]>([])
  let groups = $state<PermissionGroup[]>([])
  let policies = $state<PermissionPolicy[]>([])
  let members = $state<TenantMember[]>([])
  let principalType = $state<'USER' | 'GROUP' | 'TENANT_MEMBER'>('GROUP')
  let principalId = $state('')
  let policyId = $state('')
  let validTo = $state('')
  let loading = $state(true)
  let error = $state<Error | null>(null)
  const canEdit = $derived(management.has('TENANT', 'permission.assignment.manage'))
  const principalTypeOptions = [
    { id: 'USER', label: 'User' },
    { id: 'GROUP', label: 'Group' },
    { id: 'TENANT_MEMBER', label: 'All tenant members' },
  ]
  const principalOptions = $derived(
    principalType === 'GROUP'
      ? groups.map(group => ({ id: group.id, label: group.name, description: group.code }))
      : members.map(member => ({ id: member.user.id, label: member.user.displayName, description: member.user.primaryEmail ?? undefined })),
  )
  const policyOptions = $derived(policies.map(policy => ({ id: policy.id, label: policy.name, description: policy.description ?? policy.code })))

  async function load() {
    loading = true
    try {
      [bindings, groups, policies, members] = await Promise.all([
        managementGateway.bindings(),
        managementGateway.groups(),
        managementGateway.policies(),
        managementGateway.members(),
      ])
    }
    catch (reason) {
      error = reason as Error
    }
    finally {
      loading = false
    }
  }
  async function perform(operation: () => Promise<unknown>) {
    try {
      error = null
      await operation()
      await load()
    }
    catch (reason) {
      error = reason as Error
    }
  }

  async function createBinding() {
    await perform(async () => {
      await managementGateway.createBinding({
        principalType,
        policyId,
        groupId: principalType === 'GROUP' ? principalId : undefined,
        userId: principalType === 'USER' ? principalId : undefined,
        validTo: validTo ? new Date(validTo).toISOString() : undefined,
      })
      principalId = ''
      policyId = ''
      validTo = ''
    })
  }

  function selectPrincipalType(value: string | null) {
    if (!value)
      return
    principalType = value as typeof principalType
    principalId = ''
  }

  $effect(() => {
    void load()
  })
</script>

<div class='space-y-4'>
  <div><h2 class='text-xl font-semibold'>Bindings</h2><p class='text-sm text-muted-foreground'>Attach policies to users, groups, or all tenant members.</p></div>
  {#if error}<p class='text-sm text-destructive'>{error.message}</p>{/if}
  {#if loading}<LoadingState label='Loading permission bindings' />{:else}
    {#if canEdit}<Card><CardContent class='grid gap-4 p-5 md:grid-cols-2 xl:grid-cols-4'><div class='space-y-1'><Label>Principal type</Label><SearchableSelect value={principalType} options={principalTypeOptions} clearable={false} onValueChange={selectPrincipalType} /></div>
      {#if principalType !== 'TENANT_MEMBER'}<div class='space-y-1'><Label>Principal</Label><SearchableSelect value={principalId} options={principalOptions} placeholder='Select a principal' clearable={false} onValueChange={value => principalId = value ?? ''} /></div>{/if}
      <div class='space-y-1'><Label>Policy</Label><SearchableSelect value={policyId} options={policyOptions} placeholder='Select a tenant policy' clearable={false} onValueChange={value => policyId = value ?? ''} /></div>
      <div class='space-y-1'><Label for='binding-valid-to'>Expires (optional)</Label><Input id='binding-valid-to' type='datetime-local' value={validTo} oninput={event => validTo = event.currentTarget.value} /></div>
      <div class='md:col-span-2 xl:col-span-4'><p class='mb-3 text-sm text-muted-foreground'>This assignment applies to the entire tenant.</p><Button disabled={!policyId || (principalType !== 'TENANT_MEMBER' && !principalId)} onclick={createBinding}>Create binding</Button></div>
    </CardContent></Card>{/if}
    <div class='space-y-2'>{#each bindings as binding (binding.id)}<div class='flex flex-wrap items-center justify-between gap-3 rounded-lg border bg-background p-4'><div><div class='flex items-center gap-2'><p class='font-medium'>{binding.policy.name}</p>{#if binding.validTo && new Date(binding.validTo) <= new Date()}<Badge variant='secondary'>Expired</Badge>{/if}</div><p class='text-sm text-muted-foreground'>{binding.user?.displayName ?? binding.group?.name ?? 'All tenant members'} · Entire tenant{binding.validTo ? ` · until ${new Date(binding.validTo).toLocaleString()}` : ''}</p></div>{#if canEdit}<Button variant='ghost' size='sm' onclick={() => perform(() => managementGateway.deleteBinding(binding.id))}>Expire</Button>{/if}</div>{:else}<p class='rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground'>No tenant permission bindings.</p>{/each}</div>
  {/if}
</div>
