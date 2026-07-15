<script lang='ts'>
  import type { Snippet } from 'svelte'
  import ManagementShell from '$lib/widgets/management-shell/management-shell.svelte'

  const { children }: { children: Snippet } = $props()
</script>

<ManagementShell>{@render children()}</ManagementShell>
