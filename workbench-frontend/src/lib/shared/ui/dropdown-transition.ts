import type { TransitionConfig } from 'svelte/transition'
import { cubicIn, cubicOut } from 'svelte/easing'

function dropdownTransition(node: Element, duration: number, easing: (progress: number) => number): TransitionConfig {
  if (typeof node.animate !== 'function')
    return { duration: 0, tick: () => {} }

  const reduceMotion
    = typeof window !== 'undefined'
      && typeof window.matchMedia === 'function'
      && window.matchMedia('(prefers-reduced-motion: reduce)').matches

  return {
    duration: reduceMotion ? 0 : duration,
    easing,
    css: progress => `
      opacity: ${progress};
      transform: translateY(${(1 - progress) * -4}px) scale(${0.98 + progress * 0.02});
    `,
  }
}

export function dropdownInTransition(node: Element): TransitionConfig {
  return dropdownTransition(node, 160, cubicOut)
}

export function dropdownOutTransition(node: Element): TransitionConfig {
  return dropdownTransition(node, 100, cubicIn)
}
