<script lang='ts'>
  /* eslint-disable style/max-statements-per-line */
  import type { ManagedInvitation, TenantMember } from '$lib/entities/management/model.js'
  import { isApiProblemStatus } from '$lib/api/problem.js'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import InvitationForm from '$lib/features/invitation/invitation-form.svelte'
  import { m } from '$lib/paraglide/messages.js'
  import { AccessDeniedState, Alert, Badge, Button, Card, CardContent, CardHeader, CardTitle, LoadingState, PageHeader } from '$lib/shared/ui'

  let members = $state<TenantMember[]>([]); let invitations = $state<ManagedInvitation[]>([])
  let loading = $state(true); let loaded = $state(false); let error = $state<Error | null>(null)
  async function load() {
    loading = true; try { [members, invitations] = await Promise.all([managementGateway.members(), managementGateway.invitations()]); loaded = true }
    catch (reason) { error = reason as Error }
    finally { loading = false }
  }
  async function perform(operation: () => Promise<unknown>) {
    try { error = null; await operation(); await load() }
    catch (reason) { error = reason as Error }
  }
  $effect(() => { void load() })
</script>

<div class='space-y-8'>
  <PageHeader title={m.management_members()} description={m.management_members_description()} />
  {#if !loaded && error && isApiProblemStatus(error, 403)}<AccessDeniedState description={error.message} />
  {:else}
    <Card><CardHeader><CardTitle>{m.management_invite_member()}</CardTitle></CardHeader><CardContent><InvitationForm onInvited={() => void load()} /></CardContent></Card>
    {#if error}<Alert variant='destructive'>{error.message}</Alert>{/if}
    {#if loading}<LoadingState label={m.management_loading_members()} />{:else}
      <Card><CardHeader><CardTitle>{m.management_active_and_former_members()}</CardTitle></CardHeader><CardContent class='overflow-x-auto'><table class='w-full text-left text-sm'><thead><tr class='border-b text-muted-foreground'><th class='py-3'>{m.management_member()}</th><th>{m.management_status()}</th><th>{m.management_role()}</th><th></th></tr></thead><tbody>{#each members as member (member.id)}<tr class='border-b'><td class='py-3'><p class='font-medium'>{member.user.displayName}</p><p class='text-xs text-muted-foreground'>{member.user.primaryEmail ?? member.user.id}</p></td><td><Badge variant='secondary'>{member.status}</Badge></td><td>{member.administrator ? m.administrator() : m.management_member()}</td><td class='space-x-2 text-right'>{#if member.status === 'ACTIVE'}<Button variant='outline' size='sm' onclick={() => perform(() => managementGateway.suspendMember(member.id))}>{m.management_suspend()}</Button>{:else if member.status === 'SUSPENDED'}<Button variant='outline' size='sm' onclick={() => perform(() => managementGateway.restoreMember(member.id))}>{m.management_restore()}</Button>{/if}{#if member.status !== 'REMOVED'}<Button variant='ghost' size='sm' onclick={() => perform(() => managementGateway.removeMember(member.id))}>{m.remove()}</Button>{/if}</td></tr>{/each}</tbody></table></CardContent></Card>
      <Card><CardHeader><CardTitle>{m.management_pending_invitations()}</CardTitle></CardHeader><CardContent>{#if invitations.length === 0}<p class='text-sm text-muted-foreground'>{m.management_no_pending_invitations()}</p>{:else}{#each invitations as invitation (invitation.id)}<div class='flex items-center justify-between border-b py-3'><div><p class='font-medium'>{invitation.displayName ?? invitation.email}</p><p class='text-xs text-muted-foreground'>{m.management_invitation_expiry({ email: invitation.email, date: new Date(invitation.expiresAt).toLocaleDateString() })}</p></div><Button variant='outline' size='sm' onclick={() => perform(() => managementGateway.cancelInvitation(invitation.id))}>{m.management_cancel()}</Button></div>{/each}{/if}</CardContent></Card>
    {/if}
  {/if}
</div>
