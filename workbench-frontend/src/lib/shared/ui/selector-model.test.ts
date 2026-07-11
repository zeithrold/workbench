import { describe, expect, it } from 'vitest'
import { filterSelectorOptions, prioritizeSelectedOptions, selectedOptions, toggleSelectorValue } from './selector-model.js'

const options = [
  { id: 'ada', label: 'Ada Lovelace', description: 'ada@example.com' },
  { id: 'grace', label: 'Grace Hopper', group: 'Engineering' },
]

describe('selector model', () => {
  it('filters across labels and metadata', () => {
    expect(filterSelectorOptions(options, 'EXAMPLE')).toEqual([options[0]])
    expect(filterSelectorOptions(options, 'engineering')).toEqual([options[1]])
  })

  it('preserves option ordering for selected values', () => {
    expect(selectedOptions(options, ['grace', 'ada'])).toEqual(options)
  })

  it('toggles a multi-select value', () => {
    expect(toggleSelectorValue(['ada'], 'grace')).toEqual(['ada', 'grace'])
    expect(toggleSelectorValue(['ada', 'grace'], 'ada')).toEqual(['grace'])
  })

  it('places selected options first without changing group order', () => {
    expect(prioritizeSelectedOptions(options, ['grace'])).toEqual([options[1], options[0]])
    expect(prioritizeSelectedOptions(options, [])).toBe(options)
  })
})
