<script lang='ts'>
  import type { Snippet } from 'svelte'
  import ApplicationShell from '$lib/widgets/application-shell/application-shell.svelte'

  const { children }: { children: Snippet } = $props()
</script>

<ApplicationShell>{@render children()}</ApplicationShell>
