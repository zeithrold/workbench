<script lang='ts'>
  import { m } from '$lib/paraglide/messages.js'
  import EmptyState from './empty-state.svelte'

  const { description }: { description: string } = $props()
</script>

<EmptyState title={m.access_denied()} {description} />
