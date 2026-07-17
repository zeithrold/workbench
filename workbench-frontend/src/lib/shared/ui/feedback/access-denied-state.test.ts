import { render, screen } from '@testing-library/svelte'
import { describe, expect, it } from 'vitest'
import AccessDeniedState from './access-denied-state.svelte'

describe('accessDeniedState', () => {
  it('renders the authoritative server denial', () => {
    render(AccessDeniedState, { description: 'The requested action is not allowed.' })

    expect(screen.getByRole('heading', { name: 'Access denied' })).toBeTruthy()
    expect(screen.getByText('The requested action is not allowed.')).toBeTruthy()
  })
})
