<script lang='ts'>
  const apiVersionHeader = 'X-Workbench-API-Version'
</script>

<svelte:head>
  <title>Workbench</title>
</svelte:head>

<main class='min-h-screen bg-background p-8 text-foreground'>
  <section class='mx-auto max-w-3xl space-y-4'>
    <p class='text-sm uppercase tracking-wide text-muted-foreground'>doa.ink.workbench</p>
    <h1 class='text-4xl font-semibold'>Workbench</h1>
    <p>
      Multi-module Spring Boot 4 and SvelteKit monolith scaffold. API clients are generated from OpenAPI with Orval.
    </p>
    <code>{apiVersionHeader}</code>
  </section>
</main>
