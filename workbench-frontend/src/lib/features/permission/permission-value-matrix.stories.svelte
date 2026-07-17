<script module lang='ts'>
  import type { PermissionConditionOperator, PermissionConditionValue } from './permission-document.js'
  import type { PermissionFieldOption, PermissionFieldType, PermissionValueEditorKind } from './permission-editor-model.js'
  import { defineMeta } from '@storybook/addon-svelte-csf'
  import { expect, userEvent, waitFor, within } from 'storybook/test'
  import { defaultPermissionValue, normalizePermissionOperator, permissionOperatorLabel, permissionValueEditorKind } from './permission-editor-model.js'
  import PermissionValueMatrixStoryHost from './permission-value-matrix-story-host.svelte'

  const userValues = [
    { id: 'current-user', label: 'Current user', value: { var: 'user.currentUser' } as const },
    { id: 'ada', label: 'Ada Lovelace', description: 'ada@example.com', value: 'user-ada' },
  ]
  const referenceValues = [
    { id: 'project-alpha', label: 'Project Alpha', value: 'project-alpha' },
    { id: 'project-beta', label: 'Project Beta', value: 'project-beta' },
  ]
  const statusValues = [
    { id: 'started', label: 'Started', color: '#f59e0b', value: 'started' },
    { id: 'completed', label: 'Completed', color: '#22c55e', value: 'completed' },
  ]
  const labelValues = [
    { id: 'backend', label: 'Backend', color: '#3b82f6', value: 'backend' },
    { id: 'urgent', label: 'Urgent', color: '#ef4444', value: 'urgent' },
  ]

  const operatorsByFieldType = {
    'text': ['eq', 'neq', 'contains', 'not_contains', 'is_empty', 'is_not_empty'],
    'number': ['eq', 'neq', 'gt', 'gte', 'lt', 'lte', 'is_empty', 'is_not_empty'],
    'boolean': ['eq', 'neq'],
    'date': ['eq', 'neq', 'gt', 'gte', 'lt', 'lte', 'is_empty', 'is_not_empty'],
    'user': ['eq', 'neq', 'in', 'not_in', 'is_empty', 'is_not_empty'],
    'reference': ['eq', 'neq', 'in', 'not_in', 'is_empty', 'is_not_empty'],
    'single-select': ['eq', 'neq', 'in', 'not_in', 'is_empty', 'is_not_empty'],
    'multi-select': ['eq', 'neq', 'has_any', 'has_all', 'has_none', 'is_empty', 'is_not_empty'],
  } as const satisfies Record<PermissionFieldType, readonly PermissionConditionOperator[]>

  const expectedFieldTypes = Object.keys({
    'text': true,
    'number': true,
    'boolean': true,
    'date': true,
    'user': true,
    'reference': true,
    'single-select': true,
    'multi-select': true,
  } satisfies Record<PermissionFieldType, true>) as PermissionFieldType[]
  const expectedOperators = Object.keys({
    eq: true,
    neq: true,
    in: true,
    not_in: true,
    is_empty: true,
    is_not_empty: true,
    gt: true,
    gte: true,
    lt: true,
    lte: true,
    contains: true,
    not_contains: true,
    has_any: true,
    has_all: true,
    has_none: true,
  } satisfies Record<PermissionConditionOperator, true>) as PermissionConditionOperator[]

  const fields = [
    { id: 'property.summary', label: 'Summary', field: { kind: 'property', code: 'summary' }, type: 'text', operators: [...operatorsByFieldType.text], defaultValue: 'Initial summary' },
    { id: 'property.story-points', label: 'Story points', field: { kind: 'property', code: 'story-points' }, type: 'number', operators: [...operatorsByFieldType.number], defaultValue: 3 },
    { id: 'property.approved', label: 'Approved', field: { kind: 'property', code: 'approved' }, type: 'boolean', operators: [...operatorsByFieldType.boolean], defaultValue: true },
    { id: 'property.due-date', label: 'Due date', field: { kind: 'property', code: 'due-date' }, type: 'date', operators: [...operatorsByFieldType.date], defaultValue: '2026-07-16' },
    { id: 'issue.assignee', label: 'Assignee', field: 'issue.assignee', type: 'user', operators: [...operatorsByFieldType.user], values: userValues, defaultValue: { var: 'user.currentUser' } },
    { id: 'property.project', label: 'Project', field: { kind: 'property', code: 'project' }, type: 'reference', operators: [...operatorsByFieldType.reference], values: referenceValues, defaultValue: 'project-alpha' },
    { id: 'issue.statusGroup', label: 'Status category', field: 'issue.statusGroup', type: 'single-select', operators: [...operatorsByFieldType['single-select']], values: statusValues, defaultValue: 'started' },
    { id: 'property.labels', label: 'Labels', field: { kind: 'property', code: 'labels' }, type: 'multi-select', operators: [...operatorsByFieldType['multi-select']], values: labelValues, defaultValue: ['backend'] },
  ] satisfies PermissionFieldOption[]

  interface ValueMatrixCase {
    id: string
    label: string
    field: PermissionFieldOption
    operator: PermissionConditionOperator
    initialOperator: PermissionConditionOperator
    initialValue?: PermissionConditionValue
  }

  const cases: ValueMatrixCase[] = fields.flatMap(field => field.operators.map((operator, index) => {
    const initialOperator = operator === 'is_empty' || operator === 'is_not_empty'
      ? field.operators.find(candidate => candidate !== 'is_empty' && candidate !== 'is_not_empty') ?? operator
      : field.operators[(index + 1) % field.operators.length] ?? operator
    const initialValue = field.type === 'user' && operator === 'neq'
      ? 'user-ada'
      : defaultPermissionValue(field, initialOperator)
    return {
      id: `${field.type}-${operator.replaceAll('_', '-')}`,
      label: `${field.label} · ${permissionOperatorLabel(operator)}`,
      field,
      operator,
      initialOperator,
      initialValue,
    }
  }))

  const { Story } = defineMeta({
    title: 'Features/Permission/Permission Value matrix',
    component: PermissionValueMatrixStoryHost,
    parameters: { layout: 'fullscreen', a11y: { test: 'todo' } },
  })

  function jsonFor(canvas: ReturnType<typeof within>, matrixCase: ValueMatrixCase) {
    return canvas.getByTestId(`value-json-${matrixCase.id}`)
  }

  function valueRegion(region: ReturnType<typeof within>) {
    return within(region.getByText('Value', { exact: true }).closest('[data-slot="field"]') as HTMLElement)
  }

  async function selectOperator(
    region: ReturnType<typeof within>,
    page: ReturnType<typeof within>,
    pageElement: HTMLElement,
    matrixCase: ValueMatrixCase,
  ) {
    await userEvent.click(region.getByRole('button', { name: permissionOperatorLabel(matrixCase.initialOperator) }))
    await userEvent.click(page.getByRole('option', { name: permissionOperatorLabel(matrixCase.operator), exact: true }))
    await waitFor(() => expect(page.queryByRole('option', { name: permissionOperatorLabel(matrixCase.operator), exact: true })).not.toBeInTheDocument())
    await waitFor(() => expect(getComputedStyle(pageElement).pointerEvents).not.toBe('none'))
  }

  async function updateTextValue(region: ReturnType<typeof within>, expected: string) {
    const input = region.getByRole('textbox')
    await userEvent.clear(input)
    await userEvent.type(input, expected)
  }

  async function updateNumberValue(region: ReturnType<typeof within>, expected: number) {
    const input = region.getByRole('spinbutton')
    await userEvent.clear(input)
    await userEvent.type(input, String(expected))
  }

  async function updateBooleanValue(region: ReturnType<typeof within>) {
    await userEvent.click(region.getByRole('radio', { name: 'No' }))
  }

  async function updateDateValue(region: ReturnType<typeof within>, page: ReturnType<typeof within>) {
    await userEvent.click(region.getByRole('button', { name: 'Value' }))
    const calendar = page.getByRole('application')
    await userEvent.click(within(calendar).getByRole('button', { name: /Monday, July 20, 2026/ }))
  }

  async function updateSingleValue(
    region: ReturnType<typeof within>,
    page: ReturnType<typeof within>,
    matrixCase: ValueMatrixCase,
  ): Promise<PermissionConditionValue> {
    const options = matrixCase.field.values ?? []
    const option = matrixCase.field.type === 'user' && matrixCase.operator === 'neq' ? options[0] : options[1]
    await userEvent.click(valueRegion(region).getByRole('button'))
    await userEvent.click(page.getByRole('option', { name: new RegExp(option.label) }))
    return option.value
  }

  async function updateMultiValue(
    region: ReturnType<typeof within>,
    page: ReturnType<typeof within>,
    matrixCase: ValueMatrixCase,
  ): Promise<PermissionConditionValue> {
    const options = matrixCase.field.values ?? []
    const trigger = valueRegion(region).getByRole('button')
    await userEvent.click(trigger)
    await userEvent.click(page.getByRole('option', { name: new RegExp(options[0].label) }))
    await userEvent.click(page.getByRole('option', { name: new RegExp(options[1].label) }))
    await userEvent.keyboard('{Escape}')
    return options.map(option => option.value)
  }

  async function valueMatrixPlay({ canvasElement }: { canvasElement: HTMLElement }) {
    const canvas = within(canvasElement)
    const page = within(canvasElement.ownerDocument.body)
    const actualFieldTypes = new Set(fields.map(field => field.type))
    const actualOperators = new Set(Object.values(operatorsByFieldType).flat())

    await expect([...actualFieldTypes].sort()).toEqual([...expectedFieldTypes].sort())
    await expect([...actualOperators].sort()).toEqual([...expectedOperators].sort())
    await expect(canvas.getAllByTestId(/^value-case-/)).toHaveLength(cases.length)

    for (const matrixCase of cases) {
      const region = within(canvas.getByTestId(`value-case-${matrixCase.id}`))
      await selectOperator(region, page, canvasElement.ownerDocument.body, matrixCase)
      const kind: PermissionValueEditorKind = permissionValueEditorKind(matrixCase.field, matrixCase.operator)
      const normalized = normalizePermissionOperator({
        field: matrixCase.field.field,
        op: matrixCase.initialOperator,
        ...(matrixCase.initialValue === undefined ? {} : { value: matrixCase.initialValue }),
      }, matrixCase.field, matrixCase.operator)
      await waitFor(() => expect(JSON.parse(jsonFor(canvas, matrixCase).textContent ?? '{}').value).toEqual(normalized.value))

      if (kind === 'none') {
        await expect(region.queryByText('Value', { exact: true })).not.toBeInTheDocument()
        await expect(jsonFor(canvas, matrixCase)).not.toHaveTextContent('"value"')
        continue
      }

      let expected: PermissionConditionValue
      if (kind === 'text') {
        expected = `${matrixCase.operator} text`
        await updateTextValue(region, expected)
      }
      else if (kind === 'number') {
        expected = 42
        await updateNumberValue(region, expected)
      }
      else if (kind === 'boolean') {
        expected = false
        await updateBooleanValue(region)
      }
      else if (kind === 'date') {
        expected = '2026-07-20'
        await updateDateValue(region, page)
      }
      else if (kind === 'single') {
        expected = await updateSingleValue(region, page, matrixCase)
      }
      else {
        expected = await updateMultiValue(region, page, matrixCase)
      }

      await waitFor(() => expect(JSON.parse(jsonFor(canvas, matrixCase).textContent ?? '{}').value).toEqual(expected))
    }

    const userLiteral = cases.find(matrixCase => matrixCase.field.type === 'user' && matrixCase.operator === 'eq')
    const userVariable = cases.find(matrixCase => matrixCase.field.type === 'user' && matrixCase.operator === 'neq')
    const enumCase = cases.find(matrixCase => matrixCase.field.type === 'single-select' && matrixCase.operator === 'eq')
    await expect(JSON.parse(jsonFor(canvas, userLiteral!).textContent ?? '{}').value).toBe('user-ada')
    await expect(JSON.parse(jsonFor(canvas, userVariable!).textContent ?? '{}').value).toEqual({ var: 'user.currentUser' })
    await expect(within(canvas.getByTestId(`value-case-${enumCase!.id}`)).getByRole('button', { name: /Completed/ }).querySelector('[style*="background-color: rgb(34, 197, 94)"]')).toBeInTheDocument()
  }
</script>

<Story name='All field and operator combinations' args={{ cases, fields }} play={valueMatrixPlay} />
