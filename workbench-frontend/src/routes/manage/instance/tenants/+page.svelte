<script lang='ts'>
  /* eslint-disable style/max-statements-per-line */
  import type { TenantResource } from '$lib/entities/management/model.js'
  import { resolve } from '$app/paths'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { management } from '$lib/entities/management/management.svelte.js'
  import { Badge, Button, Card, CardContent, CardHeader, CardTitle, Input, Label, LoadingState, PageHeader } from '$lib/shared/ui'

  let tenants = $state<TenantResource[]>([])
  let loading = $state(true)
  let error = $state<Error | null>(null)
  let name = $state('')
  let slug = $state('')
  let creating = $state(false)

  async function load() {
    loading = true; try { tenants = await managementGateway.listTenants() }
    catch (reason) { error = reason as Error }
    finally { loading = false }
  }
  async function create(event: SubmitEvent) {
    event.preventDefault(); creating = true
    try { await managementGateway.createTenant({ name, slug, timezone: 'UTC', locale: 'en-US', adminAssignment: { mode: 'SELF' } }); name = ''; slug = ''; await load() }
    catch (reason) { error = reason as Error }
    finally { creating = false }
  }
  $effect(() => { void load() })
</script>

<div class='space-y-8'>
  <PageHeader title='Tenants' description='Create and govern every tenant in this instance.' />
  {#if management.has('INSTANCE', 'tenant.create')}
    <Card><CardHeader><CardTitle>Create tenant</CardTitle></CardHeader><CardContent><form class='grid gap-4 md:grid-cols-[1fr_1fr_auto]' onsubmit={create}><div><Label for='tenant-name'>Name</Label><Input id='tenant-name' bind:value={name} required /></div><div><Label for='tenant-slug'>Slug</Label><Input id='tenant-slug' bind:value={slug} pattern={'[a-z][a-z0-9-]{1,48}'} required /></div><Button class='self-end' disabled={creating}>{creating ? 'Creating…' : 'Create'}</Button></form></CardContent></Card>
  {/if}
  {#if error}<p class='text-sm text-destructive'>{error.message}</p>{/if}
  {#if loading}<LoadingState label='Loading tenants' />{:else}
    <div class='overflow-hidden rounded-lg border bg-background'><table class='w-full text-left text-sm'><thead><tr class='border-b bg-muted/40 text-muted-foreground'><th class='px-4 py-3'>Tenant</th><th>Status</th><th>Timezone</th><th>Locale</th><th></th></tr></thead><tbody>{#each tenants as tenant (tenant.id)}<tr class='border-b'><td class='px-4 py-3'><p class='font-medium'>{tenant.name}</p><p class='text-xs text-muted-foreground'>{tenant.slug}</p></td><td><Badge variant='secondary'>{tenant.status}</Badge></td><td>{tenant.timezone}</td><td>{tenant.locale}</td><td class='pr-4 text-right'><a class='text-sm font-medium text-primary hover:underline' href={resolve(`/manage/instance/tenants/${tenant.id}`)}>Manage</a></td></tr>{/each}</tbody></table></div>
  {/if}
</div>
