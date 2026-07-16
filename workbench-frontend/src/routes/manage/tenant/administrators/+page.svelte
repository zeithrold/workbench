<script lang='ts'>
  /* eslint-disable style/max-statements-per-line */
  import type { AdminUser, TenantMember } from '$lib/entities/management/model.js'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { management } from '$lib/entities/management/management.svelte.js'
  import { m } from '$lib/paraglide/messages.js'
  import { Button, Card, CardContent, CardHeader, CardTitle, Label, LoadingState, PageHeader } from '$lib/shared/ui'

  let admins = $state<AdminUser[]>([]); let members = $state<TenantMember[]>([]); let userId = $state(''); let loading = $state(true); let error = $state<Error | null>(null)
  const canManage = $derived(management.has('TENANT', 'permission.assignment.manage'))
  async function load() {
    loading = true; try { [admins, members] = await Promise.all([managementGateway.tenantAdmins(), managementGateway.members()]) }
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
  <PageHeader title={m.management_tenant_administrators()} description={m.management_tenant_administrators_description()} />
  {#if canManage}<Card><CardHeader><CardTitle>{m.management_add_administrator()}</CardTitle></CardHeader><CardContent><form class='flex gap-3' onsubmit={async (event) => { event.preventDefault(); await perform(() => managementGateway.grantTenantAdmin(userId)); userId = '' }}><div class='flex-1'><Label for='member'>{m.management_member()}</Label><select id='member' class='h-9 w-full rounded-md border bg-background px-3 text-sm' bind:value={userId} required><option value=''>{m.management_select_member()}</option>{#each members.filter(member => member.status === 'ACTIVE' && !member.administrator) as member (member.id)}<option value={member.user.id}>{member.user.displayName} ({member.user.primaryEmail})</option>{/each}</select></div><Button class='self-end'>{m.management_grant()}</Button></form></CardContent></Card>{:else}<p class='rounded-md border bg-muted/40 p-3 text-sm text-muted-foreground'>{m.management_tenant_admin_read_only()}</p>{/if}
  {#if error}<p class='text-sm text-destructive'>{error.message}</p>{/if}
  {#if loading}<LoadingState label={m.management_loading_administrators()} />{:else}<div class='rounded-lg border bg-background'>{#each admins as admin (admin.id)}<div class='flex items-center justify-between border-b p-4'><div><p class='font-medium'>{members.find(member => member.user.id === admin.userId)?.user.displayName ?? admin.userId}</p><p class='text-xs text-muted-foreground'>{m.management_tenant_admin_source({ id: admin.id })}</p></div>{#if canManage}<Button size='sm' variant='outline' onclick={() => perform(() => managementGateway.revokeTenantAdmin(admin.id))}>{m.management_revoke()}</Button>{/if}</div>{/each}</div>{/if}
</div>
