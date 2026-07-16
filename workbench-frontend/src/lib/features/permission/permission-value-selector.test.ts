import type { PermissionConditionValue } from './permission-document.js'
import type { PermissionValueOption } from './permission-editor-model.js'
import { cleanup, fireEvent, render, screen } from '@testing-library/svelte'
import { afterEach, describe, expect, it, vi } from 'vitest'
import PermissionValueSelector from './permission-value-selector.svelte'

const members: PermissionValueOption[] = [
  { id: 'current', label: 'Current user', description: 'The signed-in user', value: { var: 'user.currentUser' } },
  { id: 'ada', label: 'Ada Lovelace', description: 'ada@example.com', value: 'usr_ada' },
]
const statuses: PermissionValueOption[] = [
  { id: 'started', label: 'Started', color: '#f59e0b', value: 'started' },
  { id: 'completed', label: 'Completed', color: '#22c55e', value: 'completed' },
]

function proxiedVariable(variable: Extract<PermissionConditionValue, { var: string }>['var']): PermissionConditionValue {
  return new Proxy({ var: variable }, {})
}

afterEach(cleanup)

describe('permission value selector', () => {
  it('selects variable member references', async () => {
    const onChange = vi.fn()
    const membersWithVariables: PermissionValueOption[] = [
      { id: 'current-user', label: 'Current user', value: proxiedVariable('user.currentUser') },
      { id: 'issue-reporter', label: 'Work item reporter', value: proxiedVariable('issue.reporter') },
      { id: 'issue-assignee', label: 'Work item assignee', value: proxiedVariable('issue.assignee') },
      { id: 'ada', label: 'Ada Lovelace', value: 'user-ada' },
    ]
    render(PermissionValueSelector, { value: { var: 'user.currentUser' }, options: membersWithVariables, onChange })

    await fireEvent.click(screen.getByRole('button', { name: /Current user/ }))
    await fireEvent.click(screen.getByRole('option', { name: /Work item reporter/ }))
    expect(onChange).toHaveBeenCalledWith({ var: 'issue.reporter' })

    await fireEvent.click(screen.getByRole('button', { name: /Current user/ }))
    await fireEvent.click(screen.getByRole('option', { name: /Work item assignee/ }))
    expect(onChange).toHaveBeenCalledWith({ var: 'issue.assignee' })
  })

  it('selects literal members and preserves variable member identity', async () => {
    const onChange = vi.fn()
    render(PermissionValueSelector, { value: { var: 'user.currentUser' }, options: members, onChange })

    expect(screen.getByRole('button', { name: /Current user/ })).not.toBeNull()
    await fireEvent.click(screen.getByRole('button', { name: /Current user/ }))
    expect(screen.getByText('ada@example.com')).not.toBeNull()
    await fireEvent.click(screen.getByRole('option', { name: /Ada Lovelace/ }))
    expect(onChange).toHaveBeenCalledWith('usr_ada')
  })

  it('maps multiple member selections back to typed values', async () => {
    const onChange = vi.fn()
    const view = render(PermissionValueSelector, { value: [{ var: 'user.currentUser' }], options: members, multiple: true, onChange })

    await fireEvent.click(view.container.querySelector('[aria-haspopup="listbox"]')!)
    await fireEvent.click(screen.getByRole('option', { name: /Ada Lovelace/ }))
    expect(onChange).toHaveBeenCalledWith([{ var: 'user.currentUser' }, 'usr_ada'])
  })

  it('removes variable values from a multi-select', async () => {
    const onChange = vi.fn()
    const view = render(PermissionValueSelector, { value: [{ var: 'user.currentUser' }, 'usr_ada'], options: members, multiple: true, onChange })

    await fireEvent.click(view.container.querySelector('[aria-haspopup="listbox"]')!)
    await fireEvent.click(screen.getByRole('option', { name: /Current user/ }))
    expect(onChange).toHaveBeenCalledWith(['usr_ada'])
  })

  it('uses the shared enum color presentation for options and selected values', async () => {
    render(PermissionValueSelector, { value: 'completed', options: statuses, onChange: vi.fn() })

    const trigger = screen.getByRole('button', { name: /Completed/ })
    expect(trigger.querySelector('[style*="background-color: rgb(34, 197, 94)"]')).not.toBeNull()
    await fireEvent.click(trigger)
    const started = screen.getByRole('option', { name: /Started/ })
    expect(started.querySelector('[style*="background-color: rgb(245, 158, 11)"]')).not.toBeNull()

    cleanup()
    render(PermissionValueSelector, { value: ['completed'], options: statuses, multiple: true, onChange: vi.fn() })
    const multiTrigger = screen.getByRole('button', { name: /Completed/ })
    expect(multiTrigger.querySelector('[style*="background-color: rgb(34, 197, 94)"]')).not.toBeNull()
  })

  it('does not open when read only', async () => {
    render(PermissionValueSelector, { value: 'completed', options: statuses, disabled: true, onChange: vi.fn() })
    const trigger = screen.getByRole('button', { name: /Completed/ })
    expect(trigger.hasAttribute('disabled')).toBe(true)
    await fireEvent.click(trigger)
    expect(screen.queryByRole('listbox')).toBeNull()
  })
})
