<script module lang='ts'>
  import * as Card from '$lib/components/ui/card'
  import { defineMeta } from '@storybook/addon-svelte-csf'

  const { Story } = defineMeta({ title: 'Shared/UI/Card', component: Card.Root })
</script>

<Story name='Default' asChild>
  <Card.Root class='w-96'>
    <Card.Header>
      <Card.Title>Project API</Card.Title>
      <Card.Description>Shared contract and generated client surface.</Card.Description>
    </Card.Header>
    <Card.Content>Ready for integration.</Card.Content>
  </Card.Root>
</Story>

<Story name='Compact' asChild>
  <Card.Root size='sm' class='w-80'>
    <Card.Header><Card.Title>Compact card</Card.Title></Card.Header>
    <Card.Content>Useful for dense dashboards.</Card.Content>
  </Card.Root>
</Story>
