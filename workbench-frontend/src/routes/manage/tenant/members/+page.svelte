<script lang='ts'>
  /* eslint-disable style/max-statements-per-line */
  import type { ManagedInvitation, TenantMember } from '$lib/entities/management/model.js'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { management } from '$lib/entities/management/management.svelte.js'
  import { Badge, Button, Card, CardContent, CardHeader, CardTitle, Input, Label, LoadingState, PageHeader } from '$lib/shared/ui'

  let members = $state<TenantMember[]>([]); let invitations = $state<ManagedInvitation[]>([])
  let email = $state(''); let displayName = $state(''); let loading = $state(true); let error = $state<Error | null>(null)
  const canManage = $derived(management.has('TENANT', 'tenant.member.manage'))
  async function load() {
    loading = true; try { [members, invitations] = await Promise.all([managementGateway.members(), managementGateway.invitations()]) }
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
  <PageHeader title='Members' description='Inspect tenant members and manage invitations and access status.' />
  {#if canManage}<Card><CardHeader><CardTitle>Invite member</CardTitle></CardHeader><CardContent><form class='grid gap-4 md:grid-cols-[1fr_1fr_auto]' onsubmit={async (event) => { event.preventDefault(); await perform(() => managementGateway.invite(email, displayName || undefined)); email = ''; displayName = '' }}><div><Label for='invite-email'>Email</Label><Input id='invite-email' type='email' bind:value={email} required /></div><div><Label for='invite-name'>Display name</Label><Input id='invite-name' bind:value={displayName} /></div><Button class='self-end'>Send invite</Button></form></CardContent></Card>{:else}<p class='rounded-md border bg-muted/40 p-3 text-sm text-muted-foreground'>Read-only mode. Member and invitation changes require an explicit tenant.member.manage grant.</p>{/if}
  {#if error}<p class='text-sm text-destructive'>{error.message}</p>{/if}
  {#if loading}<LoadingState label='Loading members' />{:else}
    <Card><CardHeader><CardTitle>Active and former members</CardTitle></CardHeader><CardContent class='overflow-x-auto'><table class='w-full text-left text-sm'><thead><tr class='border-b text-muted-foreground'><th class='py-3'>Member</th><th>Status</th><th>Role</th><th></th></tr></thead><tbody>{#each members as member (member.id)}<tr class='border-b'><td class='py-3'><p class='font-medium'>{member.user.displayName}</p><p class='text-xs text-muted-foreground'>{member.user.primaryEmail ?? member.user.id}</p></td><td><Badge variant='secondary'>{member.status}</Badge></td><td>{member.administrator ? 'Administrator' : 'Member'}</td><td class='space-x-2 text-right'>{#if canManage && member.status === 'ACTIVE'}<Button variant='outline' size='sm' onclick={() => perform(() => managementGateway.suspendMember(member.id))}>Suspend</Button>{:else if canManage && member.status === 'SUSPENDED'}<Button variant='outline' size='sm' onclick={() => perform(() => managementGateway.restoreMember(member.id))}>Restore</Button>{/if}{#if canManage && member.status !== 'REMOVED'}<Button variant='ghost' size='sm' onclick={() => perform(() => managementGateway.removeMember(member.id))}>Remove</Button>{/if}</td></tr>{/each}</tbody></table></CardContent></Card>
    <Card><CardHeader><CardTitle>Pending invitations</CardTitle></CardHeader><CardContent>{#if invitations.length === 0}<p class='text-sm text-muted-foreground'>No pending invitations.</p>{:else}{#each invitations as invitation (invitation.id)}<div class='flex items-center justify-between border-b py-3'><div><p class='font-medium'>{invitation.displayName ?? invitation.email}</p><p class='text-xs text-muted-foreground'>{invitation.email} · expires {new Date(invitation.expiresAt).toLocaleDateString()}</p></div>{#if canManage}<Button variant='outline' size='sm' onclick={() => perform(() => managementGateway.cancelInvitation(invitation.id))}>Cancel</Button>{/if}</div>{/each}{/if}</CardContent></Card>
  {/if}
</div>
