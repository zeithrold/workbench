import type { WorkItemFieldOption, WorkItemListItem } from '$lib/entities/work-item/index.js'
import { fireEvent, render, screen, waitFor } from '@testing-library/svelte'
import { describe, expect, it, vi } from 'vitest'
import WorkItemCell from './work-item-cell.svelte'

const option: WorkItemFieldOption = {
  id: 'usr-jordan',
  label: 'Jordan',
  description: null,
  color: null,
  icon: null,
  status: null,
}

const item: WorkItemListItem = {
  id: 'wi-1',
  key: 'WB-1',
  title: 'Inline editing',
  projectId: 'prj-1',
  issueTypeConfigId: 'cfg-1',
  issueType: { id: 'typ-1', code: 'task', name: 'Task', color: '#64748b', icon: null },
  status: { id: 'sta-1', code: 'open', name: 'Open', group: 'todo', terminal: false, color: '#3b82f6' },
  priority: { id: 'pri-1', code: 'high', name: 'High', color: '#ef4444' },
  reporter: { id: 'usr-1', displayName: 'Alex' },
  assignee: { id: 'usr-2', displayName: 'Sam' },
  sprint: { id: 'spr-1', name: 'Sprint 1', status: 'active' },
  properties: {},
  fieldCapabilities: {
    assignee: { state: 'EDITABLE', reason: null },
    priority: { state: 'EDITABLE', reason: null },
    sprint: { state: 'EDITABLE', reason: null },
  },
  createdAt: '2026-07-20T00:00:00Z',
  updatedAt: '2026-07-21T00:00:00Z',
}

describe('work item inline cell', () => {
  it('lets assignee be replaced but provides no clear entry or delete-key clearing', async () => {
    const onValueChange = vi.fn()
    const loadOptions = vi.fn().mockResolvedValue([option])
    render(WorkItemCell, { item, fieldId: 'assignee', loadOptions, onValueChange })

    const trigger = screen.getByRole('button', { name: /Sam/ })
    await fireEvent.keyDown(trigger, { key: 'Delete' })
    expect(onValueChange).not.toHaveBeenCalled()
    await fireEvent.click(trigger)
    expect(await screen.findByRole('button', { name: /Jordan/ })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Backlog' })).toBeNull()
    await fireEvent.click(screen.getByRole('button', { name: /Jordan/ }))

    expect(onValueChange).toHaveBeenCalledWith(item, 'assignee', option)
  })

  it('keeps priority required while sprint explicitly supports clearing to Backlog', async () => {
    const priorityChange = vi.fn()
    const priorityView = render(WorkItemCell, {
      item,
      fieldId: 'priority',
      loadOptions: vi.fn().mockResolvedValue([option]),
      onValueChange: priorityChange,
    })
    await fireEvent.click(screen.getByRole('button', { name: /High/ }))
    expect(screen.queryByRole('button', { name: 'Backlog' })).toBeNull()
    priorityView.unmount()

    const sprintChange = vi.fn()
    render(WorkItemCell, {
      item,
      fieldId: 'sprint',
      loadOptions: vi.fn().mockResolvedValue([]),
      onValueChange: sprintChange,
    })
    await fireEvent.click(screen.getByRole('button', { name: /Sprint 1/ }))
    await fireEvent.click(await screen.findByRole('button', { name: 'Backlog' }))
    expect(sprintChange).toHaveBeenCalledWith(item, 'sprint', null)
  })

  it('does not open or load options for disabled cells', async () => {
    const loadOptions = vi.fn().mockResolvedValue([option])
    render(WorkItemCell, {
      item: { ...item, fieldCapabilities: { assignee: { state: 'READ_ONLY', reason: 'permission_denied' } } },
      fieldId: 'assignee',
      loadOptions,
    })

    await fireEvent.click(screen.getByText('Sam'))
    await waitFor(() => expect(loadOptions).not.toHaveBeenCalled())
    expect(screen.queryByRole('button', { name: /Jordan/ })).toBeNull()
  })
})
