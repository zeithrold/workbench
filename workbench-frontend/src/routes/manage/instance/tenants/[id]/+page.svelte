<script lang='ts'>
  /* eslint-disable no-alert, style/max-statements-per-line */
  import type { TenantResource } from '$lib/entities/management/model.js'
  import { goto } from '$app/navigation'
  import { resolve } from '$app/paths'
  import { page } from '$app/state'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { management } from '$lib/entities/management/management.svelte.js'
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
  <PageHeader title={tenant?.name ?? 'Tenant'} description='Instance-scoped tenant metadata and lifecycle.' />
  {#if error}<Alert variant='destructive'>{error.message}</Alert>{:else if !tenant}<LoadingState label='Loading tenant' />{:else}
    <Card><CardHeader><CardTitle>Settings</CardTitle></CardHeader><CardContent><form class='grid gap-4 md:grid-cols-2' onsubmit={async (event) => { event.preventDefault(); tenant = await managementGateway.updateTenant(tenant!.id, { name, slug, timezone, locale }) }}><div><Label for='name'>Name</Label><Input id='name' bind:value={name} disabled={!management.has('INSTANCE', 'tenant.update')} /></div><div><Label for='slug'>Slug</Label><Input id='slug' bind:value={slug} disabled={!management.has('INSTANCE', 'tenant.update')} /></div><div><Label for='timezone'>Timezone</Label><Input id='timezone' bind:value={timezone} disabled={!management.has('INSTANCE', 'tenant.update')} /></div><div><Label for='locale'>Locale</Label><Input id='locale' bind:value={locale} disabled={!management.has('INSTANCE', 'tenant.update')} /></div>{#if management.has('INSTANCE', 'tenant.update')}<Button class='w-fit'>Save changes</Button>{/if}</form></CardContent></Card>
    {#if management.has('INSTANCE', 'tenant.delete')}<Card class='border-destructive/40'><CardHeader><CardTitle>Danger zone</CardTitle></CardHeader><CardContent><p class='mb-4 text-sm text-muted-foreground'>Destruction is asynchronous and revokes tenant access.</p><Button variant='destructive' onclick={async () => { if (confirm(`Destroy ${tenant?.name}?`)) { await managementGateway.destroyTenant(tenant!.id, 'Destroyed from management center'); await goto(resolve('/manage/instance/tenants')) } }}>Destroy tenant</Button></CardContent></Card>{/if}
  {/if}
</div>
