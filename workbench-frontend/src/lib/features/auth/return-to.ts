export function safeReturnTo(value: string | null): string | null {
  if (!value || !value.startsWith('/') || value.startsWith('//'))
    return null
  try {
    const url = new URL(value, 'https://workbench.invalid')
    return url.origin === 'https://workbench.invalid' ? `${url.pathname}${url.search}${url.hash}` : null
  }
  catch {
    return null
  }
}
