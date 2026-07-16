<script lang='ts'>
  import type { PermissionBinding, PermissionGroup, PermissionPolicy, ProjectSummary, TenantMember } from '$lib/entities/management/model.js'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { management } from '$lib/entities/management/management.svelte.js'
  import { Badge, Button, Card, CardContent, Label, LoadingState } from '$lib/shared/ui'

  let bindings = $state<PermissionBinding[]>([])
  let groups = $state<PermissionGroup[]>([])
  let policies = $state<PermissionPolicy[]>([])
  let members = $state<TenantMember[]>([])
  let projects = $state<ProjectSummary[]>([])
  let principalType = $state<'USER' | 'GROUP' | 'TENANT_MEMBER'>('GROUP')
  let principalId = $state('')
  let policyId = $state('')
  let projectId = $state('')
  let validTo = $state('')
  let loading = $state(true)
  let error = $state<Error | null>(null)
  const canEdit = $derived(management.has('TENANT', 'permission.assignment.manage'))

  async function load() {
    loading = true
    try {
      [bindings, groups, policies, members, projects] = await Promise.all([
        managementGateway.bindings(),
        managementGateway.groups(),
        managementGateway.policies(),
        managementGateway.members(),
        managementGateway.projects(),
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
        projectId: projectId || undefined,
        validTo: validTo ? new Date(validTo).toISOString() : undefined,
      })
      principalId = ''
      policyId = ''
      projectId = ''
      validTo = ''
    })
  }

  $effect(() => {
    void load()
  })
</script>

<div class='space-y-4'>
  <div><h2 class='text-xl font-semibold'>Bindings</h2><p class='text-sm text-muted-foreground'>Attach policies to users, groups, or all tenant members.</p></div>
  {#if error}<p class='text-sm text-destructive'>{error.message}</p>{/if}
  {#if loading}<LoadingState label='Loading permission bindings' />{:else}
    {#if canEdit}<Card><CardContent class='grid gap-4 p-5 md:grid-cols-2 xl:grid-cols-5'><div class='space-y-1'><Label>Principal type</Label><select class='h-9 w-full rounded-md border bg-background px-3 text-sm' bind:value={principalType} onchange={() => principalId = ''}><option value='USER'>User</option><option value='GROUP'>Group</option><option value='TENANT_MEMBER'>All tenant members</option></select></div>
      {#if principalType !== 'TENANT_MEMBER'}<div class='space-y-1'><Label>Principal</Label><select class='h-9 w-full rounded-md border bg-background px-3 text-sm' bind:value={principalId}><option value=''>Select...</option>{#each principalType === 'GROUP' ? groups.map(group => ({ id: group.id, name: group.name })) : members.map(member => ({ id: member.user.id, name: member.user.displayName })) as option (option.id)}<option value={option.id}>{option.name}</option>{/each}</select></div>{/if}
      <div class='space-y-1'><Label>Policy</Label><select class='h-9 w-full rounded-md border bg-background px-3 text-sm' bind:value={policyId}><option value=''>Select...</option>{#each policies as policy (policy.id)}<option value={policy.id}>{policy.name}</option>{/each}</select></div>
      <div class='space-y-1'><Label>Scope</Label><select class='h-9 w-full rounded-md border bg-background px-3 text-sm' bind:value={projectId}><option value=''>Entire tenant</option>{#each projects as project (project.id)}<option value={project.id}>{project.identifier} · {project.name}</option>{/each}</select></div>
      <div class='space-y-1'><Label for='binding-valid-to'>Expires (optional)</Label><input id='binding-valid-to' class='h-9 w-full rounded-md border bg-background px-3 text-sm' type='datetime-local' bind:value={validTo} /></div>
      <div class='md:col-span-2 xl:col-span-5'><Button disabled={!policyId || (principalType !== 'TENANT_MEMBER' && !principalId)} onclick={createBinding}>Create binding</Button></div>
    </CardContent></Card>{/if}
    <div class='space-y-2'>{#each bindings as binding (binding.id)}<div class='flex flex-wrap items-center justify-between gap-3 rounded-lg border bg-background p-4'><div><div class='flex items-center gap-2'><p class='font-medium'>{binding.policy.name}</p>{#if binding.validTo && new Date(binding.validTo) <= new Date()}<Badge variant='secondary'>Expired</Badge>{/if}</div><p class='text-sm text-muted-foreground'>{binding.user?.displayName ?? binding.group?.name ?? 'All tenant members'} · {binding.project ? `${binding.project.identifier} · ${binding.project.name}` : 'Entire tenant'}{binding.validTo ? ` · until ${new Date(binding.validTo).toLocaleString()}` : ''}</p></div>{#if canEdit}<Button variant='ghost' size='sm' onclick={() => perform(() => managementGateway.deleteBinding(binding.id))}>Expire</Button>{/if}</div>{:else}<p class='rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground'>No permission bindings.</p>{/each}</div>
  {/if}
</div>
