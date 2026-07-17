import type { Session } from '$lib/entities/session/model.js'

export interface StartupContext {
  initialized: boolean
  session: Session | null
  pathname: string
}

export type StartupDestination = '/' | '/login' | '/manage/instance' | '/setup' | '/setup/complete' | null

export function startupDestination({ initialized, session, pathname }: StartupContext): StartupDestination {
  if (!initialized)
    return pathname === '/setup' ? null : '/setup'

  if (!session && pathname.startsWith('/invitations/'))
    return null

  if (!session)
    return pathname === '/login' ? null : '/login'

  if (pathname.startsWith('/invitations/'))
    return null

  if (!session.activeTenant) {
    if (session.adminScopes.includes('INSTANCE'))
      return pathname.startsWith('/manage') ? null : '/manage/instance'
    return pathname === '/setup/complete' ? null : '/setup/complete'
  }

  return pathname === '/setup' || pathname === '/setup/complete' || pathname === '/login'
    ? '/'
    : null
}
