<script module lang='ts'>
  import { defineMeta } from '@storybook/addon-svelte-csf'
  import ProjectCreateForm from './project-create-form.svelte'

  const { Story } = defineMeta({
    title: 'Features/Project/Project create form',
    component: ProjectCreateForm,
  })
</script>

<Story name='New project' asChild>
  <div class='rounded-xl border bg-background p-6 shadow-sm'>
    <ProjectCreateForm />
  </div>
</Story>
