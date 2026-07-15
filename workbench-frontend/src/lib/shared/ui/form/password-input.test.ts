import { fireEvent, render, screen } from '@testing-library/svelte'
import { describe, expect, it } from 'vitest'
import PasswordInput from './password-input.svelte'

describe('passwordInput', () => {
  it('toggles password visibility with an accessible control', async () => {
    render(PasswordInput, { id: 'password', value: 'secret-password' })
    const input = screen.getByDisplayValue('secret-password')
    expect(input.getAttribute('type')).toBe('password')

    await fireEvent.click(screen.getByRole('button', { name: 'Show password' }))
    expect(input.getAttribute('type')).toBe('text')
    expect(screen.getByRole('button', { name: 'Hide password' }).getAttribute('aria-pressed')).toBe('true')
  })
})
