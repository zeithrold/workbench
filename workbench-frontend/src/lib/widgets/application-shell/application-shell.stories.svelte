<script module lang='ts'>
  import { defineMeta } from '@storybook/addon-svelte-csf'
  import ApplicationShell from './application-shell.svelte'

  const { Story } = defineMeta({ title: 'Widgets/ApplicationShell', component: ApplicationShell })
</script>

<script lang='ts'>
  import { session } from '$lib/entities/session/session.svelte.js'

  session.current = {
    user: { name: 'Alex', email: 'alex@example.com' },
    activeTenant: { id: 'northstar', name: 'Northstar Studio', slug: 'northstar' },
    tenants: [
      { id: 'northstar', name: 'Northstar Studio', slug: 'northstar' },
      { id: 'workbench', name: 'Workbench Labs', slug: 'workbench' },
    ],
  }
</script>

<Story name='Signed in' asChild>
  <ApplicationShell>
    <div class='space-y-2'>
      <h1 class='text-2xl font-semibold'>Overview</h1>
      <p class='text-muted-foreground'>Your workspace is ready for its first project.</p>
    </div>
  </ApplicationShell>
</Story>
