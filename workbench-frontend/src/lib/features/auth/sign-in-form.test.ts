import SignInForm from '$lib/features/auth/sign-in-form.svelte'
import { fireEvent, render, screen, waitFor } from '@testing-library/svelte'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { discoverLogin, goto, signIn } = vi.hoisted(() => ({
  discoverLogin: vi.fn(),
  goto: vi.fn(async () => undefined),
  signIn: vi.fn(async (): Promise<{ activeTenant: { id: string } | null }> => ({ activeTenant: null })),
}))

vi.mock('./login-discovery.js', () => ({ discoverLogin }))
vi.mock('$lib/entities/session/session.svelte.js', () => ({
  session: { pending: false, signIn },
}))
vi.mock('$app/navigation', () => ({ goto }))
vi.mock('$app/paths', () => ({ resolve: (path: string) => path }))
vi.mock('$app/state', () => ({ page: { url: new URL('https://workbench.test/login') } }))

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

  it('selects a tenant and signs in with its password method', async () => {
    discoverLogin.mockResolvedValue({
      identifierRecognized: true,
      flow: 'TENANT',
      instancePasswordMethod: null,
      tenantMethods: [
        {
          loginMethod: { id: 'lmg_password', code: 'password', kind: 'PASSWORD', name: 'Password' },
          supportedTenants: [
            { id: 'ten_acme', name: 'Acme', slug: 'acme' },
            { id: 'ten_beta', name: 'Beta', slug: 'beta' },
          ],
        },
      ],
    })
    signIn.mockResolvedValueOnce({ activeTenant: { id: 'ten_beta' } })
    render(SignInForm)
    await fireEvent.input(screen.getByLabelText(/Email address/), { target: { value: 'member@example.test' } })
    await fireEvent.click(screen.getByRole('button', { name: 'Continue' }))
    await fireEvent.click(await screen.findByRole('button', { name: /Beta/ }))
    await fireEvent.input(screen.getByLabelText(/Password/), { target: { value: 'secure-password-1' } })
    await fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    await waitFor(() => expect(signIn).toHaveBeenCalledWith({
      email: 'member@example.test',
      password: 'secure-password-1',
      loginMethodId: 'lmg_password',
      tenantId: 'ten_beta',
    }))
    expect(goto).toHaveBeenCalledWith('/')
  })
})
