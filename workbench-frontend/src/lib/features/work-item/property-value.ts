import type { WorkItemPropertyDefinition, WorkItemScalarValue } from './selector-types.js'

export function numberConstraint(schema: Record<string, unknown>, key: 'minimum' | 'maximum' | 'multipleOf'): number | undefined {
  const value = schema[key]
  return typeof value === 'number' ? value : undefined
}

export function stringConstraint(schema: Record<string, unknown>, key: 'minLength' | 'maxLength'): number | undefined {
  const value = schema[key]
  return typeof value === 'number' && Number.isInteger(value) ? value : undefined
}

export function validateScalarValue(definition: WorkItemPropertyDefinition, value: WorkItemScalarValue, required: boolean): string | null {
  if (required && (value === null || value === ''))
    return `${definition.name} is required.`

  if (typeof value !== 'string' || value === '')
    return null

  const minLength = stringConstraint(definition.validationSchema, 'minLength')
  const maxLength = stringConstraint(definition.validationSchema, 'maxLength')
  if (minLength !== undefined && value.length < minLength)
    return `Enter at least ${minLength} characters.`
  if (maxLength !== undefined && value.length > maxLength)
    return `Enter no more than ${maxLength} characters.`

  if (definition.dataType === 'url') {
    const valid = URL.canParse(value) && ['http:', 'https:'].includes(new URL(value).protocol)
    if (!valid)
      return 'Enter a valid http or https URL.'
  }
  return null
}

export function toLocalDateTime(value: WorkItemScalarValue): string {
  if (typeof value !== 'string' || !value)
    return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime()))
    return ''
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

export function toIsoDateTime(value: string): string | null {
  if (!value)
    return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date.toISOString()
}
