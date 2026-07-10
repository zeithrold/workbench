<script module lang='ts'>
  import { Input } from '$lib/shared/ui'
  import { defineMeta } from '@storybook/addon-svelte-csf'

  const { Story } = defineMeta({ title: 'Shared/UI/Input', component: Input })
</script>

<Story name='Default' args={{ 'placeholder': 'Project name', 'aria-label': 'Project name' }} />
<Story name='Disabled' args={{ 'value': 'Workbench', 'disabled': true, 'aria-label': 'Project name' }} />
<Story name='Invalid' args={{ 'aria-invalid': true, 'value': 'Invalid identifier', 'aria-label': 'Project name' }} />
