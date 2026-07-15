<script lang='ts'>
  /* eslint-disable style/max-statements-per-line */
  import type { OutboxDelivery, OutboxMessage } from '$lib/entities/management/model.js'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { management } from '$lib/entities/management/management.svelte.js'
  import { Badge, Button, Card, CardContent, CardHeader, CardTitle, LoadingState, PageHeader } from '$lib/shared/ui'

  let messages = $state<OutboxMessage[]>([])
  let deliveries = $state<OutboxDelivery[]>([])
  let loading = $state(true)
  let error = $state<Error | null>(null)

  async function load() {
    loading = true
    try { [messages, deliveries] = await Promise.all([managementGateway.outboxMessages(), managementGateway.outboxDeliveries()]) }
    catch (reason) { error = reason as Error }
    finally { loading = false }
  }
  $effect(() => { void load() })
</script>

<div class='space-y-8'>
  <PageHeader title='Outbox' description='Domain messages, consumer deliveries, and dead-letter replay.' />
  {#if error}<p class='text-sm text-destructive'>{error.message}</p>{/if}
  {#if loading}<LoadingState label='Loading outbox' />{:else}
    <Card><CardHeader><CardTitle>Deliveries</CardTitle></CardHeader><CardContent class='overflow-x-auto'>
      <table class='w-full text-left text-sm'><thead><tr class='border-b text-muted-foreground'><th class='py-3'>Consumer</th><th>Status</th><th>Attempts</th><th>Error</th><th></th></tr></thead><tbody>
        {#each deliveries as delivery (`${delivery.outboxId}:${delivery.consumerName}`)}
          <tr class='border-b'><td class='py-3'>{delivery.consumerName}</td><td><Badge variant={delivery.status === 'DEAD' ? 'destructive' : 'secondary'}>{delivery.status}</Badge></td><td>{delivery.attempts}</td><td class='max-w-sm truncate'>{delivery.lastError ?? '—'}</td><td class='text-right'>{#if delivery.status === 'DEAD' && management.has('INSTANCE', 'outbox.manage')}<Button size='sm' variant='outline' onclick={async () => { await managementGateway.replayDelivery(delivery.outboxId, delivery.consumerName); await load() }}>Replay</Button>{/if}</td></tr>
        {/each}
      </tbody></table>
    </CardContent></Card>
    <Card><CardHeader><CardTitle>Messages</CardTitle></CardHeader><CardContent class='overflow-x-auto'>
      <table class='w-full text-left text-sm'><thead><tr class='border-b text-muted-foreground'><th class='py-3'>Event</th><th>Topic</th><th>Tenant</th><th>Created</th></tr></thead><tbody>{#each messages as message (message.id)}<tr class='border-b'><td class='py-3'><p class='font-medium'>{message.eventType}</p><p class='text-xs text-muted-foreground'>{message.eventId}</p></td><td>{message.topic}</td><td>{message.tenantId ?? 'Instance'}</td><td>{new Date(message.createdAt).toLocaleString()}</td></tr>{/each}</tbody></table>
    </CardContent></Card>
  {/if}
</div>
