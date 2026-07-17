import { fireEvent, render, screen, waitFor } from '@testing-library/svelte'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProjectCreateForm from './project-create-form.svelte'

const { create } = vi.hoisted(() => ({ create: vi.fn() }))

vi.mock('$lib/entities/project/project-gateway.js', () => ({
  projectGateway: { create },
}))

describe('projectCreateForm', () => {
  beforeEach(() => vi.clearAllMocks())

  it('normalizes the identifier and returns the created project', async () => {
    const project = { id: 'prj_1', identifier: 'CORE', name: 'Core', status: 'active' }
    create.mockResolvedValue(project)
    const onCreated = vi.fn()
    render(ProjectCreateForm, { onCreated })

    await fireEvent.input(screen.getByLabelText('Project name'), { target: { value: 'Core' } })
    await fireEvent.input(screen.getByLabelText('Identifier'), { target: { value: 'core' } })
    await fireEvent.click(screen.getByRole('button', { name: 'Create project' }))

    await waitFor(() => expect(create).toHaveBeenCalledWith({
      name: 'Core',
      identifier: 'CORE',
      description: undefined,
    }))
    expect(onCreated).toHaveBeenCalledWith(project)
  })
})
