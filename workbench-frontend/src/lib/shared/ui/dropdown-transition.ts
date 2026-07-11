import type { TransitionConfig } from 'svelte/transition'

function dropdownTransition(node: Element, duration: number): TransitionConfig {
  if (typeof node.animate !== 'function')
    return { duration: 0, tick: () => {} }

  const reduceMotion
    = typeof window !== 'undefined'
      && typeof window.matchMedia === 'function'
      && window.matchMedia('(prefers-reduced-motion: reduce)').matches

  return {
    duration: reduceMotion ? 0 : duration,
    css: progress => `
      opacity: ${progress};
      transform: translateY(${(1 - progress) * -4}px) scale(${0.95 + progress * 0.05});
    `,
  }
}

export function dropdownInTransition(node: Element): TransitionConfig {
  return dropdownTransition(node, 150)
}

export function dropdownOutTransition(node: Element): TransitionConfig {
  return dropdownTransition(node, 50)
}
