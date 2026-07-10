import adapter from '@sveltejs/adapter-auto'

const config = {
  compilerOptions: {
    runes: true,
  },
  vitePlugin: {
    dynamicCompileOptions: ({ filename }) => {
      if (filename.includes('@storybook/addon-svelte-csf/dist/runtime/Legacy'))
        return { runes: false }
    },
  },
  kit: {
    adapter: adapter(),
  },
}

export default config
