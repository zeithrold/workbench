<script lang='ts'>
  import { goto } from '$app/navigation'
  import { resolve } from '$app/paths'
  import { session } from '$lib/entities/session/session.svelte.js'
  import { Button, Input, Label } from '$lib/shared/ui'

  let email = $state('demo@workbench.local')

  async function submit() {
    await session.signIn({ email })
    await goto(resolve('/'))
  }

  function handleSubmit(event: SubmitEvent) {
    event.preventDefault()
    void submit()
  }
</script>

<form class='grid gap-5' onsubmit={handleSubmit}>
  <div class='grid gap-2'>
    <Label for='email'>Email</Label>
    <Input id='email' type='email' bind:value={email} placeholder='you@example.com' required />
    <p class='text-xs text-muted-foreground'>This demo creates an in-memory session and resets on refresh.</p>
  </div>
  <Button type='submit' disabled={session.pending}>
    {session.pending ? 'Signing in…' : 'Continue with demo account'}
  </Button>
</form>
