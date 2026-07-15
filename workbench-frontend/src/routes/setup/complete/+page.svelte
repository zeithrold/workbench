<script lang='ts'>
  import { session } from '$lib/entities/session/session.svelte.js'
  import SignOutButton from '$lib/features/auth/sign-out-button.svelte'
  import { m } from '$lib/paraglide/messages.js'
  import { BrandLogo, Card, CardContent, CardDescription, CardFooter, CardHeader } from '$lib/shared/ui'
  import CheckCircleIcon from '@lucide/svelte/icons/circle-check'
</script>

<svelte:head><title>{m.initialized_title()}</title></svelte:head>

<main class='grid min-h-screen place-items-center bg-muted/30 p-6'>
  <Card class='w-full max-w-lg'>
    <CardHeader class='space-y-5'>
      <BrandLogo />
      <div class='grid size-12 place-items-center rounded-full bg-primary/10 text-primary'>
        <CheckCircleIcon class='size-6' />
      </div>
      <div class='space-y-1'>
        <h1 class='text-base leading-normal font-medium'>{m.initialized_title()}</h1>
        <CardDescription>{m.initialized_description()}</CardDescription>
      </div>
    </CardHeader>
    <CardContent>
      <dl class='grid gap-3 rounded-lg border bg-muted/20 p-4 text-sm'>
        <div class='flex justify-between gap-4'>
          <dt class='text-muted-foreground'>{m.administrator()}</dt>
          <dd class='font-medium'>{session.current?.user.displayName}</dd>
        </div>
        <div class='flex justify-between gap-4'>
          <dt class='text-muted-foreground'>{m.email()}</dt>
          <dd class='font-medium'>{session.current?.user.primaryEmail}</dd>
        </div>
      </dl>
      <p class='mt-4 text-sm leading-6 text-muted-foreground'>
        {m.setup_next_milestone()}
      </p>
    </CardContent>
    <CardFooter class='justify-end'><SignOutButton /></CardFooter>
  </Card>
</main>
