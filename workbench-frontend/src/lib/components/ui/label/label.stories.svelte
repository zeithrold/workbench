<script module lang='ts'>
  import { Label } from '$lib/shared/ui'
  import { defineMeta } from '@storybook/addon-svelte-csf'

  const { Story } = defineMeta({ title: 'Shared/UI/Label', component: Label })
</script>

<Story name='Default'>Project name</Story>
<Story name='Required'>Email address *</Story>
