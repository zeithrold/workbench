import type { WorkItemDisplayField } from '$lib/entities/work-item/index.js'
import { fireEvent, render, screen } from '@testing-library/svelte'
import { describe, expect, it, vi } from 'vitest'
import WorkItemColumnManager from './work-item-column-manager.svelte'
import { WORK_ITEM_COLUMNS } from './work-item-list-config.js'

function item(id: string, field: WorkItemDisplayField) {
  const column = WORK_ITEM_COLUMNS.find(candidate => candidate.id === id)!
  return { id, label: column.label, field, required: Boolean(column.required) }
}

function zone(label: string): HTMLElement {
  return screen.getByLabelText(label).querySelector('.min-h-64') as HTMLElement
}

describe('work item column manager', () => {
  it('keeps changes in a draft and emits exactly once on apply', async () => {
    const onConfirm = vi.fn()
    render(WorkItemColumnManager, {
      fields: [{ field: 'key', width: 110, pinned: true }, { field: 'title', width: 360 }],
      columns: WORK_ITEM_COLUMNS,
      onConfirm,
    })
    await fireEvent.click(screen.getByRole('button', { name: 'Columns' }))
    await fireEvent(zone('Displayed columns'), new CustomEvent('finalize', {
      detail: {
        items: [
          item('title', { field: 'title', width: 360 }),
          item('key', { field: 'key', width: 110, pinned: true }),
          item('status', { field: 'status', width: 140 }),
        ],
        info: { trigger: 'droppedIntoAnother', id: 'status', source: 'pointer' },
      },
    }))
    expect(onConfirm).not.toHaveBeenCalled()
    await fireEvent.click(screen.getByRole('button', { name: 'Apply' }))

    expect(onConfirm).toHaveBeenCalledOnce()
    expect(onConfirm.mock.calls[0][0].map((field: WorkItemDisplayField) => field.field)).toEqual(['title', 'key', 'status'])
  })

  it('never allows title to move to available columns and cancel discards the draft', async () => {
    const onConfirm = vi.fn()
    render(WorkItemColumnManager, {
      fields: [{ field: 'key' }, { field: 'title' }],
      columns: WORK_ITEM_COLUMNS,
      onConfirm,
    })
    await fireEvent.click(screen.getByRole('button', { name: 'Columns' }))
    await fireEvent(zone('Available columns'), new CustomEvent('finalize', {
      detail: {
        items: [item('title', { field: 'title' })],
        info: { trigger: 'droppedIntoAnother', id: 'title', source: 'keyboard' },
      },
    }))
    await fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(onConfirm).not.toHaveBeenCalled()
  })

  it('supports keyboard sorting and cross-zone movement from the drag handles', async () => {
    const onConfirm = vi.fn()
    render(WorkItemColumnManager, {
      fields: [{ field: 'key' }, { field: 'title' }],
      columns: WORK_ITEM_COLUMNS,
      onConfirm,
    })
    await fireEvent.click(screen.getByRole('button', { name: 'Columns' }))
    await fireEvent.keyDown(screen.getByRole('button', { name: 'Drag Key' }), { key: 'ArrowDown' })
    await fireEvent.keyDown(screen.getByRole('button', { name: 'Drag Status' }), { key: 'ArrowRight' })
    await fireEvent.click(screen.getByRole('button', { name: 'Apply' }))

    expect(onConfirm.mock.calls[0][0].map((field: WorkItemDisplayField) => field.field)).toEqual(['title', 'key', 'status'])
  })
})
