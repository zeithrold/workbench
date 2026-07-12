<script module lang='ts'>
  import { defineMeta } from '@storybook/addon-svelte-csf'
  import BrandLogo from './brand-logo.svelte'

  const { Story } = defineMeta({ title: 'Shared/UI/BrandLogo', component: BrandLogo })
</script>

<Story name='Default' />
