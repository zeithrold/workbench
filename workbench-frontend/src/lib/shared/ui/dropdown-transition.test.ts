import { describe, expect, it } from 'vitest'
import { dropdownInTransition, dropdownOutTransition } from './dropdown-transition.js'

const animatableNode = { animate() {} } as unknown as Element

describe('dropdown transitions', () => {
  it('uses a decelerating entrance with subtle movement and scale', () => {
    const transition = dropdownInTransition(animatableNode)

    expect(transition.duration).toBe(160)
    expect(transition.easing?.(0.5)).toBeGreaterThan(0.5)
    expect(transition.css?.(0, 1)).toContain('translateY(-4px) scale(0.98)')
    expect(transition.css?.(1, 0)).toContain('translateY(0px) scale(1)')
  })

  it('uses an accelerating exit', () => {
    const transition = dropdownOutTransition(animatableNode)

    expect(transition.duration).toBe(100)
    expect(transition.easing?.(0.5)).toBeLessThan(0.5)
  })

  it('disables animation when the node cannot animate', () => {
    expect(dropdownInTransition({} as Element).duration).toBe(0)
  })
})
