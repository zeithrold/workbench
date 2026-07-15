<script lang='ts'>
  import { Alert, FormField, PasswordInput } from '$lib/shared/ui'

  const { variant = 'default' }: { variant?: 'default' | 'error' } = $props()
  let password = $state('')
</script>

<div class='w-96 space-y-4 p-4'>
  {#if variant === 'error'}<Alert variant='destructive'>The request could not be completed.</Alert>{/if}
  <FormField
    id='story-password'
    label='Password'
    description='Use at least 12 characters.'
    error={variant === 'error' ? 'Password is too short.' : undefined}
    required
  >
    {#snippet children(context)}
      <PasswordInput
        id='story-password'
        bind:value={password}
        aria-describedby={context.describedBy}
        aria-invalid={context.invalid}
      />
    {/snippet}
  </FormField>
</div>
