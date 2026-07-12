import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/svelte'
import { afterEach, describe, expect, it, vi } from 'vitest'
import SearchableMultiSelect from './searchable-multi-select.svelte'
import SearchableSelect from './searchable-select.svelte'

const options = [
  { id: 'todo', label: 'To do' },
  { id: 'progress', label: 'In progress' },
]

afterEach(cleanup)

describe('searchable select', () => {
  it('selects and clears a value', async () => {
    const onValueChange = vi.fn()
    render(SearchableSelect, { value: null, options, onValueChange })

    await fireEvent.click(screen.getByRole('button', { name: /select/i }))
    await fireEvent.click(screen.getByRole('option', { name: /In progress$/ }))
    expect(onValueChange).toHaveBeenCalledWith('progress')
  })

  it('does not open when disabled', async () => {
    render(SearchableSelect, { value: null, options, onValueChange: vi.fn(), disabled: true })
    await fireEvent.click(screen.getByRole('button'))
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('closes when clicking outside', async () => {
    render(SearchableSelect, { value: null, options, onValueChange: vi.fn() })
    await fireEvent.click(screen.getByRole('button', { name: /select/i }))
    expect(screen.getByRole('listbox')).not.toBeNull()

    await fireEvent.click(document.body)
    await waitFor(() => expect(screen.queryByRole('listbox')).toBeNull())
  })

  it('places the selected option first', async () => {
    render(SearchableSelect, { value: 'progress', options, onValueChange: vi.fn() })
    await fireEvent.click(screen.getByRole('button', { name: /In progress/i }))
    const selectedOption = screen.getAllByRole('option')[0]
    expect(selectedOption.getAttribute('aria-selected')).toBe('true')
    expect(selectedOption.classList.contains('w-full')).toBe(true)
    expect(selectedOption.querySelectorAll('.lucide-check')).toHaveLength(1)
  })

  it('shows skeleton rows while loading search results', async () => {
    render(SearchableSelect, { value: null, options: [], loading: true, onValueChange: vi.fn() })
    await fireEvent.click(screen.getByRole('button', { name: /select/i }))
    expect(screen.getByRole('status', { name: 'Loading options' })).not.toBeNull()
    expect(screen.getByRole('listbox').getAttribute('aria-busy')).toBe('true')
  })

  it('selects the keyboard-highlighted option', async () => {
    const onValueChange = vi.fn()
    render(SearchableSelect, { value: null, options, onValueChange })
    await fireEvent.click(screen.getByRole('button', { name: /select/i }))
    const search = screen.getByRole('combobox')
    await fireEvent.keyDown(search, { key: 'ArrowDown' })
    await fireEvent.keyDown(search, { key: 'Enter' })
    expect(onValueChange).toHaveBeenCalledWith('progress')
  })
})

describe('searchable multi-select', () => {
  it('adds and removes values', async () => {
    const onValuesChange = vi.fn()
    const view = render(SearchableMultiSelect, { values: ['todo'], options, onValuesChange })
    await fireEvent.click(view.container.querySelector('[aria-haspopup="listbox"]')!)
    await fireEvent.click(screen.getByRole('option', { name: /In progress$/ }))
    expect(onValuesChange).toHaveBeenCalledWith(['todo', 'progress'])

    await fireEvent.click(view.container.querySelector('[aria-label="Remove To do"]')!)
    expect(onValuesChange).toHaveBeenCalledWith([])
  })

  it('closes the multi-select when clicking outside', async () => {
    const view = render(SearchableMultiSelect, { values: [], options, onValuesChange: vi.fn() })
    await fireEvent.click(view.container.querySelector('[aria-haspopup="listbox"]')!)
    expect(screen.getByRole('listbox')).not.toBeNull()

    await fireEvent.click(document.body)
    await waitFor(() => expect(screen.queryByRole('listbox')).toBeNull())
  })

  it('places selected multi-select options first', async () => {
    const view = render(SearchableMultiSelect, { values: ['progress'], options, onValuesChange: vi.fn() })
    await fireEvent.click(view.container.querySelector('[aria-haspopup="listbox"]')!)
    const selectedOption = screen.getAllByRole('option')[0]
    expect(selectedOption.getAttribute('aria-selected')).toBe('true')
    expect(selectedOption.classList.contains('w-full')).toBe(true)
  })

  it('supports loading remote multi-select options', async () => {
    const view = render(SearchableMultiSelect, { values: [], options: [], loading: true, onValuesChange: vi.fn() })
    await fireEvent.click(view.container.querySelector('[aria-haspopup="listbox"]')!)
    expect(screen.getByRole('status', { name: 'Loading options' })).not.toBeNull()
  })

  it('toggles the keyboard-highlighted multi-select option', async () => {
    const onValuesChange = vi.fn()
    const view = render(SearchableMultiSelect, { values: [], options, onValuesChange })
    await fireEvent.click(view.container.querySelector('[aria-haspopup="listbox"]')!)
    const search = screen.getByRole('combobox')
    await fireEvent.keyDown(search, { key: 'ArrowDown' })
    await fireEvent.keyDown(search, { key: 'Enter' })
    expect(onValuesChange).toHaveBeenCalledWith(['progress'])
  })
})
