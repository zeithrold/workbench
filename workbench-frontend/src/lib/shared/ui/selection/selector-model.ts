export interface SelectorOption {
  id: string
  label: string
  description?: string
  color?: string
  avatar?: string
  group?: string
}

export function filterSelectorOptions(options: SelectorOption[], query: string): SelectorOption[] {
  const normalized = query.trim().toLocaleLowerCase()
  if (!normalized)
    return options
  return options.filter(option =>
    [option.label, option.description, option.group]
      .some(value => value?.toLocaleLowerCase().includes(normalized)),
  )
}

export function selectedOptions(options: SelectorOption[], values: string[]): SelectorOption[] {
  const selected = new Set(values)
  return options.filter(option => selected.has(option.id))
}

export function prioritizeSelectedOptions(options: SelectorOption[], values: string[]): SelectorOption[] {
  if (values.length === 0)
    return options

  const selected = new Set(values)
  return [
    ...options.filter(option => selected.has(option.id)),
    ...options.filter(option => !selected.has(option.id)),
  ]
}

export function toggleSelectorValue(values: string[], id: string): string[] {
  return values.includes(id) ? values.filter(value => value !== id) : [...values, id]
}
