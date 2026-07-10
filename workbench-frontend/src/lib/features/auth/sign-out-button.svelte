<script lang='ts'>
  import { goto } from '$app/navigation'
  import { resolve } from '$app/paths'
  import { session } from '$lib/entities/session/session.svelte.js'
  import { Button } from '$lib/shared/ui'

  async function signOut() {
    await session.signOut()
    await goto(resolve('/login'))
  }
</script>

<Button variant='ghost' size='sm' onclick={() => void signOut()} disabled={session.pending}>Sign out</Button>
