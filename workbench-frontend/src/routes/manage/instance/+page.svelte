<script lang='ts'>
  import type { InstanceOperations } from '$lib/entities/management/model.js'
  import { isApiProblemStatus } from '$lib/api/problem.js'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { m } from '$lib/paraglide/messages.js'
  import { AccessDeniedState, Alert, Badge, Card, CardContent, CardHeader, CardTitle, LoadingState, PageHeader } from '$lib/shared/ui'

  let snapshot = $state<InstanceOperations | null>(null)
  let error = $state<Error | null>(null)

  $effect(() => {
    void managementGateway.operations().then(value => snapshot = value).catch(reason => error = reason as Error)
  })
</script>

<svelte:head><title>{m.management_instance_title()}</title></svelte:head>
<div class='space-y-8'>
  <PageHeader title={m.management_instance_overview()} description={m.management_instance_overview_description()} />
  {#if error && isApiProblemStatus(error, 403)}<AccessDeniedState description={error.message} />
  {:else if error}<Alert variant='destructive'>{error.message}</Alert>
  {:else if !snapshot}<LoadingState label={m.management_loading_instance_overview()} />
  {:else}
    <div class='grid gap-4 md:grid-cols-2 xl:grid-cols-4'>
      <Card><CardHeader><CardTitle class='text-sm'>{m.management_instance()}</CardTitle></CardHeader><CardContent><p class='font-medium'>{snapshot.instance.name}</p><p class='text-xs text-muted-foreground'>{snapshot.instance.id}</p></CardContent></Card>
      <Card><CardHeader><CardTitle class='text-sm'>{m.management_health()}</CardTitle></CardHeader><CardContent><Badge variant={snapshot.status === 'UP' ? 'default' : 'secondary'}>{snapshot.status}</Badge></CardContent></Card>
      <Card><CardHeader><CardTitle class='text-sm'>{m.management_version()}</CardTitle></CardHeader><CardContent><p>{snapshot.version ?? m.management_development_build()}</p><p class='text-xs text-muted-foreground'>API {snapshot.apiVersion}</p></CardContent></Card>
      <Card><CardHeader><CardTitle class='text-sm'>{m.management_messaging()}</CardTitle></CardHeader><CardContent><p>{snapshot.messagingTransport}</p><p class='text-xs text-muted-foreground'>{m.management_uptime_hours({ hours: Math.floor(snapshot.uptimeSeconds / 3600) })}</p></CardContent></Card>
    </div>
    <Card><CardHeader><CardTitle>{m.management_infrastructure()}</CardTitle></CardHeader><CardContent class='grid gap-3 md:grid-cols-2'>
      {#each snapshot.components as component (component.code)}
        <div class='flex items-center justify-between rounded-lg border p-4'><div><p class='font-medium'>{component.name}</p><p class='text-xs text-muted-foreground'>{component.connection}</p></div><Badge variant={component.status === 'UP' ? 'default' : 'secondary'}>{component.status}</Badge></div>
      {/each}
    </CardContent></Card>
  {/if}
</div>
