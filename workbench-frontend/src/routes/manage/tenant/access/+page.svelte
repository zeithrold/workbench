<script lang='ts'>
  /* eslint-disable style/max-statements-per-line */
  import type { PermissionBinding, PermissionGroup, PermissionPolicy } from '$lib/entities/management/model.js'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { management } from '$lib/entities/management/management.svelte.js'
  import { Badge, Button, Card, CardContent, CardHeader, CardTitle, Input, Label, LoadingState, PageHeader } from '$lib/shared/ui'

  let groups = $state<PermissionGroup[]>([])
  let policies = $state<PermissionPolicy[]>([])
  let bindings = $state<PermissionBinding[]>([])
  let groupCode = $state(''); let groupName = $state('')
  let policyCode = $state(''); let policyName = $state('')
  let bindingGroupId = $state(''); let bindingPolicyId = $state('')
  let loading = $state(true); let error = $state<Error | null>(null)
  const canGroups = $derived(management.has('TENANT', 'permission.group.manage'))
  const canPolicies = $derived(management.has('TENANT', 'permission.policy.manage'))
  const canBindings = $derived(management.has('TENANT', 'permission.assignment.manage'))

  async function load() {
    loading = true; error = null
    try { [groups, policies, bindings] = await Promise.all([managementGateway.groups(), managementGateway.policies(), managementGateway.bindings()]) }
    catch (reason) { error = reason as Error }
    finally { loading = false }
  }
  async function perform(operation: () => Promise<unknown>) {
    try { await operation(); await load() }
    catch (reason) { error = reason as Error }
  }
  $effect(() => { void load() })
</script>

<div class='space-y-8'>
  <PageHeader title='Access control' description='Inspect built-in access objects and manage custom groups, policies, and bindings.' />
  {#if !canGroups && !canPolicies && !canBindings}<p class='rounded-md border bg-muted/40 p-3 text-sm text-muted-foreground'>Read-only mode. Built-in and custom access configuration is visible, but write actions require an explicit management grant.</p>{/if}
  {#if error}<p class='text-sm text-destructive'>{error.message}</p>{/if}
  {#if loading}<LoadingState label='Loading access control' />{:else}
    <div class='grid gap-6 xl:grid-cols-3'>
      <Card><CardHeader><CardTitle>Groups</CardTitle></CardHeader><CardContent class='space-y-3'>
        {#if canGroups}<form class='grid gap-2 rounded-md border p-3' onsubmit={async (event) => { event.preventDefault(); await perform(() => managementGateway.createGroup({ code: groupCode, name: groupName })); groupCode = ''; groupName = '' }}><Label for='group-code'>New custom group</Label><Input id='group-code' placeholder='Code' bind:value={groupCode} required /><Input aria-label='Group name' placeholder='Name' bind:value={groupName} required /><Button size='sm'>Create group</Button></form>{/if}
        {#each groups as group (group.id)}<div class='rounded-md border p-3'><div class='flex items-start justify-between gap-2'><div><p class='font-medium'>{group.name}</p><p class='text-xs text-muted-foreground'>{group.code}</p></div>{#if group.builtin}<Badge variant='secondary'>Built-in</Badge>{:else if canGroups}<Button variant='ghost' size='sm' onclick={() => perform(() => managementGateway.deleteGroup(group.id))}>Delete</Button>{/if}</div><p class='mt-2 text-sm'>{group.description ?? 'No description'}</p></div>{/each}
      </CardContent></Card>

      <Card><CardHeader><CardTitle>Policies</CardTitle></CardHeader><CardContent class='space-y-3'>
        {#if canPolicies}<form class='grid gap-2 rounded-md border p-3' onsubmit={async (event) => { event.preventDefault(); await perform(() => managementGateway.createPolicy({ code: policyCode, name: policyName })); policyCode = ''; policyName = '' }}><Label for='policy-code'>New custom policy</Label><Input id='policy-code' placeholder='Code' bind:value={policyCode} required /><Input aria-label='Policy name' placeholder='Name' bind:value={policyName} required /><Button size='sm'>Create policy</Button></form>{/if}
        {#each policies as policy (policy.id)}<div class='rounded-md border p-3'><div class='flex items-start justify-between gap-2'><div><p class='font-medium'>{policy.name}</p><p class='text-xs text-muted-foreground'>{policy.code} · {policy.rules.length} rules</p></div>{#if policy.builtin}<Badge variant='secondary'>Built-in</Badge>{:else if canPolicies}<Button variant='ghost' size='sm' onclick={() => perform(() => managementGateway.deletePolicy(policy.id))}>Delete</Button>{/if}</div></div>{/each}
      </CardContent></Card>

      <Card><CardHeader><CardTitle>Bindings</CardTitle></CardHeader><CardContent class='space-y-3'>
        {#if canBindings}<form class='grid gap-2 rounded-md border p-3' onsubmit={async (event) => { event.preventDefault(); await perform(() => managementGateway.createBinding({ principalType: 'GROUP', groupId: bindingGroupId, policyId: bindingPolicyId })); bindingGroupId = ''; bindingPolicyId = '' }}><Label for='binding-group'>Bind group to policy</Label><select id='binding-group' class='h-9 rounded-md border bg-background px-3 text-sm' bind:value={bindingGroupId} required><option value=''>Select group…</option>{#each groups as group (group.id)}<option value={group.id}>{group.name}</option>{/each}</select><select aria-label='Policy' class='h-9 rounded-md border bg-background px-3 text-sm' bind:value={bindingPolicyId} required><option value=''>Select policy…</option>{#each policies as policy (policy.id)}<option value={policy.id}>{policy.name}</option>{/each}</select><Button size='sm'>Create binding</Button></form>{/if}
        {#each bindings as binding (binding.id)}<div class='flex items-start justify-between gap-2 rounded-md border p-3'><div><p class='font-medium'>{binding.policy.name}</p><p class='text-xs text-muted-foreground'>{binding.principalType} · {binding.user?.displayName ?? binding.group?.name ?? 'Tenant members'}</p></div>{#if canBindings}<Button variant='ghost' size='sm' onclick={() => perform(() => managementGateway.deleteBinding(binding.id))}>Expire</Button>{/if}</div>{/each}
      </CardContent></Card>
    </div>
  {/if}
</div>
