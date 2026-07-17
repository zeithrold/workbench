<script lang='ts'>
  import type { TenantLoginMethod } from './login-discovery.js'
  import { goto } from '$app/navigation'
  import { resolve } from '$app/paths'
  import { page } from '$app/state'
  import { ApiProblemError } from '$lib/api/problem.js'
  import { session } from '$lib/entities/session/session.svelte.js'
  import { m } from '$lib/paraglide/messages.js'
  import { Alert, Button, FormField, Input, PasswordInput } from '$lib/shared/ui'
  import { discoverLogin } from './login-discovery.js'
  import { safeReturnTo } from './return-to.js'

  type Step = 'email' | 'tenant' | 'password'

  interface TenantChoice {
    tenant: { id: string, name: string, slug: string }
    loginMethod: TenantLoginMethod['loginMethod']
  }

  let step = $state<Step>('email')
  let email = $state('')
  let password = $state('')
  let loginMethodId = $state('')
  let tenantId = $state<string | undefined>()
  let tenantChoices = $state<TenantChoice[]>([])
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
      if (discovery.flow === 'INSTANCE_ONLY' && !discovery.instancePasswordMethod) {
        error = m.password_sign_in_unavailable()
        return
      }
      if (discovery.flow === 'INSTANCE_ONLY') {
        loginMethodId = discovery.instancePasswordMethod!.id
        tenantId = undefined
        step = 'password'
        return
      }
      tenantChoices = discovery.tenantMethods.filter(method => method.loginMethod.kind === 'PASSWORD').flatMap(method =>
        method.supportedTenants.map(tenant => ({ tenant, loginMethod: method.loginMethod })),
      )
      if (tenantChoices.length === 0) {
        error = m.password_sign_in_unavailable()
        return
      }
      if (tenantChoices.length === 1) {
        selectTenant(tenantChoices[0])
        return
      }
      step = 'tenant'
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
      const current = await session.signIn({ email: email.trim(), password, loginMethodId, tenantId })
      const returnTo = safeReturnTo(page.url.searchParams.get('returnTo'))
      // returnTo is restricted to a same-origin path by safeReturnTo.
      // eslint-disable-next-line svelte/no-navigation-without-resolve
      await goto(returnTo ?? resolve(current.activeTenant ? '/' : '/setup/complete'))
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
    tenantId = undefined
    tenantChoices = []
    error = ''
  }

  function selectTenant(choice: TenantChoice) {
    tenantId = choice.tenant.id
    loginMethodId = choice.loginMethod.id
    step = 'password'
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
  {:else if step === 'tenant'}
    <div class='grid gap-3'>
      <p class='text-sm font-medium'>{m.choose_tenant()}</p>
      {#each tenantChoices as choice (`${choice.tenant.id}:${choice.loginMethod.id}`)}
        <Button type='button' variant='outline' class='h-auto justify-start px-4 py-3 text-left' onclick={() => selectTenant(choice)}>
          <span><span class='block font-medium'>{choice.tenant.name}</span><span class='block text-xs text-muted-foreground'>{choice.loginMethod.name}</span></span>
        </Button>
      {/each}
      <Button type='button' variant='ghost' onclick={editEmail}>{m.change_email()}</Button>
    </div>
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
