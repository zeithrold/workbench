<script lang='ts'>
  import type { Snippet } from 'svelte'
  import { resolve } from '$app/paths'
  import { page } from '$app/state'
  import { management } from '$lib/entities/management/management.svelte.js'
  import { session } from '$lib/entities/session/session.svelte.js'
  import SignOutButton from '$lib/features/auth/sign-out-button.svelte'
  import TenantSwitcher from '$lib/features/tenant/tenant-switcher.svelte'
  import { m } from '$lib/paraglide/messages.js'
  import { Alert, BrandLogo, LoadingState, Separator } from '$lib/shared/ui'
  import { Activity, Building2, ChevronRight, Database, KeyRound, LayoutDashboard, Settings, Shield, Users } from '@lucide/svelte'

  const { children }: { children: Snippet } = $props()
  const instanceMode = $derived(page.url.pathname.startsWith('/manage/instance'))
  const tenantMode = $derived(page.url.pathname.startsWith('/manage/tenant'))
  const activeTenantId = $derived(session.current?.activeTenant?.id ?? null)

  $effect(() => {
    const tenantId = activeTenantId
    management.invalidateTenant()
    void management.load(Boolean(tenantId))
  })

  const instanceNav = [
    { href: '/manage/instance', label: m.overview(), action: 'instance.read', icon: LayoutDashboard },
    { href: '/manage/instance/tenants', label: m.management_tenants(), action: 'tenant.read', icon: Building2 },
    { href: '/manage/instance/administrators', label: m.management_administrators(), action: 'instance.read', icon: Shield },
    { href: '/manage/instance/operations', label: m.management_operations(), action: 'operations.read', icon: Activity },
    { href: '/manage/instance/outbox', label: m.management_outbox(), action: 'outbox.read', icon: Database },
  ] as const
  const tenantNav = [
    { href: '/manage/tenant', label: m.management_settings(), action: 'tenant.read', icon: Settings },
    { href: '/manage/tenant/members', label: m.management_members(), action: 'tenant.read', icon: Users },
    { href: '/manage/tenant/administrators', label: m.management_administrators(), action: 'tenant.read', icon: Shield },
    { href: '/manage/tenant/access', label: m.management_access_control(), action: 'tenant.read', icon: KeyRound },
  ] as const
</script>

{#if management.loading}
  <main class='grid min-h-screen place-items-center'><LoadingState label={m.management_loading()} /></main>
{:else if management.error}
  <main class='mx-auto max-w-xl p-10'><Alert variant='destructive'>{management.error.message}</Alert></main>
{:else}
  <div class='min-h-screen bg-muted/20 text-foreground lg:grid lg:grid-cols-[17rem_1fr]'>
    <aside class='border-b bg-background p-5 lg:min-h-screen lg:border-r lg:border-b-0'>
      <BrandLogo />
      <a class='mt-5 flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground' href={resolve('/')}>
        <ChevronRight class='size-4 rotate-180' /> {m.management_back_to_workspace()}
      </a>
      <div class='mt-6 grid grid-cols-2 gap-1 rounded-lg bg-muted p-1'>
        {#if management.instance}
          <a class:!bg-background={instanceMode} class='rounded-md px-3 py-2 text-center text-sm' href={resolve('/manage/instance')}>{m.management_instance()}</a>
        {/if}
        {#if management.tenant}
          <a class:!bg-background={tenantMode} class='rounded-md px-3 py-2 text-center text-sm' href={resolve('/manage/tenant')}>{m.management_tenant()}</a>
        {/if}
      </div>
      <nav class='mt-6 space-y-1' aria-label={m.management_navigation()}>
        {#each (instanceMode ? instanceNav : tenantNav).filter(item => management.has(instanceMode ? 'INSTANCE' : 'TENANT', item.action)) as item (item.href)}
          <a class:bg-muted={page.url.pathname === item.href} class='flex items-center gap-3 rounded-md px-3 py-2 text-sm hover:bg-muted' href={resolve(item.href)}>
            <item.icon class='size-4' /> {item.label}
          </a>
        {/each}
      </nav>
      {#if tenantMode}
        <div class='mt-6'><Separator /><div class='mt-5'><TenantSwitcher /></div></div>
      {/if}
    </aside>
    <div class='min-w-0'>
      <header class='flex h-16 items-center justify-between border-b bg-background px-6'>
        <div>
          <p class='text-xs uppercase tracking-wide text-muted-foreground'>{instanceMode ? m.management_instance_mode() : m.management_tenant_mode()}</p>
          <p class='font-medium'>{instanceMode ? management.instance?.instance.name : session.current?.activeTenant?.name}</p>
        </div>
        <div class='flex items-center gap-3'><span class='hidden text-sm sm:inline'>{session.current?.user.displayName}</span><SignOutButton /></div>
      </header>
      <main class='mx-auto w-full max-w-7xl p-6 md:p-10'>{@render children()}</main>
    </div>
  </div>
{/if}
