import { cleanup, fireEvent, render, screen } from '@testing-library/svelte'
import { afterEach, describe, expect, it, vi } from 'vitest'
import DynamicPropertySelector from './dynamic-property-selector.svelte'
import { customOptions, dynamicProperties, users } from './fixtures.js'

afterEach(cleanup)

describe('dynamic property selector', () => {
  it('dispatches single and multi value properties', () => {
    const single = render(DynamicPropertySelector, {
      definition: dynamicProperties.category,
      data: { options: customOptions },
    })
    expect(single.container.querySelector('[data-slot="searchable-select"]')).not.toBeNull()
    single.unmount()

    const multi = render(DynamicPropertySelector, {
      definition: dynamicProperties.watchers,
      data: { options: users },
    })
    expect(multi.container.querySelector('[data-slot="searchable-multi-select"]')).not.toBeNull()
  })

  it('dispatches scalar property editors', () => {
    render(DynamicPropertySelector, { definition: dynamicProperties.estimate, scalarValue: 8 })
    expect(screen.getByRole('spinbutton')).not.toBeNull()
  })

  it('uses a multi-select for array project and issue properties', () => {
    const definition = { ...dynamicProperties.issue, isArray: true }
    const view = render(DynamicPropertySelector, { definition, data: { options: [] }, values: [] })
    expect(view.container.querySelector('[data-slot="searchable-multi-select"]')).not.toBeNull()
  })

  it('emits typed scalar values', async () => {
    const onNumberChange = vi.fn()
    render(DynamicPropertySelector, { definition: dynamicProperties.estimate, onScalarValueChange: onNumberChange })
    await fireEvent.input(screen.getByRole('spinbutton'), { target: { value: '13.5' } })
    expect(onNumberChange).toHaveBeenCalledWith(13.5)
    cleanup()

    const onBooleanChange = vi.fn()
    render(DynamicPropertySelector, { definition: dynamicProperties.billable, scalarValue: false, onScalarValueChange: onBooleanChange })
    await fireEvent.click(screen.getByRole('switch'))
    expect(onBooleanChange).toHaveBeenCalledWith(true)
  })

  it.each([
    [dynamicProperties.title, 'Customer reference'],
    [dynamicProperties.targetDate, 'Target date'],
    [dynamicProperties.releaseAt, 'Release at'],
    [dynamicProperties.referenceUrl, 'Reference URL'],
  ])('renders the %s editor', (definition, label) => {
    render(DynamicPropertySelector, { definition })
    const editor = screen.getByLabelText(label)
    expect(editor).not.toBeNull()
    if (definition.dataType === 'date' || definition.dataType === 'datetime')
      expect(editor.tagName).toBe('BUTTON')
    cleanup()
  })

  it('opens calendar controls instead of editable date text fields', async () => {
    render(DynamicPropertySelector, { definition: dynamicProperties.targetDate, scalarValue: '2026-08-15' })
    await fireEvent.click(screen.getByLabelText('Target date'))
    expect(await screen.findByRole('grid')).not.toBeNull()
    cleanup()

    render(DynamicPropertySelector, { definition: dynamicProperties.releaseAt, scalarValue: '2026-08-15T02:30:00.000Z' })
    await fireEvent.click(screen.getByLabelText('Release at'))
    expect(screen.getByLabelText('Hour')).not.toBeNull()
    expect(screen.getByLabelText('Minute')).not.toBeNull()
  })

  it('renders an explicit fallback for rich text and json properties', () => {
    render(DynamicPropertySelector, { definition: dynamicProperties.metadata })
    expect(screen.getByText('json editor is not available yet.')).not.toBeNull()
  })
})
