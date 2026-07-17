<script lang='ts'>
  import type { InvitationCreated } from '$lib/entities/management/model.js'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { m } from '$lib/paraglide/messages.js'
  import { Alert, Button, Input, Label } from '$lib/shared/ui'

  const { onInvited }: { onInvited?: (invitation: InvitationCreated) => void } = $props()
  let email = $state('')
  let displayName = $state('')
  let pending = $state(false)
  let error = $state('')
  let created = $state<InvitationCreated[]>([])

  async function invite(event: SubmitEvent) {
    event.preventDefault()
    pending = true
    error = ''
    try {
      const invitation = await managementGateway.invite(email.trim(), displayName.trim() || undefined)
      created = [invitation, ...created]
      email = ''
      displayName = ''
      onInvited?.(invitation)
    }
    catch (caught) {
      error = caught instanceof Error ? caught.message : m.invitation_create_failed()
    }
    finally {
      pending = false
    }
  }

  async function copy(link: string) {
    await navigator.clipboard.writeText(link)
  }
</script>

<form class='grid gap-4 md:grid-cols-[1fr_1fr_auto]' onsubmit={invite}>
  <div><Label for='invite-email'>{m.email()}</Label><Input id='invite-email' type='email' bind:value={email} required disabled={pending} /></div>
  <div><Label for='invite-name'>{m.display_name()}</Label><Input id='invite-name' bind:value={displayName} disabled={pending} /></div>
  <Button class='self-end' type='submit' disabled={pending}>{pending ? m.invitation_creating() : m.management_send_invite()}</Button>
</form>
{#if error}<Alert variant='destructive' class='mt-4'>{error}</Alert>{/if}
{#if created.length > 0}
  <div class='mt-5 space-y-3'>
    <p class='text-sm text-muted-foreground'>{m.invitation_link_once()}</p>
    {#each created as invitation (invitation.id)}
      <div class='flex flex-col gap-3 rounded-lg border bg-muted/20 p-3 sm:flex-row sm:items-center'>
        <div class='min-w-0 flex-1'><p class='font-medium'>{invitation.email}</p><p class='truncate text-xs text-muted-foreground'>{invitation.invitationLink}</p></div>
        <Button variant='outline' size='sm' onclick={() => void copy(invitation.invitationLink)}>{m.copy_link()}</Button>
      </div>
    {/each}
  </div>
{/if}
