<script lang='ts'>
  import type { PermissionPolicy } from '$lib/entities/management/model.js'
  import { resolve } from '$app/paths'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { management } from '$lib/entities/management/management.svelte.js'
  import { Badge, Button, Card, CardContent, LoadingState } from '$lib/shared/ui'

  let policies = $state<PermissionPolicy[]>([])
  let loading = $state(true)
  let error = $state<Error | null>(null)
  const canEdit = $derived(management.has('TENANT', 'permission.policy.manage'))

  $effect(() => {
    void managementGateway.policies()
      .then(value => policies = value)
      .catch(reason => error = reason as Error)
      .finally(() => loading = false)
  })
</script>

<div class='space-y-4'>
  <div class='flex items-center justify-between'><div><h2 class='text-xl font-semibold'>Policies</h2><p class='text-sm text-muted-foreground'>Built-in policies are read only and can be copied.</p></div>{#if canEdit}<Button href={resolve('/manage/tenant/access/policies/new')}>New policy</Button>{/if}</div>
  {#if error}<p class='text-sm text-destructive'>{error.message}</p>{/if}
  {#if loading}<LoadingState label='Loading permission policies' />{:else}<div class='grid gap-3 md:grid-cols-2'>{#each policies as policy (policy.id)}<a href={resolve(`/manage/tenant/access/policies/${policy.id}`)}><Card class='h-full transition-colors hover:border-foreground/30'><CardContent class='flex items-start justify-between gap-3 p-5'><div><p class='font-medium'>{policy.name}</p><p class='text-xs text-muted-foreground'>{policy.code} · {policy.rules.length} rules</p><p class='mt-2 text-sm text-muted-foreground'>{policy.description ?? 'No description'}</p></div>{#if policy.builtin}<Badge variant='secondary'>Built in</Badge>{/if}</CardContent></Card></a>{/each}</div>{/if}
</div>
