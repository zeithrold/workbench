import SignInForm from '$lib/features/auth/sign-in-form.svelte'
import { render, screen, waitFor } from '@testing-library/svelte'

import { describe, expect, it, vi } from 'vitest'

const { signIn, goto } = vi.hoisted(() => ({
  signIn: vi.fn(async () => undefined),
  goto: vi.fn(async () => undefined),
}))

vi.mock('$lib/entities/session/session.svelte.js', () => ({
  session: {
    get pending() {
      return false
    },
    signIn,
  },
}))

vi.mock('$app/navigation', () => ({
  goto,
}))

vi.mock('$app/paths', () => ({
  resolve: (path: string) => path,
}))

describe('signInForm', () => {
  it('submits the demo email through the session store', async () => {
    render(SignInForm)

    screen.getByRole('button', { name: 'Continue with demo account' }).click()

    expect(signIn).toHaveBeenCalledWith({ email: 'demo@workbench.local' })
    await waitFor(() => {
      expect(goto).toHaveBeenCalledWith('/')
    })
  })
})
