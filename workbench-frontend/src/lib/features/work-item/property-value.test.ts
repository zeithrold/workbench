import { describe, expect, it } from 'vitest'
import { dynamicProperties } from './fixtures.js'
import { numberConstraint, stringConstraint, toIsoDateTime, toLocalDateTime, validateScalarValue } from './property-value.js'

describe('property value helpers', () => {
  it('reads supported validation constraints', () => {
    expect(numberConstraint(dynamicProperties.estimate.validationSchema, 'maximum')).toBe(100)
    expect(stringConstraint(dynamicProperties.title.validationSchema, 'minLength')).toBe(3)
  })

  it('validates required, string length, and URLs', () => {
    expect(validateScalarValue(dynamicProperties.title, '', true)).toBe('Customer reference is required.')
    expect(validateScalarValue(dynamicProperties.title, 'AB', false)).toBe('Enter at least 3 characters.')
    expect(validateScalarValue(dynamicProperties.referenceUrl, 'javascript:alert(1)', false)).toBe('Enter a valid http or https URL.')
    expect(validateScalarValue(dynamicProperties.referenceUrl, 'https://example.com', false)).toBeNull()
  })

  it('round trips datetime values through the local editor representation', () => {
    const iso = '2026-08-15T02:30:00.000Z'
    expect(toIsoDateTime(toLocalDateTime(iso))).toBe(iso)
  })
})
