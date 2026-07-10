<script module lang='ts'>
  import { Badge } from '$lib/shared/ui'
  import { defineMeta } from '@storybook/addon-svelte-csf'

  const { Story } = defineMeta({ title: 'Shared/UI/Badge', component: Badge })
</script>

<Story name='Default'>Active</Story>
<Story name='Secondary' args={{ variant: 'secondary' }}>Draft</Story>
<Story name='Outline' args={{ variant: 'outline' }}>External</Story>
<Story name='Destructive' args={{ variant: 'destructive' }} parameters={{ a11y: { disable: true } }}>Blocked</Story>
