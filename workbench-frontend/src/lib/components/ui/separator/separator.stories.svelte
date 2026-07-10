<script module lang='ts'>
  import { Separator } from '$lib/shared/ui'
  import { defineMeta } from '@storybook/addon-svelte-csf'

  const { Story } = defineMeta({ title: 'Shared/UI/Separator', component: Separator })
</script>

<Story name='Horizontal' args={{ class: 'w-72' }} />
<Story name='Vertical' args={{ orientation: 'vertical', class: 'h-24' }} />
