<script lang='ts'>
  /* eslint-disable style/max-statements-per-line */
  import type { InstanceOperations } from '$lib/entities/management/model.js'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { Badge, Card, CardContent, CardHeader, CardTitle, LoadingState, PageHeader } from '$lib/shared/ui'

  let snapshot = $state<InstanceOperations | null>(null)
  let error = $state<Error | null>(null)
  $effect(() => { void managementGateway.operations().then(value => snapshot = value).catch(reason => error = reason as Error) })
  const number = new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 })
</script>

<div class='space-y-8'>
  <PageHeader title='Operations' description='Read-only infrastructure, runtime, and delivery telemetry.' />
  {#if error}<p class='text-sm text-destructive'>{error.message}</p>
  {:else if !snapshot}<LoadingState label='Loading operations' />
  {:else}
    <section class='grid gap-4 md:grid-cols-2 xl:grid-cols-4'>
      {#each snapshot.components as component (component.code)}
        <Card><CardHeader><CardTitle class='flex items-center justify-between text-base'>{component.name}<Badge variant={component.status === 'UP' ? 'default' : 'secondary'}>{component.status}</Badge></CardTitle></CardHeader><CardContent class='text-xs text-muted-foreground'>{component.connection}</CardContent></Card>
      {/each}
    </section>
    <section><h2 class='mb-3 text-lg font-semibold'>Runtime metrics</h2><div class='grid gap-3 md:grid-cols-2 xl:grid-cols-3'>
      {#each Object.entries(snapshot.metrics) as [name, value] (name)}
        <div class='rounded-lg border bg-background p-4'><p class='text-xs text-muted-foreground'>{name}</p><p class='mt-1 text-2xl font-semibold'>{number.format(value)}</p></div>
      {/each}
    </div></section>
    <section><h2 class='mb-3 text-lg font-semibold'>Delivery state</h2><div class='grid gap-3 md:grid-cols-3 xl:grid-cols-5'>
      {#each Object.entries(snapshot.deliveries) as [status, count] (status)}
        <div class='rounded-lg border bg-background p-4'><p class='text-xs text-muted-foreground'>{status}</p><p class='mt-1 text-2xl font-semibold'>{count}</p></div>
      {/each}
    </div></section>
    <Card><CardHeader><CardTitle>Last 24 hours</CardTitle></CardHeader><CardContent>
      {#if snapshot.deliveryTrend.length === 0}<p class='text-sm text-muted-foreground'>No delivery activity.</p>{:else}
        <div class='space-y-2'>{#each snapshot.deliveryTrend as point (point.bucketAt)}<div class='grid grid-cols-[10rem_1fr_1fr] gap-3 text-sm'><span>{new Date(point.bucketAt).toLocaleString()}</span><span class='text-emerald-600'>{point.succeeded} succeeded</span><span class='text-destructive'>{point.failed} failed</span></div>{/each}</div>
      {/if}
    </CardContent></Card>
  {/if}
</div>
