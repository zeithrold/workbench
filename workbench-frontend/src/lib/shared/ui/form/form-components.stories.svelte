<script module lang='ts'>
  import { defineMeta } from '@storybook/addon-svelte-csf'
  import FormComponentsStoryHost from './form-components-story-host.svelte'

  const { Story } = defineMeta({ title: 'Shared/UI/Form components', component: FormComponentsStoryHost })
</script>

<Story name='Default' />
<Story name='Validation error' args={{ variant: 'error' }} />
