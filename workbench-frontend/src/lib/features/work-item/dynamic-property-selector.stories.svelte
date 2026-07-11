<script module lang='ts'>
  import { defineMeta } from '@storybook/addon-svelte-csf'
  import DynamicPropertySelector from './dynamic-property-selector.svelte'

  const { Story } = defineMeta({ title: 'Features/Work item/Dynamic property editors', component: DynamicPropertySelector })
</script>

<script lang='ts'>
  import { customOptions, dynamicProperties, issues, manyIssues, projects, users } from './fixtures.js'

  let category = $state<string | null>('quality')
  let labels = $state(['customer', 'security', 'performance'])
  let reviewer = $state<string | null>('grace')
  let watchers = $state(['ada', 'linus', 'margaret'])
  let project = $state<string | null>('web')
  let issue = $state<string | null>('WEB-128')
  let customerReference = $state<string | number | boolean | null>('CRM-2048')
  let estimate = $state<string | number | boolean | null>(5.5)
  let billable = $state<string | number | boolean | null>(true)
  let targetDate = $state<string | number | boolean | null>('2026-08-15')
  let releaseAt = $state<string | number | boolean | null>('2026-08-15T02:30:00.000Z')
  let referenceUrl = $state<string | number | boolean | null>('https://example.com/spec')
</script>

{#snippet dynamicPanel()}
  <div class='grid w-[26rem] gap-4 rounded-xl border bg-card p-4 shadow-sm'>
    <DynamicPropertySelector definition={dynamicProperties.category} data={{ options: customOptions }} value={category} onValueChange={value => category = value} required />
    <DynamicPropertySelector definition={dynamicProperties.labels} data={{ options: customOptions }} values={labels} onValuesChange={value => labels = value} />
    <DynamicPropertySelector definition={dynamicProperties.reviewer} data={{ options: users }} value={reviewer} onValueChange={value => reviewer = value} />
    <DynamicPropertySelector definition={dynamicProperties.watchers} data={{ options: users }} values={watchers} onValuesChange={value => watchers = value} />
    <DynamicPropertySelector definition={dynamicProperties.project} data={{ options: projects }} value={project} onValueChange={value => project = value} />
    <DynamicPropertySelector definition={dynamicProperties.issue} data={{ options: issues }} value={issue} onValueChange={value => issue = value} />
  </div>
{/snippet}

<Story name='Configuration driven panel' asChild>{@render dynamicPanel()}</Story>
<Story name='Unsupported property' asChild>
  <div class='w-80'><DynamicPropertySelector definition={dynamicProperties.metadata} /></div>
</Story>
<Story name='Empty required value' asChild>
  <div class='w-80'><DynamicPropertySelector definition={dynamicProperties.category} data={{ options: customOptions }} value={null} required /></div>
</Story>
<Story name='Loading remote options' asChild>
  <div class='w-80'><DynamicPropertySelector definition={dynamicProperties.issue} data={{ options: [], loading: true }} /></div>
</Story>
<Story name='Scrollable remote results' asChild>
  <div class='w-80'><DynamicPropertySelector definition={dynamicProperties.issue} data={{ options: manyIssues }} value='WEB-158' /></div>
</Story>
<Story name='Scalar property editors' asChild>
  <div class='grid w-[26rem] gap-4 rounded-xl border bg-card p-4 shadow-sm'>
    <DynamicPropertySelector definition={dynamicProperties.title} scalarValue={customerReference} onScalarValueChange={value => customerReference = value} required />
    <DynamicPropertySelector definition={dynamicProperties.estimate} scalarValue={estimate} onScalarValueChange={value => estimate = value} />
    <DynamicPropertySelector definition={dynamicProperties.billable} scalarValue={billable} onScalarValueChange={value => billable = value} />
    <DynamicPropertySelector definition={dynamicProperties.targetDate} scalarValue={targetDate} onScalarValueChange={value => targetDate = value} />
    <DynamicPropertySelector definition={dynamicProperties.releaseAt} scalarValue={releaseAt} onScalarValueChange={value => releaseAt = value} />
    <DynamicPropertySelector definition={dynamicProperties.referenceUrl} scalarValue={referenceUrl} onScalarValueChange={value => referenceUrl = value} />
  </div>
</Story>
