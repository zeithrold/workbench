<script lang='ts'>
  /* eslint-disable style/max-statements-per-line */
  import type { AccessGrant, AdminUser } from '$lib/entities/management/model.js'
  import { isApiProblemStatus } from '$lib/api/problem.js'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { m } from '$lib/paraglide/messages.js'
  import { AccessDeniedState, Alert, Badge, Button, Card, CardContent, CardHeader, CardTitle, Input, Label, LoadingState, PageHeader } from '$lib/shared/ui'

  let admins = $state<AdminUser[]>([]); let grants = $state<AccessGrant[]>([]); let userId = $state(''); let loading = $state(true); let loaded = $state(false); let error = $state<Error | null>(null)
  async function load() {
    loading = true; try { [admins, grants] = await Promise.all([managementGateway.instanceAdmins(), managementGateway.instanceGrants()]); loaded = true }
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
  <PageHeader title={m.management_instance_administrators()} description={m.management_instance_administrators_description()} />
  {#if !loaded && error && isApiProblemStatus(error, 403)}<AccessDeniedState description={error.message} />
  {:else}
    <Card><CardHeader><CardTitle>{m.management_add_administrator()}</CardTitle></CardHeader><CardContent><form class='flex gap-3' onsubmit={async (event) => {
      event.preventDefault(); if (await perform(() => managementGateway.grantInstanceAdmin(userId)))
        userId = ''
    }}><div class='flex-1'><Label for='user-id'>{m.management_user_public_id()}</Label><Input id='user-id' bind:value={userId} placeholder='usr_…' required /></div><Button class='self-end'>{m.management_grant()}</Button></form></CardContent></Card>
    {#if error}<Alert variant='destructive'>{error.message}</Alert>{/if}
    {#if loading}<LoadingState label={m.management_loading_administrators()} />{:else}<div class='space-y-4'>{#each admins as admin (admin.id)}<Card><CardHeader><div class='flex items-start justify-between gap-3'><div><CardTitle>{admin.userId}</CardTitle><p class='text-xs text-muted-foreground'>{admin.id} · {admin.status}</p></div><Button variant='outline' size='sm' onclick={() => perform(() => managementGateway.revokeInstanceAdmin(admin.id))}>{m.management_revoke()}</Button></div></CardHeader><CardContent><p class='mb-2 text-xs font-medium uppercase text-muted-foreground'>{m.management_explicit_grants()}</p><div class='flex flex-wrap gap-2'>{#each grants.filter(grant => grant.userId === admin.userId) as grant (grant.id)}<Badge variant='secondary'>{grant.effect} {grant.action} · {grant.resourcePattern}</Badge>{:else}<span class='text-sm text-destructive'>{m.management_no_active_grants()}</span>{/each}</div></CardContent></Card>{/each}</div>{/if}
  {/if}
</div>
