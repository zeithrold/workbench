<script lang='ts'>
  /* eslint-disable style/max-statements-per-line */
  import type { AdminUser, TenantMember } from '$lib/entities/management/model.js'
  import { isApiProblemStatus } from '$lib/api/problem.js'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { m } from '$lib/paraglide/messages.js'
  import { AccessDeniedState, Alert, Button, Card, CardContent, CardHeader, CardTitle, Label, LoadingState, PageHeader } from '$lib/shared/ui'

  let admins = $state<AdminUser[]>([]); let members = $state<TenantMember[]>([]); let userId = $state(''); let loading = $state(true); let loaded = $state(false); let error = $state<Error | null>(null)
  async function load() {
    loading = true; try { [admins, members] = await Promise.all([managementGateway.tenantAdmins(), managementGateway.members()]); loaded = true }
    catch (reason) { error = reason as Error }
    finally { loading = false }
  }
  async function perform(operation: () => Promise<unknown>): Promise<boolean> {
    try { error = null; await operation(); await load(); return true }
    catch (reason) { error = reason as Error; return false }
  }
  $effect(() => { void load() })
</script>

<div class='space-y-8'>
  <PageHeader title={m.management_tenant_administrators()} description={m.management_tenant_administrators_description()} />
  {#if !loaded && error && isApiProblemStatus(error, 403)}<AccessDeniedState description={error.message} />
  {:else}
    <Card><CardHeader><CardTitle>{m.management_add_administrator()}</CardTitle></CardHeader><CardContent><form class='flex gap-3' onsubmit={async (event) => {
      event.preventDefault(); if (await perform(() => managementGateway.grantTenantAdmin(userId)))
        userId = ''
    }}><div class='flex-1'><Label for='member'>{m.management_member()}</Label><select id='member' class='h-9 w-full rounded-md border bg-background px-3 text-sm' bind:value={userId} required><option value=''>{m.management_select_member()}</option>{#each members.filter(member => member.status === 'ACTIVE' && !member.administrator) as member (member.id)}<option value={member.user.id}>{member.user.displayName} ({member.user.primaryEmail})</option>{/each}</select></div><Button class='self-end'>{m.management_grant()}</Button></form></CardContent></Card>
    {#if error}<Alert variant='destructive'>{error.message}</Alert>{/if}
    {#if loading}<LoadingState label={m.management_loading_administrators()} />{:else}<div class='rounded-lg border bg-background'>{#each admins as admin (admin.id)}<div class='flex items-center justify-between border-b p-4'><div><p class='font-medium'>{members.find(member => member.user.id === admin.userId)?.user.displayName ?? admin.userId}</p><p class='text-xs text-muted-foreground'>{m.management_tenant_admin_source({ id: admin.id })}</p></div><Button size='sm' variant='outline' onclick={() => perform(() => managementGateway.revokeTenantAdmin(admin.id))}>{m.management_revoke()}</Button></div>{/each}</div>{/if}
  {/if}
</div>
