import { describe, expect, it } from 'vitest'
import {
  DEFAULT_WORK_ITEM_DISPLAY_FIELDS,
  moveDisplayField,
  normalizeDisplayFields,
  setColumnVisible,
  updateDisplayField,
} from './work-item-list-config.js'

describe('work item display fields', () => {
  it('defines the agreed default columns and widths', () => {
    expect(DEFAULT_WORK_ITEM_DISPLAY_FIELDS.map(field => [field.field, field.width])).toEqual([
      ['key', 110],
      ['title', 360],
      ['issueType', 140],
      ['status', 140],
      ['priority', 120],
      ['assignee', 180],
      ['sprint', 160],
      ['updatedAt', 180],
    ])
  })

  it('keeps title visible and clamps widths', () => {
    expect(normalizeDisplayFields([{ field: 'key', width: 10 }])).toEqual([
      { field: 'key', width: 80, pinned: false },
      { field: 'title', width: 360, pinned: false },
    ])
    expect(updateDisplayField(DEFAULT_WORK_ITEM_DISPLAY_FIELDS, 'title', { width: 999 })[1].width).toBe(720)
  })

  it('supports visibility, ordering and pinning without drag state', () => {
    const hidden = setColumnVisible(DEFAULT_WORK_ITEM_DISPLAY_FIELDS, 'priority', false)
    expect(hidden.some(field => field.field === 'priority')).toBe(false)
    expect(setColumnVisible(hidden, 'priority', true).at(-1)).toEqual({ field: 'priority', width: 120, pinned: false })
    expect(setColumnVisible(DEFAULT_WORK_ITEM_DISPLAY_FIELDS, 'title', false)).toHaveLength(8)
    expect(moveDisplayField(DEFAULT_WORK_ITEM_DISPLAY_FIELDS, 'status', -1).map(field => field.field).slice(2, 4))
      .toEqual(['status', 'issueType'])
    expect(updateDisplayField(DEFAULT_WORK_ITEM_DISPLAY_FIELDS, 'status', { pinned: true })[3].pinned).toBe(true)
  })
})
