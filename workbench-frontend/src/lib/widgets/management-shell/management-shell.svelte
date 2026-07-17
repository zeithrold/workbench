<script lang='ts'>
  import type { Snippet } from 'svelte'
  import { browser } from '$app/environment'
  import { resolve } from '$app/paths'
  import { page } from '$app/state'
  import { managementNavigation } from '$lib/entities/management/management-navigation.svelte.js'
  import { session } from '$lib/entities/session/session.svelte.js'
  import SignOutButton from '$lib/features/auth/sign-out-button.svelte'
  import TenantSwitcher from '$lib/features/tenant/tenant-switcher.svelte'
  import { m } from '$lib/paraglide/messages.js'
  import { Alert, BrandLogo, Button, EmptyState, Separator } from '$lib/shared/ui'
  import { Activity, Building2, ChevronRight, Database, KeyRound, LayoutDashboard, Settings, Shield, Users } from '@lucide/svelte'

  const { children }: { children: Snippet } = $props()
  const instanceMode = $derived(page.url.pathname.startsWith('/manage/instance'))
  const tenantMode = $derived(page.url.pathname.startsWith('/manage/tenant'))

  const instanceNav = [
    { id: 'MANAGEMENT_INSTANCE_OVERVIEW', href: '/manage/instance', label: m.overview(), icon: LayoutDashboard },
    { id: 'MANAGEMENT_INSTANCE_TENANTS', href: '/manage/instance/tenants', label: m.management_tenants(), icon: Building2 },
    { id: 'MANAGEMENT_INSTANCE_ADMINISTRATORS', href: '/manage/instance/administrators', label: m.management_administrators(), icon: Shield },
    { id: 'MANAGEMENT_INSTANCE_OPERATIONS', href: '/manage/instance/operations', label: m.management_operations(), icon: Activity },
    { id: 'MANAGEMENT_INSTANCE_OUTBOX', href: '/manage/instance/outbox', label: m.management_outbox(), icon: Database },
  ] as const
  const tenantNav = [
    { id: 'MANAGEMENT_TENANT_SETTINGS', href: '/manage/tenant', label: m.management_settings(), icon: Settings },
    { id: 'MANAGEMENT_TENANT_MEMBERS', href: '/manage/tenant/members', label: m.management_members(), icon: Users },
    { id: 'MANAGEMENT_TENANT_ADMINISTRATORS', href: '/manage/tenant/administrators', label: m.management_administrators(), icon: Shield },
    { id: 'MANAGEMENT_TENANT_ACCESS', href: '/manage/tenant/access', label: m.management_access_control(), icon: KeyRound },
  ] as const
  const visibleInstanceNav = $derived(instanceNav.filter(item => managementNavigation.has(item.id)))
  const visibleTenantNav = $derived(tenantNav.filter(item => managementNavigation.has(item.id)))
  const tenantNotSelected = $derived(
    managementNavigation.current?.tenantContextStatus === 'NOT_SELECTED',
  )

  $effect(() => {
    const contextKey = session.current?.activeTenant?.id ?? null
    if (browser)
      void managementNavigation.load(contextKey).catch(() => undefined)
  })
</script>

<div class='min-h-screen bg-muted/20 text-foreground lg:grid lg:grid-cols-[17rem_1fr]'>
  <aside class='border-b bg-background p-5 lg:min-h-screen lg:border-r lg:border-b-0'>
    <BrandLogo />
    <a class='mt-5 flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground' href={resolve('/')}>
      <ChevronRight class='size-4 rotate-180' /> {m.management_back_to_workspace()}
    </a>
    {#if visibleInstanceNav.length || visibleTenantNav.length || tenantNotSelected}
      <div class='mt-6 grid grid-cols-2 gap-1 rounded-lg bg-muted p-1'>
        {#if visibleInstanceNav.length}<a class:!bg-background={instanceMode} class='rounded-md px-3 py-2 text-center text-sm' href={resolve('/manage/instance')}>{m.management_instance()}</a>{/if}
        {#if visibleTenantNav.length}<a class:!bg-background={tenantMode} class='rounded-md px-3 py-2 text-center text-sm' href={resolve('/manage/tenant')}>{m.management_tenant()}</a>{:else if tenantNotSelected}<a class='rounded-md px-3 py-2 text-center text-sm' href={resolve('/manage')}>{m.management_tenant()}</a>{/if}
      </div>
    {/if}
    {#if instanceMode || tenantMode}
      <nav class='mt-6 space-y-1' aria-label={m.management_navigation()}>
        {#each (instanceMode ? visibleInstanceNav : visibleTenantNav) as item (item.href)}
          <a class:bg-muted={page.url.pathname === item.href} class='flex items-center gap-3 rounded-md px-3 py-2 text-sm hover:bg-muted' href={resolve(item.href)}>
            <item.icon class='size-4' /> {item.label}
          </a>
        {/each}
      </nav>
    {/if}
    {#if managementNavigation.error}
      <div class='mt-5 space-y-3'>
        <Alert variant='destructive'>{m.management_navigation_failed()}</Alert>
        <Button size='sm' variant='outline' onclick={() => void managementNavigation.load(session.current?.activeTenant?.id ?? null, true).catch(() => undefined)}>{m.try_again()}</Button>
      </div>
    {/if}
    {#if tenantMode}
      <div class='mt-6'><Separator /><div class='mt-5'><TenantSwitcher /></div></div>
    {/if}
  </aside>
  <div class='min-w-0'>
    <header class='flex h-16 items-center justify-between border-b bg-background px-6'>
      <div>
        <p class='text-xs uppercase tracking-wide text-muted-foreground'>{instanceMode ? m.management_instance_mode() : tenantMode ? m.management_tenant_mode() : m.management()}</p>
        <p class='font-medium'>{instanceMode ? m.management_instance() : tenantMode ? (session.current?.activeTenant?.name ?? m.management_tenant()) : m.app_name()}</p>
      </div>
      <div class='flex items-center gap-3'><span class='hidden text-sm sm:inline'>{session.current?.user.displayName}</span><SignOutButton /></div>
    </header>
    <main class='mx-auto w-full max-w-7xl p-6 md:p-10'>
      {#if tenantMode && !session.current?.activeTenant}
        <EmptyState title={m.management_tenant_not_selected()} description={m.management_tenant_not_selected_description()}>
          {#snippet action()}<TenantSwitcher />{/snippet}
        </EmptyState>
      {:else}
        {@render children()}
      {/if}
    </main>
  </div>
</div>
