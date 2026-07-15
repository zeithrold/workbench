import { instanceSetup } from '$lib/entities/instance-setup/instance-setup.svelte.js'
import { session } from '$lib/entities/session/session.svelte.js'
import { localeState } from '$lib/i18n/locale.svelte.js'
import { m } from '$lib/paraglide/messages.js'

export type AppBootstrapState = 'idle' | 'loading' | 'ready' | 'failed'

export class AppBootstrapStore {
  state = $state<AppBootstrapState>('idle')
  error = $state<Error | null>(null)
  #pending: Promise<void> | null = null

  load(force = false): Promise<void> {
    if (this.#pending && !force)
      return this.#pending
    if (this.state === 'ready' && !force)
      return Promise.resolve()

    this.state = 'loading'
    this.error = null
    this.#pending = Promise.all([instanceSetup.load(), session.restore()])
      .then(() => {
        localeState.synchronize(session.current?.localeContext)
        this.state = 'ready'
      })
      .catch((error: unknown) => {
        this.error = error instanceof Error ? error : new Error(m.app_start_failed())
        this.state = 'failed'
        throw this.error
      })
      .finally(() => {
        this.#pending = null
      })
    return this.#pending
  }
}

export const appBootstrap = new AppBootstrapStore()
