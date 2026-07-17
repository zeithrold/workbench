<script lang='ts'>
  import type { InvitationPreview } from '$lib/entities/invitation/model.js'
  import { goto } from '$app/navigation'
  import { resolve } from '$app/paths'
  import { page } from '$app/state'
  import { invitationGateway } from '$lib/entities/invitation/invitation-gateway.js'
  import { session } from '$lib/entities/session/session.svelte.js'
  import { m } from '$lib/paraglide/messages.js'
  import { Alert, BrandLogo, Button, Card, CardContent, CardDescription, CardHeader, CardTitle, FormField, Input, LoadingState, PasswordInput } from '$lib/shared/ui'

  let preview = $state<InvitationPreview | null>(null)
  let displayName = $state('')
  let password = $state('')
  let confirmPassword = $state('')
  let pending = $state(false)
  let error = $state('')
  const token = $derived(page.params.token ?? '')
  const signedInMatches = $derived(
    Boolean(session.current && preview && session.current.user.primaryEmail.toLowerCase() === preview.email.toLowerCase()),
  )
  const returnTo = $derived(`/invitations/${encodeURIComponent(token)}`)

  async function load() {
    error = ''
    try {
      preview = await invitationGateway.preview(token)
      displayName = preview.displayName ?? ''
    }
    catch (caught) {
      error = caught instanceof Error ? caught.message : m.invitation_load_failed()
    }
  }

  async function finishAcceptance(operation: () => Promise<unknown>) {
    pending = true
    error = ''
    try {
      await operation()
      await session.restore()
      await goto(resolve('/'))
    }
    catch (caught) {
      error = caught instanceof Error ? caught.message : m.invitation_accept_failed()
    }
    finally {
      pending = false
    }
  }

  function acceptNew(event: SubmitEvent) {
    event.preventDefault()
    if (password !== confirmPassword) {
      error = m.passwords_do_not_match()
      return
    }
    void finishAcceptance(() => invitationGateway.acceptNew(token, displayName.trim(), password))
  }

  async function switchAccount() {
    await session.signOut()
    // returnTo is constructed from the current local invitation route.
    // eslint-disable-next-line svelte/no-navigation-without-resolve
    await goto(`/login?${new URLSearchParams({ returnTo })}`)
  }

  $effect(() => {
    void load()
  })
</script>

<svelte:head><title>{m.invitation_title()}</title></svelte:head>

<main class='grid min-h-screen place-items-center bg-muted/30 p-6'>
  <Card class='w-full max-w-lg'>
    <CardHeader class='space-y-4'><BrandLogo /><div><CardTitle>{m.invitation_title()}</CardTitle><CardDescription>{preview ? m.invitation_description({ tenantName: preview.tenant.name }) : m.invitation_loading()}</CardDescription></div></CardHeader>
    <CardContent class='space-y-5'>
      {#if error}<Alert variant='destructive'>{error}</Alert>{/if}
      {#if !preview && !error}<LoadingState label={m.invitation_loading()} />
      {:else if preview}
        <div class='rounded-lg border bg-muted/20 p-4'><p class='font-medium'>{preview.tenant.name}</p><p class='text-sm text-muted-foreground'>{preview.email}</p></div>
        {#if preview.type === 'TENANT_MEMBER' && signedInMatches}
          <Button class='w-full' disabled={pending} onclick={() => void finishAcceptance(() => invitationGateway.acceptExisting(token))}>{pending ? m.invitation_accepting() : m.invitation_accept()}</Button>
        {:else if session.current}
          <Alert>{m.invitation_account_mismatch({ email: preview.email })}</Alert>
          <Button class='w-full' variant='outline' onclick={() => void switchAccount()}>{m.switch_account()}</Button>
        {:else}
          <form class='grid gap-4' onsubmit={acceptNew}>
            <FormField id='invitation-display-name' label={m.display_name()} required>{#snippet children(context)}<Input id='invitation-display-name' bind:value={displayName} required disabled={pending} aria-describedby={context.describedBy} />{/snippet}</FormField>
            <FormField id='invitation-password' label={m.password()} description={m.password_requirements()} required>{#snippet children(context)}<PasswordInput id='invitation-password' bind:value={password} minlength={12} maxlength={128} required disabled={pending} aria-describedby={context.describedBy} />{/snippet}</FormField>
            <FormField id='invitation-confirm-password' label={m.confirm_password()} required>{#snippet children(context)}<PasswordInput id='invitation-confirm-password' bind:value={confirmPassword} minlength={12} maxlength={128} required disabled={pending} aria-describedby={context.describedBy} />{/snippet}</FormField>
            <Button type='submit' disabled={pending}>{pending ? m.invitation_accepting() : m.invitation_create_account()}</Button>
          </form>
          {#if preview.type === 'TENANT_MEMBER'}<Button class='w-full' variant='link' href={`/login?${new URLSearchParams({ returnTo })}`}>{m.invitation_existing_account()}</Button>{/if}
        {/if}
      {/if}
    </CardContent>
  </Card>
</main>
