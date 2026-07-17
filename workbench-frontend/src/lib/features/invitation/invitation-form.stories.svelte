<script module lang='ts'>
  import { defineMeta } from '@storybook/addon-svelte-csf'
  import InvitationForm from './invitation-form.svelte'

  const { Story } = defineMeta({
    title: 'Features/Invitation/Invitation form',
    component: InvitationForm,
  })
</script>

<Story name='New invitation' asChild>
  <div class='rounded-xl border bg-background p-6 shadow-sm'>
    <InvitationForm />
  </div>
</Story>
