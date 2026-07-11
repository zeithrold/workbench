<script module lang='ts'>
  import { defineMeta } from '@storybook/addon-svelte-csf'
  import DynamicPropertySelector from './dynamic-property-selector.svelte'

  const { Story } = defineMeta({ title: 'Features/Work item/Property editor', component: DynamicPropertySelector })
</script>

<script lang='ts'>
  import { customOptions, dynamicProperties, issues, priorities, sprints, statuses, users, workItemTypes } from './fixtures.js'
  import SystemFieldSelector from './system-field-selector.svelte'

  let type = $state<string | null>('story')
  let status = $state<string | null>('progress')
  let assignee = $state<string | null>('ada')
  let priority = $state<string | null>('high')
  let sprint = $state<string | null>('s24')
  let category = $state<string | null>('quality')
  let labels = $state(['customer', 'performance'])
  let watchers = $state(['grace'])
  let issue = $state<string | null>('WEB-131')
</script>

<Story name='System and dynamic properties' asChild>
  <section class='w-[46rem] max-w-[calc(100vw-2rem)] rounded-2xl border bg-card p-6 shadow-sm'>
    <header class='mb-5'><p class='text-xs font-medium text-muted-foreground'>WEB-142 · Story</p><h2 class='mt-1 text-lg font-semibold'>Design configurable work item selectors</h2></header>
    <div class='grid gap-x-6 gap-y-4 sm:grid-cols-2'>
      <SystemFieldSelector kind='type' value={type} options={workItemTypes} onValueChange={value => type = value} required />
      <SystemFieldSelector kind='status' value={status} options={statuses} onValueChange={value => status = value} required />
      <SystemFieldSelector kind='assignee' value={assignee} options={users} onValueChange={value => assignee = value} />
      <SystemFieldSelector kind='priority' value={priority} options={priorities} onValueChange={value => priority = value} />
      <SystemFieldSelector kind='sprint' value={sprint} options={sprints} onValueChange={value => sprint = value} />
      <DynamicPropertySelector definition={dynamicProperties.category} data={{ options: customOptions }} value={category} onValueChange={value => category = value} />
      <DynamicPropertySelector definition={dynamicProperties.labels} data={{ options: customOptions }} values={labels} onValuesChange={value => labels = value} />
      <DynamicPropertySelector definition={dynamicProperties.watchers} data={{ options: users }} values={watchers} onValuesChange={value => watchers = value} />
      <div class='sm:col-span-2'><DynamicPropertySelector definition={dynamicProperties.issue} data={{ options: issues }} value={issue} onValueChange={value => issue = value} /></div>
    </div>
  </section>
</Story>
