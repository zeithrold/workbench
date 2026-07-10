<script module lang='ts'>
  import { defineMeta } from '@storybook/addon-svelte-csf'
  import EmptyState from './empty-state.svelte'

  const { Story } = defineMeta({ title: 'Shared/UI/EmptyState', component: EmptyState })
</script>

<Story name='Default' args={{ title: 'No projects yet', description: 'Create your first project to begin organizing work.' }} />
