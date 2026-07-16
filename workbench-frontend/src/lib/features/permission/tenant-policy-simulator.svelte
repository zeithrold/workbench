<script lang='ts'>
  import type { TenantPolicySimulation } from '$lib/entities/management/model.js'
  import type { PermissionPolicyDocument } from './permission-document.js'
  import type { TenantPermissionActionOption } from './tenant-permission-document.js'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
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
    <CardTitle>Test this draft</CardTitle>
    <CardDescription>Check whether the current tenant policy allows or denies one available capability. This does not save the policy.</CardDescription>
  </CardHeader>
  <CardContent class='space-y-4'>
    <div class='grid items-end gap-3 md:grid-cols-[minmax(0,1fr)_auto]'>
      <div class='space-y-1.5'><Label>Capability</Label><SearchableSelect value={action} {options} placeholder='Select a tenant capability' clearable={false} onValueChange={value => action = value ?? ''} /></div>
      <Button type='button' disabled={!action || running || document.rules.length === 0} onclick={simulate}>{running ? 'Testing...' : 'Test draft'}</Button>
    </div>
    {#if error}<Alert variant='destructive'>{error.message}</Alert>{/if}
    {#if result}
      <div class='rounded-lg border p-4'>
        <div class='flex items-center gap-2'><Badge variant={result.decision === 'DENY' ? 'destructive' : 'secondary'}>{result.decision === 'DENY' ? 'Denied' : 'Allowed'}</Badge><span class='text-sm text-muted-foreground'>{result.reason === 'matching_deny' ? 'A matching deny rule takes precedence.' : result.reason === 'matching_allow' ? 'At least one allow rule matches.' : 'No allow rule matches this capability.'}</span></div>
        <ol class='mt-3 space-y-2 text-sm'>
          {#each result.rules as rule (rule.index)}
            <li class='flex items-center justify-between gap-3 rounded-md bg-muted/40 px-3 py-2'><span>Rule {rule.index + 1}: {actions.find(item => item.code === rule.action)?.name ?? rule.action}</span><span class='text-muted-foreground'>{rule.matches ? `${rule.effect === 'DENY' ? 'Deny' : 'Allow'} match` : 'No match'}</span></li>
          {/each}
        </ol>
      </div>
    {/if}
  </CardContent>
</Card>
