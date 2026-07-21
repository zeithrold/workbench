<script lang='ts'>
  import { browser } from '$app/environment'
  import { goto } from '$app/navigation'
  import { resolve } from '$app/paths'
  import { page } from '$app/state'
  import { appBootstrap } from '$lib/app/app-bootstrap.svelte.js'
  import { createWorkbenchQueryClient } from '$lib/app/query-client.js'
  import { startupDestination } from '$lib/app/startup-destination.js'
  import { instanceSetup } from '$lib/entities/instance-setup/instance-setup.svelte.js'
  import { session } from '$lib/entities/session/session.svelte.js'
  import { localeState } from '$lib/i18n/locale.svelte.js'
  import { m } from '$lib/paraglide/messages.js'
  import { Alert, BrandLogo, Button, LoadingState } from '$lib/shared/ui'
  import { QueryClientProvider } from '@tanstack/svelte-query'
  import '../app.css'

  const { children } = $props()
  const queryClient = createWorkbenchQueryClient()

  const destination = $derived(
    appBootstrap.state === 'ready' && instanceSetup.current
      ? startupDestination({
        initialized: instanceSetup.current.initialized,
        session: session.current,
        pathname: page.url.pathname,
      })
      : null,
  )

  $effect(() => {
    if (browser && appBootstrap.state === 'idle')
      void appBootstrap.load().catch(() => undefined)
  })

  $effect(() => {
    if (browser && destination)
      void goto(resolve(destination))
  })
</script>

<QueryClientProvider client={queryClient}>
  {#key localeState.current}
    {#if appBootstrap.state === 'loading' || appBootstrap.state === 'idle'}
      <main class='grid min-h-screen place-items-center bg-muted/30 p-6'>
        <div class='space-y-5 text-center'>
          <BrandLogo class='justify-center' />
          <LoadingState label={m.app_starting()} />
        </div>
      </main>
    {:else if appBootstrap.state === 'failed'}
      <main class='grid min-h-screen place-items-center bg-muted/30 p-6'>
        <div class='w-full max-w-md space-y-4'>
          <BrandLogo />
          <Alert variant='destructive'>
            <div class='space-y-1'>
              <p class='font-medium'>{m.app_start_failed()}</p>
              <p>{appBootstrap.error?.message ?? m.server_unreachable()}</p>
            </div>
          </Alert>
          <Button onclick={() => void appBootstrap.load(true).catch(() => undefined)}>{m.try_again()}</Button>
        </div>
      </main>
    {:else if !destination}
      {@render children()}
    {/if}
  {/key}
</QueryClientProvider>
