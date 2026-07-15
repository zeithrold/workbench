<script lang='ts'>
  import type { InstanceOperations } from '$lib/entities/management/model.js'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { Badge, Card, CardContent, CardHeader, CardTitle, LoadingState, PageHeader } from '$lib/shared/ui'

  let snapshot = $state<InstanceOperations | null>(null)
  let error = $state<Error | null>(null)

  $effect(() => {
    void managementGateway.operations().then(value => snapshot = value).catch(reason => error = reason as Error)
  })
</script>

<svelte:head><title>Instance management · Workbench</title></svelte:head>
<div class='space-y-8'>
  <PageHeader title='Instance overview' description='Identity, runtime, messaging, and current health.' />
  {#if error}<p class='text-sm text-destructive'>{error.message}</p>
  {:else if !snapshot}<LoadingState label='Loading instance overview' />
  {:else}
    <div class='grid gap-4 md:grid-cols-2 xl:grid-cols-4'>
      <Card><CardHeader><CardTitle class='text-sm'>Instance</CardTitle></CardHeader><CardContent><p class='font-medium'>{snapshot.instance.name}</p><p class='text-xs text-muted-foreground'>{snapshot.instance.id}</p></CardContent></Card>
      <Card><CardHeader><CardTitle class='text-sm'>Health</CardTitle></CardHeader><CardContent><Badge variant={snapshot.status === 'UP' ? 'default' : 'secondary'}>{snapshot.status}</Badge></CardContent></Card>
      <Card><CardHeader><CardTitle class='text-sm'>Version</CardTitle></CardHeader><CardContent><p>{snapshot.version ?? 'Development build'}</p><p class='text-xs text-muted-foreground'>API {snapshot.apiVersion}</p></CardContent></Card>
      <Card><CardHeader><CardTitle class='text-sm'>Messaging</CardTitle></CardHeader><CardContent><p>{snapshot.messagingTransport}</p><p class='text-xs text-muted-foreground'>{Math.floor(snapshot.uptimeSeconds / 3600)}h uptime</p></CardContent></Card>
    </div>
    <Card><CardHeader><CardTitle>Infrastructure</CardTitle></CardHeader><CardContent class='grid gap-3 md:grid-cols-2'>
      {#each snapshot.components as component (component.code)}
        <div class='flex items-center justify-between rounded-lg border p-4'><div><p class='font-medium'>{component.name}</p><p class='text-xs text-muted-foreground'>{component.connection}</p></div><Badge variant={component.status === 'UP' ? 'default' : 'secondary'}>{component.status}</Badge></div>
      {/each}
    </CardContent></Card>
  {/if}
</div>
