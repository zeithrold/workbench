import { cleanup, fireEvent, render, screen } from '@testing-library/svelte'
import { afterEach, describe, expect, it } from 'vitest'
import SelectValueTestHost from './select-value.test-host.svelte'

afterEach(cleanup)

describe('select value', () => {
  it('renders and updates the default selected item label', async () => {
    render(SelectValueTestHost)

    await fireEvent.keyDown(screen.getByRole('button', { name: 'is' }), { key: 'Enter' })
    await fireEvent.pointerUp(screen.getByRole('option', { name: 'is not' }), { pointerType: 'mouse' })

    expect(screen.getByRole('button', { name: 'is not' })).not.toBeNull()
  })

  it('allows callers to override the selected value content', () => {
    render(SelectValueTestHost, { custom: true })

    expect(screen.getByRole('button', { name: 'Value' })).not.toBeNull()
  })

  it('does not open or update when disabled', async () => {
    render(SelectValueTestHost, { disabled: true })
    const trigger = screen.getByRole('button', { name: 'is' })

    expect(trigger.hasAttribute('disabled')).toBe(true)
    await fireEvent.click(trigger)
    expect(screen.queryByRole('listbox')).toBeNull()
    expect(screen.getByRole('button', { name: 'is' })).not.toBeNull()
  })
})
