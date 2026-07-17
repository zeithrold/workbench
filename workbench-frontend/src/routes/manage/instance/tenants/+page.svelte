<script lang='ts'>
  /* eslint-disable style/max-statements-per-line */
  import type { TenantResource } from '$lib/entities/management/model.js'
  import { browser } from '$app/environment'
  import { goto } from '$app/navigation'
  import { resolve } from '$app/paths'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { management } from '$lib/entities/management/management.svelte.js'
  import { session } from '$lib/entities/session/session.svelte.js'
  import { localeState } from '$lib/i18n/locale.svelte.js'
  import { m } from '$lib/paraglide/messages.js'
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
    try {
      const tenant = await managementGateway.createTenant({
        name,
        slug,
        timezone: browser ? Intl.DateTimeFormat().resolvedOptions().timeZone : 'UTC',
        locale: localeState.current,
        adminAssignment: { mode: 'SELF' },
      })
      await session.switchTenant(tenant.id)
      management.invalidateTenant()
      await goto(resolve('/onboarding?step=members'))
    }
    catch (reason) { error = reason as Error }
    finally { creating = false }
  }
  $effect(() => { void load() })
</script>

<div class='space-y-8'>
  <PageHeader title={m.management_tenants()} description={m.management_tenants_description()} />
  {#if management.has('INSTANCE', 'tenant.create')}
    <Card><CardHeader><CardTitle>{m.management_create_tenant()}</CardTitle></CardHeader><CardContent><form class='grid gap-4 md:grid-cols-[1fr_1fr_auto]' onsubmit={create}><div><Label for='tenant-name'>{m.management_name()}</Label><Input id='tenant-name' bind:value={name} required /></div><div><Label for='tenant-slug'>{m.management_slug()}</Label><Input id='tenant-slug' bind:value={slug} pattern={'[a-z][a-z0-9-]{1,48}'} required /></div><Button class='self-end' disabled={creating}>{creating ? m.management_creating() : m.management_create()}</Button></form></CardContent></Card>
  {/if}
  {#if error}<p class='text-sm text-destructive'>{error.message}</p>{/if}
  {#if loading}<LoadingState label={m.management_loading_tenants()} />{:else}
    <div class='overflow-hidden rounded-lg border bg-background'><table class='w-full text-left text-sm'><thead><tr class='border-b bg-muted/40 text-muted-foreground'><th class='px-4 py-3'>{m.management_tenant()}</th><th>{m.management_status()}</th><th>{m.management_timezone()}</th><th>{m.management_locale()}</th><th></th></tr></thead><tbody>{#each tenants as tenant (tenant.id)}<tr class='border-b'><td class='px-4 py-3'><p class='font-medium'>{tenant.name}</p><p class='text-xs text-muted-foreground'>{tenant.slug}</p></td><td><Badge variant='secondary'>{tenant.status}</Badge></td><td>{tenant.timezone}</td><td>{tenant.locale}</td><td class='pr-4 text-right'><a class='text-sm font-medium text-primary hover:underline' href={resolve(`/manage/instance/tenants/${tenant.id}`)}>{m.management_manage()}</a></td></tr>{/each}</tbody></table></div>
  {/if}
</div>
