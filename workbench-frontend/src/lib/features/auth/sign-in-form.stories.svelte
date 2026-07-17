<script module lang='ts'>
  import { defineMeta } from '@storybook/addon-svelte-csf'
  import SignInForm from './sign-in-form.svelte'

  const { Story } = defineMeta({
    title: 'Features/Auth/Sign in form',
    component: SignInForm,
  })
</script>

<Story name='Email entry' asChild>
  <div class='mx-auto w-full max-w-md rounded-xl border bg-background p-6 shadow-sm'>
    <SignInForm />
  </div>
</Story>
