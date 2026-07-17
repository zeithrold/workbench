import { fireEvent, render, screen, waitFor } from '@testing-library/svelte'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import InvitationForm from './invitation-form.svelte'

const { invite } = vi.hoisted(() => ({ invite: vi.fn() }))

vi.mock('$lib/entities/management/management-gateway.js', () => ({
  managementGateway: { invite },
}))

describe('invitationForm', () => {
  beforeEach(() => vi.clearAllMocks())

  it('creates multiple invitations and exposes each one-time link', async () => {
    invite
      .mockResolvedValueOnce({ id: 'inv_1', email: 'ada@example.test', expiresAt: '2026-07-24T00:00:00Z', invitationLink: 'https://workbench.test/invitations/one' })
      .mockResolvedValueOnce({ id: 'inv_2', email: 'grace@example.test', expiresAt: '2026-07-24T00:00:00Z', invitationLink: 'https://workbench.test/invitations/two' })
    render(InvitationForm)

    for (const email of ['ada@example.test', 'grace@example.test']) {
      await fireEvent.input(screen.getByLabelText('Email'), { target: { value: email } })
      await fireEvent.click(screen.getByRole('button', { name: 'Send invite' }))
    }

    await waitFor(() => expect(invite).toHaveBeenCalledTimes(2))
    expect(screen.getByText('https://workbench.test/invitations/one')).not.toBeNull()
    expect(screen.getByText('https://workbench.test/invitations/two')).not.toBeNull()
  })
})
