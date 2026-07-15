import type { InstanceSetupGateway } from './instance-setup-gateway.js'
import type { InstanceSetupInput, InstanceSetupStatus } from './model.js'
import { ApiInstanceSetupGateway } from './instance-setup-gateway.js'

export class InstanceSetupStore {
  current = $state<InstanceSetupStatus | null>(null)

  constructor(private readonly gateway: InstanceSetupGateway) {}

  async load() {
    this.current = await this.gateway.status()
    return this.current
  }

  async setup(input: InstanceSetupInput) {
    const createdSession = await this.gateway.setup(input)
    this.current = {
      initialized: true,
      setupTokenRequired: this.current?.setupTokenRequired ?? false,
    }
    return createdSession
  }
}

export const instanceSetup = new InstanceSetupStore(new ApiInstanceSetupGateway())
