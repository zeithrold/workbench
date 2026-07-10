<script lang='ts'>
  import { session } from '$lib/entities/session/session.svelte.js'
  import { EmptyState, PageHeader } from '$lib/shared/ui'
</script>

<svelte:head><title>Workbench</title></svelte:head>

<div class='space-y-8'>
  <PageHeader title='Overview' description='Your workspace is ready for its first project.' />
  <EmptyState title='No projects yet' description='Create a project when you are ready to organize work for {session.current?.activeTenant.name}.' />
</div>
