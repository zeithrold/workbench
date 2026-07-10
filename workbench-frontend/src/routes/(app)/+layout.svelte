<script lang='ts'>
  import type { Snippet } from 'svelte'
  import { browser } from '$app/environment'
  import { goto } from '$app/navigation'
  import { resolve } from '$app/paths'
  import { session } from '$lib/entities/session/session.svelte.js'
  import ApplicationShell from '$lib/widgets/application-shell/application-shell.svelte'

  const { children }: { children: Snippet } = $props()

  $effect(() => {
    if (browser && !session.current)
      void goto(resolve('/login'))
  })
</script>

{#if session.current}
  <ApplicationShell>{@render children()}</ApplicationShell>
{/if}
