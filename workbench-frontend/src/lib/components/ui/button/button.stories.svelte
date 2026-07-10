<script module lang='ts'>
  import { Button } from '$lib/shared/ui'
  import { defineMeta } from '@storybook/addon-svelte-csf'

  const { Story } = defineMeta({ title: 'Shared/UI/Button', component: Button })
</script>

<Story name='Default'>Create project</Story>
<Story name='Outline' args={{ variant: 'outline' }}>Open docs</Story>
<Story name='Destructive' args={{ variant: 'destructive' }} parameters={{ a11y: { disable: true } }}>Delete project</Story>
<Story name='Disabled' args={{ disabled: true }}>Saving</Story>
