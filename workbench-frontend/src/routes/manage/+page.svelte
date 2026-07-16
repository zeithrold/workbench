<script lang='ts'>
  import { browser } from '$app/environment'
  import { goto } from '$app/navigation'
  import { resolve } from '$app/paths'
  import { management } from '$lib/entities/management/management.svelte.js'
  import { m } from '$lib/paraglide/messages.js'
  import { EmptyState } from '$lib/shared/ui'

  $effect(() => {
    if (!browser || management.loading)
      return
    if (management.instance)
      void goto(resolve('/manage/instance'), { replaceState: true })
    else if (management.tenant)
      void goto(resolve('/manage/tenant'), { replaceState: true })
  })
</script>

{#if !management.loading && !management.instance && !management.tenant}
  <EmptyState title={m.management_access_required()} description={m.management_access_required_description()} />
{/if}
