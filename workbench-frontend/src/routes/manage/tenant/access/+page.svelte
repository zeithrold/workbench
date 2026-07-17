<script lang='ts'>
  import type { PermissionPolicy } from '$lib/entities/management/model.js'
  import { resolve } from '$app/paths'
  import { isApiProblemStatus } from '$lib/api/problem.js'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { m } from '$lib/paraglide/messages.js'
  import { AccessDeniedState, Alert, Badge, Button, Card, CardContent, LoadingState } from '$lib/shared/ui'

  let policies = $state<PermissionPolicy[]>([])
  let loading = $state(true)
  let error = $state<Error | null>(null)

  $effect(() => {
    void managementGateway.policies()
      .then(value => policies = value)
      .catch(reason => error = reason as Error)
      .finally(() => loading = false)
  })
</script>

<div class='space-y-4'>
  <div class='flex items-center justify-between'><div><h2 class='text-xl font-semibold'>{m.management_policies()}</h2><p class='text-sm text-muted-foreground'>{m.management_policies_description()}</p></div><Button href={resolve('/manage/tenant/access/policies/new')}>{m.management_new_policy()}</Button></div>
  {#if loading}<LoadingState label={m.management_loading_permission_policies()} />
  {:else if error && isApiProblemStatus(error, 403)}<AccessDeniedState description={error.message} />
  {:else if error}<Alert variant='destructive'>{error.message}</Alert>
  {:else}<div class='grid gap-3 md:grid-cols-2'>{#each policies as policy (policy.id)}<a href={resolve(`/manage/tenant/access/policies/${policy.id}`)}><Card class='h-full transition-colors hover:border-foreground/30'><CardContent class='flex items-start justify-between gap-3 p-5'><div><p class='font-medium'>{policy.name}</p><p class='text-xs text-muted-foreground'>{policy.code} · {m.management_policy_rule_count({ count: policy.rules.length })}</p><p class='mt-2 text-sm text-muted-foreground'>{policy.description ?? m.management_no_description()}</p></div>{#if policy.builtin}<Badge variant='secondary'>{m.management_built_in()}</Badge>{/if}</CardContent></Card></a>{/each}</div>{/if}
</div>
