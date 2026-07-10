import type { Preview } from '@storybook/sveltekit'
import '../src/app.css'

const preview: Preview = {
  globalTypes: {
    theme: {
      description: 'Color theme',
      defaultValue: 'light',
      toolbar: {
        icon: 'mirror',
        items: [
          { value: 'light', title: 'Light' },
          { value: 'dark', title: 'Dark' },
        ],
      },
    },
  },
  decorators: [
    (story, context) => {
      document.documentElement.classList.toggle('dark', context.globals.theme === 'dark')
      return story()
    },
  ],
  parameters: {
    layout: 'centered',
    backgrounds: { disable: true },
    a11y: { test: 'error' },
  },
}

export default preview
