<script lang='ts'>
  import { browser } from '$app/environment'
  import { goto } from '$app/navigation'
  import { resolve } from '$app/paths'
  import { session } from '$lib/entities/session/session.svelte.js'
  import SignInForm from '$lib/features/auth/sign-in-form.svelte'
  import { BrandLogo, Card, CardContent, CardDescription, CardHeader, CardTitle } from '$lib/shared/ui'

  $effect(() => {
    if (browser && session.current)
      void goto(resolve('/'))
  })
</script>

<svelte:head><title>Sign in · Workbench</title></svelte:head>

<main class='grid min-h-screen place-items-center bg-muted/30 p-6'>
  <Card class='w-full max-w-md'>
    <CardHeader class='space-y-4'>
      <BrandLogo />
      <div class='space-y-1'>
        <CardTitle>Welcome to Workbench</CardTitle>
        <CardDescription>Sign in to explore the demo application shell.</CardDescription>
      </div>
    </CardHeader>
    <CardContent><SignInForm /></CardContent>
  </Card>
</main>
