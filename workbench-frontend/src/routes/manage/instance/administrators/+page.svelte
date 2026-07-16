<script lang='ts'>
  /* eslint-disable style/max-statements-per-line */
  import type { AccessGrant, AdminUser } from '$lib/entities/management/model.js'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { management } from '$lib/entities/management/management.svelte.js'
  import { m } from '$lib/paraglide/messages.js'
  import { Badge, Button, Card, CardContent, CardHeader, CardTitle, Input, Label, LoadingState, PageHeader } from '$lib/shared/ui'

  let admins = $state<AdminUser[]>([]); let grants = $state<AccessGrant[]>([]); let userId = $state(''); let loading = $state(true); let error = $state<Error | null>(null)
  const canManage = $derived(management.has('INSTANCE', 'instance.admin.manage'))
  async function load() {
    loading = true; try { [admins, grants] = await Promise.all([managementGateway.instanceAdmins(), managementGateway.instanceGrants()]) }
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
  <PageHeader title={m.management_instance_administrators()} description={m.management_instance_administrators_description()} />
  {#if canManage}<Card><CardHeader><CardTitle>{m.management_add_administrator()}</CardTitle></CardHeader><CardContent><form class='flex gap-3' onsubmit={async (event) => { event.preventDefault(); await perform(() => managementGateway.grantInstanceAdmin(userId)); userId = '' }}><div class='flex-1'><Label for='user-id'>{m.management_user_public_id()}</Label><Input id='user-id' bind:value={userId} placeholder='usr_…' required /></div><Button class='self-end'>{m.management_grant()}</Button></form></CardContent></Card>{:else}<p class='rounded-md border bg-muted/40 p-3 text-sm text-muted-foreground'>{m.management_instance_admin_read_only()}</p>{/if}
  {#if error}<p class='text-sm text-destructive'>{error.message}</p>{/if}
  {#if loading}<LoadingState label={m.management_loading_administrators()} />{:else}<div class='space-y-4'>{#each admins as admin (admin.id)}<Card><CardHeader><div class='flex items-start justify-between gap-3'><div><CardTitle>{admin.userId}</CardTitle><p class='text-xs text-muted-foreground'>{admin.id} · {admin.status}</p></div>{#if canManage}<Button variant='outline' size='sm' onclick={() => perform(() => managementGateway.revokeInstanceAdmin(admin.id))}>{m.management_revoke()}</Button>{/if}</div></CardHeader><CardContent><p class='mb-2 text-xs font-medium uppercase text-muted-foreground'>{m.management_explicit_grants()}</p><div class='flex flex-wrap gap-2'>{#each grants.filter(grant => grant.userId === admin.userId) as grant (grant.id)}<Badge variant='secondary'>{grant.effect} {grant.action} · {grant.resourcePattern}</Badge>{:else}<span class='text-sm text-destructive'>{m.management_no_active_grants()}</span>{/each}</div></CardContent></Card>{/each}</div>{/if}
</div>
