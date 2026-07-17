<script module lang='ts'>
  import { defineMeta } from '@storybook/addon-svelte-csf'
  import AccessDeniedState from './access-denied-state.svelte'
  import EmptyState from './empty-state.svelte'

  const { Story } = defineMeta({ title: 'Shared/UI/EmptyState', component: EmptyState })
</script>

<Story name='Default' args={{ title: 'No projects yet', description: 'Create your first project to begin organizing work.' }} />
<Story name='Access denied' asChild>
  <AccessDeniedState description='Ask a tenant administrator to grant access to project settings.' />
</Story>
