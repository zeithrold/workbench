<script lang='ts'>
  import InstanceSetupForm from '$lib/features/instance-setup/instance-setup-form.svelte'
  import { m } from '$lib/paraglide/messages.js'
  import { BrandLogo, Card, CardContent, CardDescription, CardHeader } from '$lib/shared/ui'
</script>

<svelte:head><title>{m.initialize_workbench_title()}</title></svelte:head>

<main class='grid min-h-screen place-items-center bg-muted/30 p-6'>
  <Card class='w-full max-w-lg'>
    <CardHeader class='space-y-4'>
      <BrandLogo />
      <div class='space-y-1'>
        <h1 class='text-base leading-normal font-medium'>{m.initialize_workbench_title()}</h1>
        <CardDescription>{m.initialize_workbench_description()}</CardDescription>
      </div>
    </CardHeader>
    <CardContent><InstanceSetupForm /></CardContent>
  </Card>
</main>
