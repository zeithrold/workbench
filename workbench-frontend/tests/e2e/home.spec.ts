import { expect, test } from '@playwright/test'

test('demo user can sign in, switch tenant, and sign out', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Welcome to Workbench' })).toBeVisible()

  await page.getByRole('button', { name: 'Continue with demo account' }).click()
  await expect(page.getByRole('heading', { name: 'Overview' })).toBeVisible()

  await page.getByLabel('Active tenant').selectOption('workbench')
  await expect(page.getByText('Workbench Labs', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('heading', { name: 'Welcome to Workbench' })).toBeVisible()
})
