import { describe, expect, it } from 'vitest'
import { safeReturnTo } from './return-to.js'

describe('safeReturnTo', () => {
  it('accepts internal paths and rejects external redirects', () => {
    expect(safeReturnTo('/invitations/token?from=mail')).toBe('/invitations/token?from=mail')
    expect(safeReturnTo('//evil.example/path')).toBeNull()
    expect(safeReturnTo('https://evil.example/path')).toBeNull()
    expect(safeReturnTo(null)).toBeNull()
  })
})
