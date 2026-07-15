<script lang='ts' generics='Props extends Record<string, any>'>
  import type { Component, Snippet } from 'svelte'
  import { Button } from '$lib/components/ui/button'
  import { Skeleton } from '$lib/components/ui/skeleton'
  import { m } from '$lib/paraglide/messages.js'

  type ComponentModule = { default: Component<Props> }

  const {
    loader,
    componentProps,
    loading,
    error,
    loadingLabel = 'Loading component',
  }: {
    loader: () => Promise<ComponentModule>
    componentProps: Props
    loading?: Snippet
    error?: Snippet<[unknown, () => void]>
    loadingLabel?: string
  } = $props()

  let attempt = $state(0)
  let cachedLoader: typeof loader | undefined
  let cachedAttempt = -1
  let cachedModule: Promise<ComponentModule> | undefined
  const componentModule = $derived.by(() => {
    const nextLoader = loader
    const nextAttempt = attempt
    if (nextLoader !== cachedLoader || nextAttempt !== cachedAttempt) {
      cachedLoader = nextLoader
      cachedAttempt = nextAttempt
      cachedModule = nextLoader()
    }
    return cachedModule!
  })

  function retry() {
    attempt += 1
  }
</script>

{#await componentModule}
  {#if loading}
    {@render loading()}
  {:else}
    <Skeleton class='h-24 w-full' label={loadingLabel} />
  {/if}
{:then module}
  <module.default {...componentProps} />
{:catch loadError}
  {#if error}
    {@render error(loadError, retry)}
  {:else}
    <div class='flex items-center justify-between gap-4 rounded-md border border-destructive/30 bg-destructive/5 p-4' role='alert'>
      <span class='text-sm text-destructive'>{m.component_load_failed()}</span>
      <Button variant='outline' size='sm' onclick={retry}>{m.retry()}</Button>
    </div>
  {/if}
{/await}
