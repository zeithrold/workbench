<script lang='ts'>
  /* eslint-disable no-alert, style/max-statements-per-line */
  import type { TenantResource } from '$lib/entities/management/model.js'
  import { goto } from '$app/navigation'
  import { resolve } from '$app/paths'
  import { page } from '$app/state'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { management } from '$lib/entities/management/management.svelte.js'
  import { m } from '$lib/paraglide/messages.js'
  import { Alert, Button, Card, CardContent, CardHeader, CardTitle, Input, Label, LoadingState, PageHeader } from '$lib/shared/ui'

  let tenant = $state<TenantResource | null>(null)
  let name = $state('')
  let slug = $state('')
  let timezone = $state('UTC')
  let locale = $state('en-US')
  let error = $state<Error | null>(null)

  $effect(() => { void managementGateway.listTenants().then((items) => { tenant = items.find(item => item.id === page.params.id) ?? null; if (tenant) { name = tenant.name; slug = tenant.slug; timezone = tenant.timezone; locale = tenant.locale } }).catch(reason => error = reason as Error) })
</script>

<div class='space-y-8'>
  <PageHeader title={tenant?.name ?? m.management_tenant()} description={m.management_tenant_metadata_description()} />
  {#if error}<Alert variant='destructive'>{error.message}</Alert>{:else if !tenant}<LoadingState label={m.management_loading_tenant()} />{:else}
    <Card><CardHeader><CardTitle>{m.management_settings()}</CardTitle></CardHeader><CardContent><form class='grid gap-4 md:grid-cols-2' onsubmit={async (event) => { event.preventDefault(); tenant = await managementGateway.updateTenant(tenant!.id, { name, slug, timezone, locale }) }}><div><Label for='name'>{m.management_name()}</Label><Input id='name' bind:value={name} disabled={!management.has('INSTANCE', 'tenant.update')} /></div><div><Label for='slug'>{m.management_slug()}</Label><Input id='slug' bind:value={slug} disabled={!management.has('INSTANCE', 'tenant.update')} /></div><div><Label for='timezone'>{m.management_timezone()}</Label><Input id='timezone' bind:value={timezone} disabled={!management.has('INSTANCE', 'tenant.update')} /></div><div><Label for='locale'>{m.management_locale()}</Label><Input id='locale' bind:value={locale} disabled={!management.has('INSTANCE', 'tenant.update')} /></div>{#if management.has('INSTANCE', 'tenant.update')}<Button class='w-fit'>{m.management_save_changes()}</Button>{/if}</form></CardContent></Card>
    {#if management.has('INSTANCE', 'tenant.delete')}<Card class='border-destructive/40'><CardHeader><CardTitle>{m.management_danger_zone()}</CardTitle></CardHeader><CardContent><p class='mb-4 text-sm text-muted-foreground'>{m.management_tenant_destruction_description()}</p><Button variant='destructive' onclick={async () => { if (confirm(m.management_destroy_tenant_confirmation({ tenant: tenant?.name ?? m.management_tenant() }))) { await managementGateway.destroyTenant(tenant!.id, 'Destroyed from management center'); await goto(resolve('/manage/instance/tenants')) } }}>{m.management_destroy_tenant()}</Button></CardContent></Card>{/if}
  {/if}
</div>
