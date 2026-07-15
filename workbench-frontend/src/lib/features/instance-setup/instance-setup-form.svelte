<script lang='ts'>
  import { goto } from '$app/navigation'
  import { resolve } from '$app/paths'
  import { ApiProblemError } from '$lib/api/problem.js'
  import { appBootstrap } from '$lib/app/app-bootstrap.svelte.js'
  import { instanceSetup } from '$lib/entities/instance-setup/instance-setup.svelte.js'
  import { session } from '$lib/entities/session/session.svelte.js'
  import { m } from '$lib/paraglide/messages.js'
  import { Alert, Button, FormField, Input, PasswordInput } from '$lib/shared/ui'

  let displayName = $state('')
  let email = $state('')
  let password = $state('')
  let confirmPassword = $state('')
  let setupToken = $state('')
  let pending = $state(false)
  let formError = $state('')
  let passwordError = $state('')
  let confirmPasswordError = $state('')
  let setupTokenError = $state('')

  function validate(): boolean {
    passwordError = password.length < 12 || password.length > 128
      ? m.password_length_error()
      : ''
    confirmPasswordError = password !== confirmPassword ? m.passwords_do_not_match() : ''
    setupTokenError = instanceSetup.current?.setupTokenRequired && !setupToken.trim()
      ? m.setup_token_required()
      : ''
    return !passwordError && !confirmPasswordError && !setupTokenError
  }

  async function submit() {
    formError = ''
    setupTokenError = ''
    if (!validate())
      return

    pending = true
    try {
      const createdSession = await instanceSetup.setup({
        displayName: displayName.trim(),
        email: email.trim(),
        password,
        setupToken: setupToken.trim() || undefined,
      })
      session.accept(createdSession)
      await goto(resolve('/setup/complete'))
    }
    catch (error) {
      if (error instanceof ApiProblemError && error.status === 403) {
        setupTokenError = error.message
      }
      else if (error instanceof ApiProblemError && error.status === 409) {
        await appBootstrap.load(true).catch(() => undefined)
      }
      else {
        formError = error instanceof Error ? error.message : m.instance_setup_failed()
      }
    }
    finally {
      pending = false
    }
  }

  function handleSubmit(event: SubmitEvent) {
    event.preventDefault()
    void submit()
  }
</script>

<form class='grid gap-5' onsubmit={handleSubmit} novalidate>
  {#if formError}<Alert variant='destructive'>{formError}</Alert>{/if}

  <FormField id='display-name' label={m.display_name()} required>
    {#snippet children(context)}
      <Input
        id='display-name'
        name='displayName'
        bind:value={displayName}
        autocomplete='name'
        placeholder={m.admin_placeholder()}
        required
        disabled={pending}
        aria-describedby={context.describedBy}
        aria-invalid={context.invalid}
      />
    {/snippet}
  </FormField>

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

  <FormField
    id='password'
    label={m.password()}
    description={m.password_requirements()}
    error={passwordError}
    required
  >
    {#snippet children(context)}
      <PasswordInput
        id='password'
        name='password'
        bind:value={password}
        autocomplete='new-password'
        minlength={12}
        maxlength={128}
        required
        disabled={pending}
        aria-describedby={context.describedBy}
        aria-invalid={context.invalid}
      />
    {/snippet}
  </FormField>

  <FormField id='confirm-password' label={m.confirm_password()} error={confirmPasswordError} required>
    {#snippet children(context)}
      <PasswordInput
        id='confirm-password'
        name='confirmPassword'
        bind:value={confirmPassword}
        autocomplete='new-password'
        minlength={12}
        maxlength={128}
        required
        disabled={pending}
        aria-describedby={context.describedBy}
        aria-invalid={context.invalid}
      />
    {/snippet}
  </FormField>

  {#if instanceSetup.current?.setupTokenRequired}
    <FormField
      id='setup-token'
      label={m.setup_token()}
      description={m.setup_token_description()}
      error={setupTokenError}
      required
    >
      {#snippet children(context)}
        <PasswordInput
          id='setup-token'
          name='setupToken'
          bind:value={setupToken}
          autocomplete='off'
          required
          disabled={pending}
          aria-describedby={context.describedBy}
          aria-invalid={context.invalid}
        />
      {/snippet}
    </FormField>
  {/if}

  <Button type='submit' size='lg' disabled={pending}>
    {pending ? m.creating_administrator() : m.initialize_workbench_title()}
  </Button>
</form>
