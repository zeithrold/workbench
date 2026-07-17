<script lang='ts'>
  import { resolve } from '$app/paths'
  import { managementNavigation } from '$lib/entities/management/management-navigation.svelte.js'
  import { session } from '$lib/entities/session/session.svelte.js'
  import TenantSwitcher from '$lib/features/tenant/tenant-switcher.svelte'
  import { m } from '$lib/paraglide/messages.js'
  import { Alert, Button, Card, CardContent, CardHeader, CardTitle, EmptyState, LoadingState, PageHeader } from '$lib/shared/ui'

  const hasInstanceNavigation = $derived(
    managementNavigation.current?.items.some(item => item.id.startsWith('MANAGEMENT_INSTANCE_')) ?? false,
  )
  const hasTenantNavigation = $derived(
    managementNavigation.current?.items.some(item => item.id.startsWith('MANAGEMENT_TENANT_')) ?? false,
  )
  const tenantNotSelected = $derived(
    managementNavigation.current?.tenantContextStatus === 'NOT_SELECTED',
  )
</script>

<div class='space-y-8'>
  <PageHeader title={m.management_choose_scope()} description={m.management_choose_scope_description()} />
  {#if !managementNavigation.current}
    {#if managementNavigation.error}
      <div class='space-y-3'>
        <Alert variant='destructive'>{managementNavigation.error.message || m.management_navigation_failed()}</Alert>
        <Button onclick={() => void managementNavigation.load(session.current?.activeTenant?.id ?? null, true).catch(() => undefined)}>{m.try_again()}</Button>
      </div>
    {:else}
      <LoadingState label={m.management_navigation_loading()} />
    {/if}
  {:else}
    {#if managementNavigation.error}<Alert variant='destructive'>{managementNavigation.error.message}</Alert>{/if}
    {#if hasInstanceNavigation || hasTenantNavigation || tenantNotSelected}
      <div class='grid gap-4 md:grid-cols-2'>
        {#if hasInstanceNavigation}
          <a href={resolve('/manage/instance')}>
            <Card class='h-full transition-colors hover:border-foreground/30'><CardHeader><CardTitle>{m.management_instance_mode()}</CardTitle></CardHeader><CardContent><p class='text-sm text-muted-foreground'>{m.management_instance_entry_description()}</p></CardContent></Card>
          </a>
        {/if}
        {#if hasTenantNavigation}
          <a href={resolve('/manage/tenant')}>
            <Card class='h-full transition-colors hover:border-foreground/30'><CardHeader><CardTitle>{m.management_tenant_mode()}</CardTitle></CardHeader><CardContent><p class='text-sm text-muted-foreground'>{m.management_tenant_entry_description()}</p></CardContent></Card>
          </a>
        {:else if tenantNotSelected}
          <Card><CardHeader><CardTitle>{m.management_tenant_not_selected()}</CardTitle></CardHeader><CardContent class='space-y-4'><p class='text-sm text-muted-foreground'>{m.management_tenant_not_selected_description()}</p><TenantSwitcher /></CardContent></Card>
        {/if}
      </div>
    {:else}
      <EmptyState title={m.management_no_accessible_areas()} description={m.management_no_accessible_areas_description()} />
    {/if}
  {/if}
</div>
