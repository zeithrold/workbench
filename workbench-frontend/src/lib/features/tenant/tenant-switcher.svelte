<script lang='ts'>
  import { session } from '$lib/entities/session/session.svelte.js'

  async function changeTenant(event: Event) {
    await session.switchTenant((event.currentTarget as HTMLSelectElement).value)
  }
</script>

{#if session.current}
  <label class='grid gap-1 text-xs font-medium text-muted-foreground' for='tenant-switcher'>
    Active tenant
    <select
      id='tenant-switcher'
      class='h-9 rounded-md border bg-background px-2 text-sm text-foreground shadow-xs'
      value={session.current.activeTenant.id}
      onchange={event => void changeTenant(event)}
      disabled={session.pending}
    >
      {#each session.current.tenants as tenant (tenant.id)}
        <option value={tenant.id}>{tenant.name}</option>
      {/each}
    </select>
  </label>
{/if}
