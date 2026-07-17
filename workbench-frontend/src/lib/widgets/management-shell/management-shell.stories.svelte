<script module lang='ts'>
  import { defineMeta } from '@storybook/addon-svelte-csf'
  import ManagementShell from './management-shell.svelte'

  const { Story } = defineMeta({
    title: 'Widgets/ManagementShell',
    component: ManagementShell,
    parameters: { layout: 'fullscreen' },
  })
</script>

<script lang='ts'>
  import { managementNavigation } from '$lib/entities/management/management-navigation.svelte.js'
  import { session } from '$lib/entities/session/session.svelte.js'

  session.current = {
    user: { id: 'usr_demo', displayName: 'Alex', primaryEmail: 'alex@example.com' },
    activeTenant: { id: 'northstar', name: 'Northstar Studio', slug: 'northstar' },
    tenants: [{ id: 'northstar', name: 'Northstar Studio', slug: 'northstar' }],
    sessionExpiresAt: '2099-01-01T00:00:00Z',
    adminScopes: [],
    localeContext: { userPreference: null, tenantDefault: 'en-US' },
  }
  managementNavigation.accept({
    tenantContextStatus: 'ACTIVE',
    items: [
      { id: 'MANAGEMENT_INSTANCE_OVERVIEW' },
      { id: 'MANAGEMENT_INSTANCE_TENANTS' },
      { id: 'MANAGEMENT_TENANT_SETTINGS' },
      { id: 'MANAGEMENT_TENANT_MEMBERS' },
    ],
  }, 'northstar')
</script>

<Story name='Signed in' asChild>
  <ManagementShell>
    <div class='space-y-2 rounded-xl border bg-background p-6 shadow-sm'>
      <h1 class='text-2xl font-semibold'>Management overview</h1>
      <p class='text-muted-foreground'>Review instance health and tenant administration.</p>
    </div>
  </ManagementShell>
</Story>
