<script lang='ts'>
  import type { Project } from '$lib/entities/project/model.js'
  import { projectGateway } from '$lib/entities/project/project-gateway.js'
  import ProjectCreateForm from '$lib/features/project/project-create-form.svelte'
  import { m } from '$lib/paraglide/messages.js'
  import { Alert, Badge, Card, CardContent, CardHeader, CardTitle, EmptyState, LoadingState, PageHeader } from '$lib/shared/ui'

  let projects = $state<Project[]>([])
  let loading = $state(true)
  let error = $state('')

  async function load() {
    loading = true
    error = ''
    try {
      projects = await projectGateway.list()
    }
    catch (caught) {
      error = caught instanceof Error ? caught.message : m.projects_load_failed()
    }
    finally {
      loading = false
    }
  }

  function addProject(project: Project) {
    projects = [...projects, project]
  }

  $effect(() => {
    void load()
  })
</script>

<svelte:head><title>{m.app_name()}</title></svelte:head>

<div class='space-y-8'><PageHeader title={m.projects()} description={m.projects_description()} />
  {#if error}<Alert variant='destructive'>{error}</Alert>{/if}
  {#if loading}<LoadingState label={m.projects_loading()} />
  {:else}
    <Card><CardHeader><CardTitle>{m.project_create()}</CardTitle></CardHeader><CardContent><ProjectCreateForm onCreated={addProject} /></CardContent></Card>
    {#if projects.length > 0}<div class='grid gap-4 md:grid-cols-2'>{#each projects as project (project.id)}<Card><CardHeader><div class='flex items-center justify-between gap-3'><CardTitle>{project.name}</CardTitle><Badge variant='secondary'>{project.identifier}</Badge></div></CardHeader><CardContent><p class='text-sm text-muted-foreground'>{project.description ?? m.project_no_description()}</p></CardContent></Card>{/each}</div>
    {:else}<EmptyState title={m.no_projects_yet()} description={m.projects_empty_description()} />{/if}
  {/if}
</div>
