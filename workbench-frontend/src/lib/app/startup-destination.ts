import type { Session } from '$lib/entities/session/model.js'

export interface StartupContext {
  initialized: boolean
  session: Session | null
  pathname: string
}

export type StartupDestination = '/' | '/login' | '/setup' | '/setup/complete' | null

export function startupDestination({ initialized, session, pathname }: StartupContext): StartupDestination {
  if (!initialized)
    return pathname === '/setup' ? null : '/setup'

  if (!session)
    return pathname === '/login' ? null : '/login'

  if (!session.activeTenant)
    return pathname === '/setup/complete' ? null : '/setup/complete'

  return pathname === '/setup' || pathname === '/setup/complete' || pathname === '/login'
    ? '/'
    : null
}
