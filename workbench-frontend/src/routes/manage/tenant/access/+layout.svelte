<script lang='ts'>
  import type { Snippet } from 'svelte'
  import { resolve } from '$app/paths'
  import { page } from '$app/state'
  import { PageHeader } from '$lib/shared/ui'

  const { children }: { children: Snippet } = $props()
  const sections = [
    { href: '/manage/tenant/access', label: 'Policies' },
    { href: '/manage/tenant/access/groups', label: 'Groups' },
    { href: '/manage/tenant/access/bindings', label: 'Bindings' },
  ] as const
</script>

<div class='space-y-6'>
  <PageHeader title='Access control' description='Author tenant policies, organize members, and assign tenant-wide permissions.' />
  <nav class='flex gap-1 border-b' aria-label='Access control sections'>
    {#each sections as section (section.href)}
      <a href={resolve(section.href)} class:border-foreground={section.href === '/manage/tenant/access' ? page.url.pathname === section.href : page.url.pathname.startsWith(section.href)} class='border-b-2 border-transparent px-4 py-2 text-sm font-medium text-muted-foreground hover:text-foreground'>{section.label}</a>
    {/each}
  </nav>
  {@render children()}
</div>
