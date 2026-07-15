import { instanceSetup } from '$lib/entities/instance-setup/instance-setup.svelte.js'
import { fireEvent, render, screen, waitFor } from '@testing-library/svelte'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import InstanceSetupForm from './instance-setup-form.svelte'

const { goto, load, accept } = vi.hoisted(() => ({
  goto: vi.fn(async () => undefined),
  load: vi.fn(async () => undefined),
  accept: vi.fn(),
}))

vi.mock('$app/navigation', () => ({ goto }))
vi.mock('$app/paths', () => ({ resolve: (path: string) => path }))
vi.mock('$lib/app/app-bootstrap.svelte.js', () => ({ appBootstrap: { load } }))
vi.mock('$lib/entities/session/session.svelte.js', () => ({ session: { accept } }))

describe('instanceSetupForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    instanceSetup.current = { initialized: false, setupTokenRequired: true }
  })

  it('requires the configured setup token and matching passwords', async () => {
    const setup = vi.spyOn(instanceSetup, 'setup')
    render(InstanceSetupForm)
    expect(screen.getByLabelText(/Setup token/)).not.toBeNull()

    await fireEvent.input(screen.getByLabelText(/^Password/), { target: { value: 'secure-password-1' } })
    await fireEvent.input(screen.getByLabelText(/Confirm password/), { target: { value: 'different-password' } })
    await fireEvent.click(screen.getByRole('button', { name: 'Initialize Workbench' }))

    expect(screen.getByText('Passwords do not match.')).not.toBeNull()
    expect(screen.getByText('Setup token is required for this instance.')).not.toBeNull()
    expect(setup).not.toHaveBeenCalled()
  })

  it('creates the administrator and accepts the returned session', async () => {
    const createdSession = { user: { id: 'usr_1' }, activeTenant: null }
    const setup = vi.spyOn(instanceSetup, 'setup').mockResolvedValue(createdSession as never)
    render(InstanceSetupForm)

    await fireEvent.input(screen.getByLabelText(/Display name/), { target: { value: 'Admin' } })
    await fireEvent.input(screen.getByLabelText(/Email address/), { target: { value: 'admin@example.test' } })
    await fireEvent.input(screen.getByLabelText(/^Password/), { target: { value: 'secure-password-1' } })
    await fireEvent.input(screen.getByLabelText(/Confirm password/), { target: { value: 'secure-password-1' } })
    await fireEvent.input(screen.getByLabelText(/Setup token/), { target: { value: 'bootstrap-token' } })
    await fireEvent.click(screen.getByRole('button', { name: 'Initialize Workbench' }))

    await waitFor(() => expect(setup).toHaveBeenCalledWith({
      displayName: 'Admin',
      email: 'admin@example.test',
      password: 'secure-password-1',
      setupToken: 'bootstrap-token',
    }))
    expect(accept).toHaveBeenCalledWith(createdSession)
    expect(goto).toHaveBeenCalledWith('/setup/complete')
  })
})
