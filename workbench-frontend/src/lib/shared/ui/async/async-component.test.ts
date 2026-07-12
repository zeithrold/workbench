import type { Component } from 'svelte'
import { cleanup, fireEvent, render, screen } from '@testing-library/svelte'
import { afterEach, describe, expect, it, vi } from 'vitest'
import TestTarget from './async-component-test-target.svelte'
import AsyncComponent from './async-component.svelte'

interface TestAsyncProps {
  loader: () => Promise<{ default: Component<{ message: string }> }>
  componentProps: { message: string }
  loadingLabel?: string
}

const TestAsyncComponent = AsyncComponent as Component<TestAsyncProps>

afterEach(cleanup)

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

describe('async component', () => {
  it('shows a skeleton before rendering the loaded component with its props', async () => {
    const pending = deferred<{ default: Component<{ message: string }> }>()
    const loader = vi.fn(() => pending.promise)

    const view = render(TestAsyncComponent, {
      loader,
      componentProps: { message: 'Loaded on demand' },
      loadingLabel: 'Loading test component',
    })

    expect(screen.getByRole('status', { name: 'Loading test component' })).toBeTruthy()
    view.rerender({
      loader,
      componentProps: { message: 'Updated before load' },
      loadingLabel: 'Loading test component',
    })
    expect(loader).toHaveBeenCalledTimes(1)

    pending.resolve({ default: TestTarget })
    expect((await screen.findByTestId('async-target')).textContent).toContain('Updated before load')
    expect(loader).toHaveBeenCalledTimes(1)
  })

  it('retries after a failed component load', async () => {
    const loader = vi.fn()
      .mockRejectedValueOnce(new Error('network unavailable'))
      .mockResolvedValueOnce({ default: TestTarget })

    render(TestAsyncComponent, {
      loader,
      componentProps: { message: 'Recovered' },
    })

    expect((await screen.findByRole('alert')).textContent).toContain('could not be loaded')
    await fireEvent.click(screen.getByRole('button', { name: 'Retry' }))

    expect((await screen.findByTestId('async-target')).textContent).toContain('Recovered')
    expect(loader).toHaveBeenCalledTimes(2)
  })
})
