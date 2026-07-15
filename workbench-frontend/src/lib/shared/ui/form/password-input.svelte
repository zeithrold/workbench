<script lang='ts'>
  import type { HTMLInputAttributes } from 'svelte/elements'
  import { Button } from '$lib/components/ui/button'
  import { Input } from '$lib/components/ui/input'
  import { m } from '$lib/paraglide/messages.js'
  import EyeIcon from '@lucide/svelte/icons/eye'
  import EyeOffIcon from '@lucide/svelte/icons/eye-off'

  type Props = {
    value?: string
    id: string
    name?: string
    autocomplete?: HTMLInputAttributes['autocomplete']
    required?: boolean
    minlength?: number
    maxlength?: number
    disabled?: boolean
    placeholder?: string
  } & {
    'aria-describedby'?: string
    'aria-invalid'?: boolean
  }

  let {
    value = $bindable(''),
    id,
    name,
    autocomplete,
    required = false,
    minlength,
    maxlength,
    disabled = false,
    placeholder,
    'aria-describedby': ariaDescribedby,
    'aria-invalid': ariaInvalid,
  }: Props = $props()

  let visible = $state(false)
</script>

<div class='relative'>
  <Input
    {id}
    {name}
    type={visible ? 'text' : 'password'}
    bind:value
    {autocomplete}
    {required}
    {minlength}
    {maxlength}
    {disabled}
    {placeholder}
    aria-describedby={ariaDescribedby}
    aria-invalid={ariaInvalid}
    class='pr-10'
  />
  <Button
    type='button'
    variant='ghost'
    size='icon-sm'
    class='absolute top-0.5 right-0.5'
    aria-label={visible ? m.hide_password() : m.show_password()}
    aria-pressed={visible}
    onclick={() => visible = !visible}
    {disabled}
  >
    {#if visible}<EyeOffIcon />{:else}<EyeIcon />{/if}
  </Button>
</div>
