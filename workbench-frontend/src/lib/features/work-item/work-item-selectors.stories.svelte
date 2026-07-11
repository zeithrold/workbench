<script module lang='ts'>
  import { defineMeta } from '@storybook/addon-svelte-csf'
  import SystemFieldSelector from './system-field-selector.svelte'

  const { Story } = defineMeta({ title: 'Features/Work item/System selectors', component: SystemFieldSelector })
</script>

<script lang='ts'>
  import { priorities, sprints, statuses, users, workItemTypes } from './fixtures.js'

  let type = $state<string | null>('story')
  let status = $state<string | null>('progress')
  let assignee = $state<string | null>('ada')
  let priority = $state<string | null>('high')
  let sprint = $state<string | null>('s24')
</script>

{#snippet panel()}
  <div class='grid w-80 gap-4 rounded-xl border bg-card p-4 text-card-foreground shadow-sm'>
    <SystemFieldSelector kind='type' value={type} options={workItemTypes} onValueChange={value => type = value} required />
    <SystemFieldSelector kind='status' value={status} options={statuses} onValueChange={value => status = value} required />
    <SystemFieldSelector kind='assignee' value={assignee} options={users} onValueChange={value => assignee = value} />
    <SystemFieldSelector kind='priority' value={priority} options={priorities} onValueChange={value => priority = value} />
    <SystemFieldSelector kind='sprint' value={sprint} options={sprints} onValueChange={value => sprint = value} />
  </div>
{/snippet}

<Story name='Compact property rail' asChild>{@render panel()}</Story>
<Story name='Empty and clearable' asChild>
  <div class='w-80'><SystemFieldSelector kind='assignee' value={null} options={users} onValueChange={() => {}} /></div>
</Story>
<Story name='Disabled' asChild>
  <div class='w-80'><SystemFieldSelector kind='status' value='done' options={statuses} onValueChange={() => {}} disabled /></div>
</Story>
<Story name='No results' asChild>
  <div class='w-80'><SystemFieldSelector kind='sprint' value={null} options={[]} onValueChange={() => {}} /></div>
</Story>
