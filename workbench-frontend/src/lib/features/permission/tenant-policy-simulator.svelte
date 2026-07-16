<script lang='ts'>
  import type { TenantPolicySimulation } from '$lib/entities/management/model.js'
  import type { PermissionPolicyDocument } from './permission-document.js'
  import type { TenantPermissionActionOption } from './tenant-permission-document.js'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { m } from '$lib/paraglide/messages.js'
  import { Alert, Badge, Button, Card, CardContent, CardDescription, CardHeader, CardTitle, Label, SearchableSelect } from '$lib/shared/ui'

  const { document, actions }: {
    document: PermissionPolicyDocument
    actions: TenantPermissionActionOption[]
  } = $props()

  let action = $state('')
  let result = $state<TenantPolicySimulation | null>(null)
  let error = $state<Error | null>(null)
  let running = $state(false)
  const options = $derived(actions.map(item => ({
    id: item.code,
    label: item.name,
    description: item.description ?? item.code,
  })))

  async function simulate() {
    if (!action)
      return
    running = true
    error = null
    try {
      result = await managementGateway.simulatePolicy({
        schemaVersion: 1,
        rules: document.rules,
        action,
      })
    }
    catch (reason) {
      result = null
      error = reason as Error
    }
    finally {
      running = false
    }
  }
</script>

<Card>
  <CardHeader>
    <CardTitle>{m.permission_simulator_title()}</CardTitle>
    <CardDescription>{m.permission_simulator_description()}</CardDescription>
  </CardHeader>
  <CardContent class='space-y-4'>
    <div class='grid items-end gap-3 md:grid-cols-[minmax(0,1fr)_auto]'>
      <div class='space-y-1.5'><Label>{m.permission_simulator_capability()}</Label><SearchableSelect value={action} {options} placeholder={m.permission_simulator_select_capability()} clearable={false} onValueChange={value => action = value ?? ''} /></div>
      <Button type='button' disabled={!action || running || document.rules.length === 0} onclick={simulate}>{running ? m.permission_simulator_testing() : m.permission_simulator_test_draft()}</Button>
    </div>
    {#if error}<Alert variant='destructive'>{error.message}</Alert>{/if}
    {#if result}
      <div class='rounded-lg border p-4'>
        <div class='flex items-center gap-2'><Badge variant={result.decision === 'DENY' ? 'destructive' : 'secondary'}>{result.decision === 'DENY' ? m.permission_simulator_denied() : m.permission_simulator_allowed()}</Badge><span class='text-sm text-muted-foreground'>{result.reason === 'matching_deny' ? m.permission_simulator_matching_deny() : result.reason === 'matching_allow' ? m.permission_simulator_matching_allow() : m.permission_simulator_no_allow()}</span></div>
        <ol class='mt-3 space-y-2 text-sm'>
          {#each result.rules as rule (rule.index)}
            <li class='flex items-center justify-between gap-3 rounded-md bg-muted/40 px-3 py-2'><span>{m.permission_simulator_rule({ number: rule.index + 1, action: actions.find(item => item.code === rule.action)?.name ?? rule.action })}</span><span class='text-muted-foreground'>{rule.matches ? m.permission_simulator_effect_match({ effect: rule.effect === 'DENY' ? m.permission_simulator_deny() : m.permission_simulator_allow() }) : m.permission_simulator_no_match()}</span></li>
          {/each}
        </ol>
      </div>
    {/if}
  </CardContent>
</Card>
