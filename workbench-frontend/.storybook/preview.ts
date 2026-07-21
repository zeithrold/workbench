import type { Preview } from '@storybook/sveltekit'
import { initialize, mswLoader } from 'msw-storybook-addon'
import '../src/app.css'

initialize({
  onUnhandledRequest(request, print) {
    if (new URL(request.url).pathname.startsWith('/api/'))
      print.error()
  },
})

const preview: Preview = {
  loaders: [mswLoader],
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
