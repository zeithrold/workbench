import { describe, expect, it } from 'vitest'

import { buttonVariants } from './button.svelte'

describe('button', () => {
  it('exposes primary variant classes', () => {
    expect(buttonVariants({ variant: 'default' })).toContain('bg-primary')
  })

  it('exposes outline variant classes', () => {
    expect(buttonVariants({ variant: 'outline' })).toContain('border-border')
  })
})
