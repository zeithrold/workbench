<script lang='ts'>
  import type { Project } from '$lib/entities/project/model.js'
  import { projectGateway } from '$lib/entities/project/project-gateway.js'
  import { m } from '$lib/paraglide/messages.js'
  import { Alert, Button, Input, Label } from '$lib/shared/ui'

  const { onCreated }: { onCreated?: (project: Project) => void } = $props()
  let name = $state('')
  let identifier = $state('')
  let description = $state('')
  let pending = $state(false)
  let error = $state('')

  async function create(event: SubmitEvent) {
    event.preventDefault()
    pending = true
    error = ''
    try {
      const project = await projectGateway.create({
        name: name.trim(),
        identifier: identifier.trim().toUpperCase(),
        description: description.trim() || undefined,
      })
      name = ''
      identifier = ''
      description = ''
      onCreated?.(project)
    }
    catch (caught) {
      error = caught instanceof Error ? caught.message : m.project_create_failed()
    }
    finally {
      pending = false
    }
  }
</script>

<form class='grid gap-4' onsubmit={create}>
  <div class='grid gap-4 md:grid-cols-[minmax(0,1fr)_12rem]'>
    <div><Label for='project-name'>{m.project_name()}</Label><Input id='project-name' bind:value={name} required disabled={pending} /></div>
    <div><Label for='project-identifier'>{m.project_identifier()}</Label><Input id='project-identifier' bind:value={identifier} pattern={'[A-Za-z][A-Za-z0-9]{1,9}'} maxlength={10} required disabled={pending} /></div>
  </div>
  <div><Label for='project-description'>{m.project_description()}</Label><Input id='project-description' bind:value={description} disabled={pending} /></div>
  {#if error}<Alert variant='destructive'>{error}</Alert>{/if}
  <Button class='w-fit' type='submit' disabled={pending}>{pending ? m.project_creating() : m.project_create()}</Button>
</form>
