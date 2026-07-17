<script module lang='ts'>
  import { defineMeta } from '@storybook/addon-svelte-csf'
  import SelectorLoading from '../selection/selector-loading.svelte'
  import LoadingState from './loading-state.svelte'

  const { Story } = defineMeta({ title: 'Shared/UI/LoadingState', component: LoadingState })
</script>

<Story name='Default' />
<Story name='Custom label' args={{ label: 'Loading projects' }} />
<Story name='Selector options' asChild>
  <div class='w-80 rounded-md border bg-popover p-1'>
    <SelectorLoading />
  </div>
</Story>
