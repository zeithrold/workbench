<script module lang='ts'>
  import { defineMeta } from '@storybook/addon-svelte-csf'
  import LoadingState from './loading-state.svelte'

  const { Story } = defineMeta({ title: 'Shared/UI/LoadingState', component: LoadingState })
</script>

<Story name='Default' />
<Story name='Custom label' args={{ label: 'Loading projects' }} />
