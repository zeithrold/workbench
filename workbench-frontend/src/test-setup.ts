import { cleanup } from '@testing-library/svelte'
import { afterEach } from 'vitest'

afterEach(cleanup)

class TestResizeObserver implements ResizeObserver {
  disconnect() {}

  observe() {}

  unobserve() {}
}

globalThis.ResizeObserver = TestResizeObserver
HTMLElement.prototype.scrollIntoView = () => {}
