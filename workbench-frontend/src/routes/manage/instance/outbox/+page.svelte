<script lang='ts'>
  /* eslint-disable style/max-statements-per-line */
  import type { OutboxDelivery, OutboxMessage } from '$lib/entities/management/model.js'
  import { isApiProblemStatus } from '$lib/api/problem.js'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { m } from '$lib/paraglide/messages.js'
  import { AccessDeniedState, Alert, Badge, Button, Card, CardContent, CardHeader, CardTitle, LoadingState, PageHeader } from '$lib/shared/ui'

  let messages = $state<OutboxMessage[]>([])
  let deliveries = $state<OutboxDelivery[]>([])
  let loading = $state(true)
  let loaded = $state(false)
  let error = $state<Error | null>(null)

  async function load() {
    loading = true
    try { [messages, deliveries] = await Promise.all([managementGateway.outboxMessages(), managementGateway.outboxDeliveries()]); loaded = true }
    catch (reason) { error = reason as Error }
    finally { loading = false }
  }
  async function replay(delivery: OutboxDelivery) {
    error = null
    try { await managementGateway.replayDelivery(delivery.outboxId, delivery.consumerName); await load() }
    catch (reason) { error = reason as Error }
  }
  $effect(() => { void load() })
</script>

<div class='space-y-8'>
  <PageHeader title={m.management_outbox()} description={m.management_outbox_description()} />
  {#if !loaded && error && isApiProblemStatus(error, 403)}<AccessDeniedState description={error.message} />
  {:else}
    {#if error}<Alert variant='destructive'>{error.message}</Alert>{/if}
    {#if loading}<LoadingState label={m.management_loading_outbox()} />{:else}
      <Card><CardHeader><CardTitle>{m.management_deliveries()}</CardTitle></CardHeader><CardContent class='overflow-x-auto'>
        <table class='w-full text-left text-sm'><thead><tr class='border-b text-muted-foreground'><th class='py-3'>{m.management_consumer()}</th><th>{m.management_status()}</th><th>{m.management_attempts()}</th><th>{m.management_error()}</th><th></th></tr></thead><tbody>
          {#each deliveries as delivery (`${delivery.outboxId}:${delivery.consumerName}`)}
            <tr class='border-b'><td class='py-3'>{delivery.consumerName}</td><td><Badge variant={delivery.status === 'DEAD' ? 'destructive' : 'secondary'}>{delivery.status}</Badge></td><td>{delivery.attempts}</td><td class='max-w-sm truncate'>{delivery.lastError ?? '—'}</td><td class='text-right'>{#if delivery.status === 'DEAD'}<Button size='sm' variant='outline' onclick={() => replay(delivery)}>{m.management_replay()}</Button>{/if}</td></tr>
          {/each}
        </tbody></table>
      </CardContent></Card>
      <Card><CardHeader><CardTitle>{m.management_messages()}</CardTitle></CardHeader><CardContent class='overflow-x-auto'>
        <table class='w-full text-left text-sm'><thead><tr class='border-b text-muted-foreground'><th class='py-3'>{m.management_event()}</th><th>{m.management_topic()}</th><th>{m.management_tenant()}</th><th>{m.management_created()}</th></tr></thead><tbody>{#each messages as message (message.id)}<tr class='border-b'><td class='py-3'><p class='font-medium'>{message.eventType}</p><p class='text-xs text-muted-foreground'>{message.eventId}</p></td><td>{message.topic}</td><td>{message.tenantId ?? m.management_instance()}</td><td>{new Date(message.createdAt).toLocaleString()}</td></tr>{/each}</tbody></table>
      </CardContent></Card>
    {/if}
  {/if}
</div>
