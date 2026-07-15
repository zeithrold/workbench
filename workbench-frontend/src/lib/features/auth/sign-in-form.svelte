<script lang='ts'>
  import { goto } from '$app/navigation'
  import { resolve } from '$app/paths'
  import { ApiProblemError } from '$lib/api/problem.js'
  import { session } from '$lib/entities/session/session.svelte.js'
  import { m } from '$lib/paraglide/messages.js'
  import { Alert, Button, FormField, Input, PasswordInput } from '$lib/shared/ui'
  import { discoverLogin } from './login-discovery.js'

  type Step = 'email' | 'password'

  let step = $state<Step>('email')
  let email = $state('')
  let password = $state('')
  let loginMethodId = $state('')
  let pending = $state(false)
  let error = $state('')

  async function continueWithEmail() {
    pending = true
    error = ''
    try {
      const discovery = await discoverLogin(email.trim())
      if (!discovery.identifierRecognized) {
        error = m.invalid_credentials()
        return
      }
      if (discovery.flow !== 'INSTANCE_ONLY') {
        error = m.tenant_sign_in_unavailable()
        return
      }
      if (!discovery.instancePasswordMethod) {
        error = m.password_sign_in_unavailable()
        return
      }
      loginMethodId = discovery.instancePasswordMethod.id
      step = 'password'
    }
    catch (caught) {
      error = caught instanceof Error ? caught.message : m.sign_in_discovery_failed()
    }
    finally {
      pending = false
    }
  }

  async function signIn() {
    pending = true
    error = ''
    try {
      await session.signIn({ email: email.trim(), password, loginMethodId })
      await goto(resolve('/setup/complete'))
    }
    catch (caught) {
      error = caught instanceof ApiProblemError && caught.status === 401
        ? m.invalid_credentials()
        : caught instanceof Error ? caught.message : m.sign_in_failed()
    }
    finally {
      pending = false
    }
  }

  function handleSubmit(event: SubmitEvent) {
    event.preventDefault()
    void (step === 'email' ? continueWithEmail() : signIn())
  }

  function editEmail() {
    step = 'email'
    password = ''
    loginMethodId = ''
    error = ''
  }
</script>

<form class='grid gap-5' onsubmit={handleSubmit}>
  {#if error}<Alert variant='destructive'>{error}</Alert>{/if}

  {#if step === 'email'}
    <FormField id='email' label={m.email_address()} required>
      {#snippet children(context)}
        <Input
          id='email'
          name='email'
          type='email'
          bind:value={email}
          autocomplete='email'
          placeholder='admin@example.com'
          required
          disabled={pending}
          aria-describedby={context.describedBy}
          aria-invalid={context.invalid}
        />
      {/snippet}
    </FormField>
    <Button type='submit' disabled={pending}>
      {pending ? m.checking_account() : m.continue_action()}
    </Button>
  {:else}
    <div class='flex items-center justify-between rounded-md border bg-muted/20 px-3 py-2 text-sm'>
      <span class='truncate'>{email}</span>
      <Button type='button' variant='ghost' size='sm' onclick={editEmail} disabled={pending}>{m.change()}</Button>
    </div>
    <FormField id='password' label={m.password()} required>
      {#snippet children(context)}
        <PasswordInput
          id='password'
          name='password'
          bind:value={password}
          autocomplete='current-password'
          required
          disabled={pending}
          aria-describedby={context.describedBy}
          aria-invalid={context.invalid}
        />
      {/snippet}
    </FormField>
    <Button type='submit' disabled={pending}>
      {pending ? m.signing_in() : m.sign_in()}
    </Button>
  {/if}
</form>
