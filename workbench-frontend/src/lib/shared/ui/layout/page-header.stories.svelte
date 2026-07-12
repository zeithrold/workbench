<script module lang='ts'>
  import { defineMeta } from '@storybook/addon-svelte-csf'
  import PageHeader from './page-header.svelte'

  const { Story } = defineMeta({ title: 'Shared/UI/PageHeader', component: PageHeader })
</script>

<Story name='Default' args={{ title: 'Projects', description: 'Plan and track work across your team.' }} />
<Story name='Title only' args={{ title: 'Settings' }} />
