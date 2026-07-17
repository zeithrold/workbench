<script module lang='ts'>
  import { defineMeta } from '@storybook/addon-svelte-csf'
  import InstanceSetupForm from './instance-setup-form.svelte'

  const { Story } = defineMeta({
    title: 'Features/Instance setup/Instance setup form',
    component: InstanceSetupForm,
  })
</script>

<script lang='ts'>
  import { instanceSetup } from '$lib/entities/instance-setup/instance-setup.svelte.js'

  instanceSetup.current = { initialized: false, setupTokenRequired: true }
</script>

<Story name='Setup token required' asChild>
  <div class='mx-auto w-full max-w-xl rounded-xl border bg-background p-6 shadow-sm'>
    <InstanceSetupForm />
  </div>
</Story>
