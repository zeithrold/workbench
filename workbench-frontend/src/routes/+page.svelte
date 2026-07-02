<script lang='ts'>
  import { Badge } from '$lib/components/ui/badge'
  import { Button } from '$lib/components/ui/button'
  import * as Card from '$lib/components/ui/card'
  import { Input } from '$lib/components/ui/input'
  import { Label } from '$lib/components/ui/label'
  import { Separator } from '$lib/components/ui/separator'

  const apiVersionHeader = 'X-Workbench-API-Version'
</script>

<svelte:head>
  <title>Workbench</title>
</svelte:head>

<main class='min-h-screen bg-background p-6 text-foreground md:p-10'>
  <section class='mx-auto grid max-w-5xl gap-6'>
    <div class='space-y-3'>
      <Badge variant='secondary'>doa.ink.workbench</Badge>
      <div class='space-y-2'>
        <h1 class='text-3xl font-semibold tracking-normal'>Workbench</h1>
        <p class='max-w-2xl text-sm leading-6 text-muted-foreground'>
          SvelteKit frontend with shadcn-svelte components, OpenAPI client generation, and a Spring Boot backend.
        </p>
      </div>
    </div>

    <div class='grid gap-4 md:grid-cols-[1fr_22rem]'>
      <Card.Root>
        <Card.Header>
          <Card.Title>Project API</Card.Title>
          <Card.Description>Shared request contract and generated client surface.</Card.Description>
        </Card.Header>
        <Card.Content class='space-y-4'>
          <div class='grid gap-2'>
            <Label for='api-version'>API version header</Label>
            <Input id='api-version' value={apiVersionHeader} readonly />
          </div>
          <Separator />
          <div class='flex flex-wrap gap-2'>
            <Button>New project</Button>
            <Button variant='outline'>Open docs</Button>
            <Button variant='ghost'>Health check</Button>
          </div>
        </Card.Content>
      </Card.Root>

      <Card.Root size='sm'>
        <Card.Header>
          <Card.Title>UI Kit</Card.Title>
          <Card.Description>Core shadcn primitives are installed under <code>$lib/components/ui</code>.</Card.Description>
        </Card.Header>
        <Card.Content>
          <div class='flex flex-wrap gap-2'>
            <Badge>button</Badge>
            <Badge variant='outline'>card</Badge>
            <Badge variant='secondary'>input</Badge>
            <Badge variant='outline'>label</Badge>
            <Badge variant='secondary'>separator</Badge>
          </div>
        </Card.Content>
      </Card.Root>
    </div>
  </section>
</main>
