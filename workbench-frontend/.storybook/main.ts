import type { StorybookConfig } from '@storybook/sveltekit'

const config: StorybookConfig = {
  stories: ['../src/**/*.stories.svelte'],
  staticDirs: ['../static'],
  addons: ['@storybook/addon-svelte-csf', '@storybook/addon-a11y', '@storybook/addon-vitest'],
  framework: {
    name: '@storybook/sveltekit',
    options: {},
  },
}

export default config
