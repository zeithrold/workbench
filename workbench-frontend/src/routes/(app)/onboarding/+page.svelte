<script lang='ts'>
  import type { ManagedInvitation } from '$lib/entities/management/model.js'
  import type { Project } from '$lib/entities/project/model.js'
  import { goto } from '$app/navigation'
  import { resolve } from '$app/paths'
  import { page } from '$app/state'
  import { managementGateway } from '$lib/entities/management/management-gateway.js'
  import { session } from '$lib/entities/session/session.svelte.js'
  import InvitationForm from '$lib/features/invitation/invitation-form.svelte'
  import ProjectCreateForm from '$lib/features/project/project-create-form.svelte'
  import { m } from '$lib/paraglide/messages.js'
  import { Badge, Button, Card, CardContent, CardDescription, CardHeader, CardTitle, PageHeader } from '$lib/shared/ui'

  const step = $derived(page.url.searchParams.get('step') === 'project' ? 'project' : 'members')
  let pendingInvitations = $state<ManagedInvitation[]>([])

  async function loadInvitations() {
    pendingInvitations = await managementGateway.invitations()
  }

  async function showProjectStep() {
    await goto(resolve('/onboarding?step=project'), { replaceState: false })
  }

  async function finish(_project: Project) {
    await goto(resolve('/'))
  }

  $effect(() => {
    if (step === 'members')
      void loadInvitations()
  })
</script>

<svelte:head><title>{m.onboarding_title()}</title></svelte:head>

<div class='space-y-8'>
  <PageHeader title={m.onboarding_title()} description={m.onboarding_description({ tenantName: session.current?.activeTenant?.name ?? '' })} />
  <ol class='grid gap-3 md:grid-cols-3'>
    <li class='rounded-lg border bg-muted/20 p-4'><Badge>{m.done()}</Badge><p class='mt-2 font-medium'>{m.onboarding_tenant_created()}</p></li>
    <li class:!border-primary={step === 'members'} class='rounded-lg border p-4'><Badge variant={step === 'members' ? 'default' : 'secondary'}>2</Badge><p class='mt-2 font-medium'>{m.onboarding_invite_members()}</p></li>
    <li class:!border-primary={step === 'project'} class='rounded-lg border p-4'><Badge variant={step === 'project' ? 'default' : 'secondary'}>3</Badge><p class='mt-2 font-medium'>{m.onboarding_create_project()}</p></li>
  </ol>

  {#if step === 'members'}
    <Card><CardHeader><CardTitle>{m.onboarding_invite_members()}</CardTitle><CardDescription>{m.onboarding_invite_optional()}</CardDescription></CardHeader><CardContent><InvitationForm onInvited={() => void loadInvitations()} />{#if pendingInvitations.length > 0}<div class='mt-6 space-y-2'><p class='text-sm font-medium'>{m.management_pending_invitations()}</p>{#each pendingInvitations as invitation (invitation.id)}<div class='flex items-center justify-between rounded-lg border px-3 py-2 text-sm'><span>{invitation.displayName ?? invitation.email}</span><span class='text-xs text-muted-foreground'>{invitation.email}</span></div>{/each}</div>{/if}<div class='mt-6 flex justify-end'><Button onclick={() => void showProjectStep()}>{m.continue_to_project()}</Button></div></CardContent></Card>
  {:else}
    <Card><CardHeader><CardTitle>{m.onboarding_create_project()}</CardTitle><CardDescription>{m.onboarding_project_description()}</CardDescription></CardHeader><CardContent><ProjectCreateForm onCreated={project => void finish(project)} /></CardContent></Card>
  {/if}
</div>
