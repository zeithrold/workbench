<script lang='ts'>
  import type { GroupMember, PermissionGroup, TenantMember } from '$lib/entities/management/model.js'
  import { isApiProblemStatus } from '$lib/api/problem.js'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { m } from '$lib/paraglide/messages.js'
  import { AccessDeniedState, Alert, Badge, Button, Card, CardContent, Input, Label, LoadingState } from '$lib/shared/ui'

  let groups = $state<PermissionGroup[]>([])
  let members = $state<TenantMember[]>([])
  let groupMembers = $state<GroupMember[]>([])
  let selectedId = $state<string | null>(null)
  let code = $state('')
  let name = $state('')
  let userId = $state('')
  let loading = $state(true)
  let loaded = $state(false)
  let error = $state<Error | null>(null)
  const selected = $derived(groups.find(group => group.id === selectedId) ?? null)

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
      loaded = true
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
    try {
      groupMembers = await managementGateway.groupMembers(id)
    }
    catch (reason) { error = reason as Error }
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
  <div><h2 class='text-xl font-semibold'>{m.management_groups()}</h2><p class='text-sm text-muted-foreground'>{m.management_groups_description()}</p></div>
  {#if !loaded && error && isApiProblemStatus(error, 403)}<AccessDeniedState description={error.message} />
  {:else}
    {#if error}<Alert variant='destructive'>{error.message}</Alert>{/if}
    {#if loading}<LoadingState label={m.management_loading_permission_groups()} />{:else}
      <div class='grid gap-5 lg:grid-cols-[22rem_1fr]'>
        <div class='space-y-3'>
          <Card><CardContent class='grid gap-2 p-4'><Label for='group-code'>{m.management_new_custom_group()}</Label><Input id='group-code' placeholder={m.management_code()} bind:value={code} /><Input placeholder={m.management_name()} aria-label={m.management_group_name()} bind:value={name} /><Button disabled={!code || !name} onclick={createGroup}>{m.management_create_group()}</Button></CardContent></Card>
          {#each groups as group (group.id)}<button class:border-foreground={selectedId === group.id} class='flex w-full items-start justify-between rounded-lg border bg-background p-4 text-left' onclick={() => select(group.id)}><span><span class='font-medium'>{group.name}</span><span class='block text-xs text-muted-foreground'>{group.code}</span></span>{#if group.builtin}<Badge variant='secondary'>{m.management_built_in()}</Badge>{/if}</button>{/each}
        </div>
        {#if selected}<Card><CardContent class='space-y-5 p-5'><div class='flex items-start justify-between'><div><h3 class='text-lg font-semibold'>{selected.name}</h3><p class='text-sm text-muted-foreground'>{selected.description ?? m.management_no_description()}</p></div>{#if !selected.builtin}<Button variant='ghost' size='sm' onclick={deleteSelectedGroup}>{m.management_delete_group()}</Button>{/if}</div>
          {#if !selected.builtin}<div class='flex gap-2'><select class='h-9 min-w-0 flex-1 rounded-md border bg-background px-3 text-sm' bind:value={userId}><option value=''>{m.management_select_tenant_member()}</option>{#each members.filter(member => !groupMembers.some(item => item.user.id === member.user.id)) as member (member.user.id)}<option value={member.user.id}>{member.user.displayName}</option>{/each}</select><Button disabled={!userId} onclick={addMember}>{m.management_add_member()}</Button></div>{/if}
          <div class='divide-y rounded-md border'>{#each groupMembers as member (member.id)}<div class='flex items-center justify-between p-3'><div><p class='text-sm font-medium'>{member.user.displayName}</p><p class='text-xs text-muted-foreground'>{member.user.primaryEmail}</p></div>{#if !selected.builtin}<Button variant='ghost' size='sm' onclick={() => perform(() => managementGateway.removeGroupMember(selected.id, member.user.id))}>{m.remove()}</Button>{/if}</div>{:else}<p class='p-5 text-center text-sm text-muted-foreground'>{m.management_no_group_members()}</p>{/each}</div>
        </CardContent></Card>{/if}
      </div>
    {/if}
  {/if}
</div>
