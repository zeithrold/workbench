import type { WorkItemListItem } from '$lib/entities/work-item/index.js'
import { fireEvent, render, screen } from '@testing-library/svelte'
import { describe, expect, it, vi } from 'vitest'
import WorkItemList from './work-item-list.svelte'

const item: WorkItemListItem = {
  id: 'wi-1',
  key: 'WB-1',
  title: 'Ship the list foundation',
  projectId: 'project-1',
  issueTypeConfigId: 'config-1',
  issueType: { id: 'type-1', code: 'task', name: 'Task', icon: null, color: null },
  status: { id: 'status-1', code: 'open', name: 'Open', group: 'unstarted', terminal: false, color: null },
  priority: null,
  reporter: { id: 'user-1', displayName: 'Alex' },
  assignee: null,
  sprint: null,
  properties: {},
  fieldCapabilities: {},
  createdAt: '2026-07-17T01:00:00Z',
  updatedAt: '2026-07-17T02:00:00Z',
}

describe('work item list', () => {
  it('renders loading, empty and error states', () => {
    const loading = render(WorkItemList, { loading: true })
    expect(screen.getByLabelText('Loading work items')).toBeTruthy()
    loading.unmount()

    const empty = render(WorkItemList)
    expect(screen.getByText('No work items found.')).toBeTruthy()
    empty.unmount()

    render(WorkItemList, { error: new Error('Server unavailable') })
    expect(screen.getByText('Work items could not be loaded.')).toBeTruthy()
    expect(screen.getByText('Server unavailable')).toBeTruthy()
  })

  it('keeps checkbox interaction separate from row opening', async () => {
    const onRowOpen = vi.fn()
    const onSelectionChange = vi.fn()
    render(WorkItemList, { items: [item], selectedIds: [], onSelectionChange, onRowOpen })

    await fireEvent.click(screen.getByRole('checkbox', { name: 'Select WB-1' }))
    expect(onSelectionChange).toHaveBeenCalledWith(['wi-1'])
    expect(onRowOpen).not.toHaveBeenCalled()

    await fireEvent.click(screen.getByText('Ship the list foundation'))
    expect(onRowOpen).toHaveBeenCalledWith(item)
  })

  it('selects only the currently loaded rows and guards duplicate load-more clicks', async () => {
    const onSelectionChange = vi.fn()
    const onLoadMore = vi.fn()
    render(WorkItemList, {
      items: [item, { ...item, id: 'wi-2', key: 'WB-2' }],
      selectedIds: ['not-loaded'],
      onSelectionChange,
      hasNextPage: true,
      onLoadMore,
    })

    await fireEvent.click(screen.getByRole('checkbox', { name: 'Select all loaded work items' }))
    expect(onSelectionChange).toHaveBeenCalledWith(expect.arrayContaining(['not-loaded', 'wi-1', 'wi-2']))
    await fireEvent.click(screen.getByRole('button', { name: 'Load more' }))
    expect(onLoadMore).toHaveBeenCalledOnce()
  })
})
