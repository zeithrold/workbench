<script lang='ts'>
  import SignInForm from '$lib/features/auth/sign-in-form.svelte'
  import { m } from '$lib/paraglide/messages.js'
  import { BrandLogo, Card, CardContent, CardDescription, CardHeader } from '$lib/shared/ui'
</script>

<svelte:head><title>{m.sign_in_title()}</title></svelte:head>

<main class='grid min-h-screen place-items-center bg-muted/30 p-6'>
  <Card class='w-full max-w-md'>
    <CardHeader class='space-y-4'>
      <BrandLogo />
      <div class='space-y-1'>
        <h1 class='text-base leading-normal font-medium'>{m.welcome_to_workbench()}</h1>
        <CardDescription>{m.sign_in_description()}</CardDescription>
      </div>
    </CardHeader>
    <CardContent><SignInForm /></CardContent>
  </Card>
</main>
