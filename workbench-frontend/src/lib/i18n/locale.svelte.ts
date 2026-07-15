import { browser } from '$app/environment'
import {
  baseLocale,
  getTextDirection,
  locales,
  setLocale,
} from '$lib/paraglide/runtime.js'

export type Locale = typeof locales[number]

export interface LocaleContext {
  userPreference: string | null
  tenantDefault: string | null
}

function canonicalize(candidate: string): string | null {
  try {
    return Intl.getCanonicalLocales(candidate)[0] ?? null
  }
  catch {
    return null
  }
}

export function resolveLocale(
  context: Partial<LocaleContext> | null | undefined,
  browserLanguages: readonly string[] = [],
): Locale {
  const supported = locales.map(locale => ({ locale, canonical: canonicalize(locale) ?? locale }))
  const candidates = [
    context?.userPreference,
    context?.tenantDefault,
    ...browserLanguages,
    baseLocale,
  ]

  for (const candidate of candidates) {
    if (!candidate)
      continue
    const canonical = canonicalize(candidate)
    if (!canonical)
      continue
    const exact = supported.find(item => item.canonical === canonical)
    if (exact)
      return exact.locale
    const language = new Intl.Locale(canonical).language
    const languageMatch = supported.find(item => new Intl.Locale(item.canonical).language === language)
    if (languageMatch)
      return languageMatch.locale
  }
  return baseLocale
}

export class LocaleState {
  current = $state<Locale>(baseLocale)

  synchronize(
    context: Partial<LocaleContext> | null | undefined,
    browserLanguages: readonly string[] = browser ? navigator.languages : [],
  ): Locale {
    const next = resolveLocale(context, browserLanguages)
    void setLocale(next, { reload: false })
    this.current = next
    if (browser) {
      document.documentElement.lang = next
      document.documentElement.dir = getTextDirection(next)
    }
    return next
  }
}

export const localeState = new LocaleState()
