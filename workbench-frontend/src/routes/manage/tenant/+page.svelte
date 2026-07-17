<script lang='ts'>
  /* eslint-disable style/max-statements-per-line */
  import type { TenantResource } from '$lib/entities/management/model.js'
  import { isApiProblemStatus } from '$lib/api/problem.js'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { m } from '$lib/paraglide/messages.js'
  import { AccessDeniedState, Alert, Button, Card, CardContent, CardHeader, CardTitle, Input, Label, LoadingState, PageHeader } from '$lib/shared/ui'

  let tenant = $state<TenantResource | null>(null); let name = $state(''); let slug = $state(''); let timezone = $state('UTC'); let locale = $state('en-US'); let loaded = $state(false); let error = $state<Error | null>(null)
  async function load() {
    try { const value = await managementGateway.currentTenant(); tenant = value; name = value.name; slug = value.slug; timezone = value.timezone; locale = value.locale; loaded = true }
    catch (reason) { error = reason as Error }
  }
  async function save(event: SubmitEvent) {
    event.preventDefault(); error = null
    try { tenant = await managementGateway.updateCurrentTenant({ name, slug, timezone, locale }) }
    catch (reason) { error = reason as Error }
  }
  $effect(() => { void load() })
</script>

<div class='space-y-8'><PageHeader title={m.management_tenant_settings()} description={m.management_tenant_settings_description()} />
  {#if !loaded && error && isApiProblemStatus(error, 403)}<AccessDeniedState description={error.message} />
  {:else if !loaded && error}<Alert variant='destructive'>{error.message}</Alert>
  {:else if !tenant}<LoadingState label={m.management_loading_tenant_settings()} />
  {:else}{#if error}<Alert variant='destructive'>{error.message}</Alert>{/if}<Card><CardHeader><CardTitle>{tenant.name}</CardTitle></CardHeader><CardContent><form class='grid gap-4 md:grid-cols-2' onsubmit={save}><div><Label for='tenant-name'>{m.management_name()}</Label><Input id='tenant-name' bind:value={name} /></div><div><Label for='tenant-slug'>{m.management_slug()}</Label><Input id='tenant-slug' bind:value={slug} /></div><div><Label for='tenant-timezone'>{m.management_timezone()}</Label><Input id='tenant-timezone' bind:value={timezone} /></div><div><Label for='tenant-locale'>{m.management_locale()}</Label><Input id='tenant-locale' bind:value={locale} /></div><Button class='w-fit'>{m.management_save_changes()}</Button></form></CardContent></Card>{/if}
</div>
