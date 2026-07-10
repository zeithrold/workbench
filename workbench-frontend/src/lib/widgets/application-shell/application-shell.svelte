<script lang='ts'>
  import type { Snippet } from 'svelte'
  import { resolve } from '$app/paths'
  import { session } from '$lib/entities/session/session.svelte.js'
  import SignOutButton from '$lib/features/auth/sign-out-button.svelte'
  import TenantSwitcher from '$lib/features/tenant/tenant-switcher.svelte'
  import { BrandLogo, Separator } from '$lib/shared/ui'

  const { children }: { children: Snippet } = $props()
</script>

<div class='min-h-screen bg-muted/30 text-foreground lg:grid lg:grid-cols-[16rem_1fr]'>
  <aside class='border-b bg-background p-5 lg:min-h-screen lg:border-r lg:border-b-0'>
    <div class='space-y-6'>
      <BrandLogo />
      <nav aria-label='Primary navigation'>
        <a class='block rounded-md bg-muted px-3 py-2 text-sm font-medium' href={resolve('/')}>Overview</a>
      </nav>
      <Separator />
      <TenantSwitcher />
    </div>
  </aside>
  <div class='min-w-0'>
    <header class='flex h-16 items-center justify-between border-b bg-background px-6'>
      <span class='text-sm text-muted-foreground'>{session.current?.activeTenant.name}</span>
      <div class='flex items-center gap-3'>
        <span class='hidden text-sm sm:inline'>{session.current?.user.name}</span>
        <SignOutButton />
      </div>
    </header>
    <main class='mx-auto w-full max-w-6xl p-6 md:p-10'>{@render children()}</main>
  </div>
</div>
