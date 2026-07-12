class TestResizeObserver implements ResizeObserver {
  disconnect() {}

  observe() {}

  unobserve() {}
}

globalThis.ResizeObserver = TestResizeObserver
HTMLElement.prototype.scrollIntoView = () => {}
