<script lang='ts'>
  import { m } from '$lib/paraglide/messages.js'
  import * as Select from './index.js'

  const { custom = false, disabled = false }: { custom?: boolean, disabled?: boolean } = $props()
  const items = [
    { value: 'eq', label: m.permission_operator_eq() },
    { value: 'neq', label: m.permission_operator_neq() },
  ]
  let value = $state('eq')
</script>

<Select.Root type='single' {items} bind:value {disabled}>
  <Select.Trigger>
    {#if custom}
      <Select.Value><span>{m.permission_value()}</span></Select.Value>
    {:else}
      <Select.Value placeholder={m.permission_operator()} />
    {/if}
  </Select.Trigger>
  <Select.Content>
    <Select.Item value='eq' label={m.permission_operator_eq()}>{m.permission_operator_eq()}</Select.Item>
    <Select.Item value='neq' label={m.permission_operator_neq()}>{m.permission_operator_neq()}</Select.Item>
  </Select.Content>
</Select.Root>
