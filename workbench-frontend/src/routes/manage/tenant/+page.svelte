<script lang='ts'>
  /* eslint-disable style/max-statements-per-line */
  import type { TenantResource } from '$lib/entities/management/model.js'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { management } from '$lib/entities/management/management.svelte.js'
  import { m } from '$lib/paraglide/messages.js'
  import { Alert, Button, Card, CardContent, CardHeader, CardTitle, Input, Label, LoadingState, PageHeader } from '$lib/shared/ui'

  let tenant = $state<TenantResource | null>(null); let name = $state(''); let slug = $state(''); let timezone = $state('UTC'); let locale = $state('en-US'); let error = $state<Error | null>(null)
  $effect(() => { void managementGateway.currentTenant().then((value) => { tenant = value; name = value.name; slug = value.slug; timezone = value.timezone; locale = value.locale }).catch(reason => error = reason as Error) })
</script>

<div class='space-y-8'><PageHeader title={m.management_tenant_settings()} description={m.management_tenant_settings_description()} />
  {#if error}<Alert variant='destructive'>{error.message}</Alert>{:else if !tenant}<LoadingState label={m.management_loading_tenant_settings()} />{:else}<Card><CardHeader><CardTitle>{tenant.name}</CardTitle></CardHeader><CardContent><form class='grid gap-4 md:grid-cols-2' onsubmit={async (event) => { event.preventDefault(); tenant = await managementGateway.updateCurrentTenant({ name, slug, timezone, locale }) }}><div><Label for='tenant-name'>{m.management_name()}</Label><Input id='tenant-name' bind:value={name} disabled={!management.has('TENANT', 'tenant.update')} /></div><div><Label for='tenant-slug'>{m.management_slug()}</Label><Input id='tenant-slug' bind:value={slug} disabled={!management.has('TENANT', 'tenant.update')} /></div><div><Label for='tenant-timezone'>{m.management_timezone()}</Label><Input id='tenant-timezone' bind:value={timezone} disabled={!management.has('TENANT', 'tenant.update')} /></div><div><Label for='tenant-locale'>{m.management_locale()}</Label><Input id='tenant-locale' bind:value={locale} disabled={!management.has('TENANT', 'tenant.update')} /></div>{#if management.has('TENANT', 'tenant.update')}<Button class='w-fit'>{m.management_save_changes()}</Button>{/if}</form></CardContent></Card>{/if}
</div>
