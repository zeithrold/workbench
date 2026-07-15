<script lang='ts'>
  import { session } from '$lib/entities/session/session.svelte.js'
  import { m } from '$lib/paraglide/messages.js'
  import { EmptyState, PageHeader } from '$lib/shared/ui'
</script>

<svelte:head><title>{m.app_name()}</title></svelte:head>

<div class='space-y-8'>
  <PageHeader title={m.overview()} description={m.workspace_ready()} />
  <EmptyState
    title={m.no_projects_yet()}
    description={m.create_project_for_tenant({ tenantName: session.current?.activeTenant?.name ?? '' })}
  />
</div>
