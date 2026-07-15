import SignInForm from '$lib/features/auth/sign-in-form.svelte'
import { fireEvent, render, screen, waitFor } from '@testing-library/svelte'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { discoverLogin, goto, signIn } = vi.hoisted(() => ({
  discoverLogin: vi.fn(),
  goto: vi.fn(async () => undefined),
  signIn: vi.fn(async () => undefined),
}))

vi.mock('./login-discovery.js', () => ({ discoverLogin }))
vi.mock('$lib/entities/session/session.svelte.js', () => ({
  session: { pending: false, signIn },
}))
vi.mock('$app/navigation', () => ({ goto }))
vi.mock('$app/paths', () => ({ resolve: (path: string) => path }))

describe('signInForm', () => {
  beforeEach(() => vi.clearAllMocks())

  it('discovers and signs in an instance administrator', async () => {
    discoverLogin.mockResolvedValue({
      identifierRecognized: true,
      flow: 'INSTANCE_ONLY',
      instancePasswordMethod: { id: 'lmg_1', code: 'instance_password', kind: 'PASSWORD', name: 'Workbench Admin' },
    })
    render(SignInForm)

    await fireEvent.input(screen.getByLabelText(/Email address/), { target: { value: 'admin@example.test' } })
    await fireEvent.click(screen.getByRole('button', { name: 'Continue' }))
    await screen.findByLabelText(/Password/)
    await fireEvent.input(screen.getByLabelText(/Password/), { target: { value: 'secure-password-1' } })
    await fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    await waitFor(() => expect(signIn).toHaveBeenCalledWith({
      email: 'admin@example.test',
      password: 'secure-password-1',
      loginMethodId: 'lmg_1',
    }))
    expect(goto).toHaveBeenCalledWith('/setup/complete')
  })

  it('explains that tenant sign-in is outside this milestone', async () => {
    discoverLogin.mockResolvedValue({
      identifierRecognized: true,
      flow: 'TENANT',
      instancePasswordMethod: null,
    })
    render(SignInForm)
    await fireEvent.input(screen.getByLabelText(/Email address/), { target: { value: 'member@example.test' } })
    await fireEvent.click(screen.getByRole('button', { name: 'Continue' }))
    expect((await screen.findByRole('alert')).textContent).toContain('requires tenant sign-in')
  })
})
