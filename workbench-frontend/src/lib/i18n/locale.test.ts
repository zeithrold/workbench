import { describe, expect, it } from 'vitest'
import { LocaleState, resolveLocale } from './locale.svelte.js'

describe('locale resolution', () => {
  it('uses user preference before tenant and browser', () => {
    expect(resolveLocale({ userPreference: 'en-US', tenantDefault: 'fr-FR' }, ['de-DE']))
      .toBe('en-US')
  })

  it('uses tenant default when the user follows the tenant', () => {
    expect(resolveLocale({ userPreference: null, tenantDefault: 'en-US' }, ['fr-FR']))
      .toBe('en-US')
  })

  it('matches a supported language when the region differs', () => {
    expect(resolveLocale(null, ['en-GB'])).toBe('en-US')
  })

  it('falls back after malformed and unsupported locales', () => {
    expect(resolveLocale({ userPreference: 'not_a_locale', tenantDefault: 'fr-FR' }, ['zh-CN']))
      .toBe('en-US')
  })

  it('updates the document language and direction', () => {
    new LocaleState().synchronize({ userPreference: 'en-US', tenantDefault: null }, [])
    expect(document.documentElement.lang).toBe('en-US')
    expect(document.documentElement.dir).toBe('ltr')
  })
})
