<script lang='ts'>
  import type { GroupMember, PermissionGroup, TenantMember } from '$lib/entities/management/model.js'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { management } from '$lib/entities/management/management.svelte.js'
  import { Badge, Button, Card, CardContent, Input, Label, LoadingState } from '$lib/shared/ui'

  let groups = $state<PermissionGroup[]>([])
  let members = $state<TenantMember[]>([])
  let groupMembers = $state<GroupMember[]>([])
  let selectedId = $state<string | null>(null)
  let code = $state('')
  let name = $state('')
  let userId = $state('')
  let loading = $state(true)
  let error = $state<Error | null>(null)
  const selected = $derived(groups.find(group => group.id === selectedId) ?? null)
  const canEdit = $derived(management.has('TENANT', 'permission.group.manage'))

  async function load() {
    loading = true
    try {
      [groups, members] = await Promise.all([
        managementGateway.groups(),
        managementGateway.members(),
      ])
      selectedId ??= groups[0]?.id ?? null
      if (selectedId)
        groupMembers = await managementGateway.groupMembers(selectedId)
    }
    catch (reason) {
      error = reason as Error
    }
    finally {
      loading = false
    }
  }
  async function select(id: string) {
    selectedId = id
    groupMembers = await managementGateway.groupMembers(id)
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
  async function createGroup() {
    await perform(async () => {
      await managementGateway.createGroup({ code, name })
      code = ''
      name = ''
    })
  }
  async function deleteSelectedGroup() {
    if (!selected)
      return
    await perform(async () => {
      await managementGateway.deleteGroup(selected.id)
      selectedId = null
    })
  }
  async function addMember() {
    if (!selected)
      return
    await perform(async () => {
      await managementGateway.addGroupMember(selected.id, userId)
      userId = ''
    })
  }
  $effect(() => {
    void load()
  })
</script>

<div class='space-y-4'>
  <div><h2 class='text-xl font-semibold'>Groups</h2><p class='text-sm text-muted-foreground'>Organize tenant members into reusable permission principals.</p></div>
  {#if error}<p class='text-sm text-destructive'>{error.message}</p>{/if}
  {#if loading}<LoadingState label='Loading permission groups' />{:else}
    <div class='grid gap-5 lg:grid-cols-[22rem_1fr]'>
      <div class='space-y-3'>
        {#if canEdit}<Card><CardContent class='grid gap-2 p-4'><Label for='group-code'>New custom group</Label><Input id='group-code' placeholder='Code' bind:value={code} /><Input placeholder='Name' aria-label='Group name' bind:value={name} /><Button disabled={!code || !name} onclick={createGroup}>Create group</Button></CardContent></Card>{/if}
        {#each groups as group (group.id)}<button class:border-foreground={selectedId === group.id} class='flex w-full items-start justify-between rounded-lg border bg-background p-4 text-left' onclick={() => select(group.id)}><span><span class='font-medium'>{group.name}</span><span class='block text-xs text-muted-foreground'>{group.code}</span></span>{#if group.builtin}<Badge variant='secondary'>Built in</Badge>{/if}</button>{/each}
      </div>
      {#if selected}<Card><CardContent class='space-y-5 p-5'><div class='flex items-start justify-between'><div><h3 class='text-lg font-semibold'>{selected.name}</h3><p class='text-sm text-muted-foreground'>{selected.description ?? 'No description'}</p></div>{#if canEdit && !selected.builtin}<Button variant='ghost' size='sm' onclick={deleteSelectedGroup}>Delete group</Button>{/if}</div>
        {#if canEdit && !selected.builtin}<div class='flex gap-2'><select class='h-9 min-w-0 flex-1 rounded-md border bg-background px-3 text-sm' bind:value={userId}><option value=''>Select tenant member...</option>{#each members.filter(member => !groupMembers.some(item => item.user.id === member.user.id)) as member (member.user.id)}<option value={member.user.id}>{member.user.displayName}</option>{/each}</select><Button disabled={!userId} onclick={addMember}>Add member</Button></div>{/if}
        <div class='divide-y rounded-md border'>{#each groupMembers as member (member.id)}<div class='flex items-center justify-between p-3'><div><p class='text-sm font-medium'>{member.user.displayName}</p><p class='text-xs text-muted-foreground'>{member.user.primaryEmail}</p></div>{#if canEdit && !selected.builtin}<Button variant='ghost' size='sm' onclick={() => perform(() => managementGateway.removeGroupMember(selected.id, member.user.id))}>Remove</Button>{/if}</div>{:else}<p class='p-5 text-center text-sm text-muted-foreground'>No members in this group.</p>{/each}</div>
      </CardContent></Card>{/if}
    </div>
  {/if}
</div>
