import { expect, test } from './fixtures'

test('demo user can sign in, switch tenant, and sign out', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByText('Welcome to Workbench')).toBeVisible()

  await page.getByRole('button', { name: 'Continue with demo account' }).click()
  await expect(page.getByRole('heading', { name: 'Overview' })).toBeVisible()

  await page.getByLabel('Active tenant').selectOption('workbench')
  await expect(page.getByRole('banner').getByText('Workbench Labs', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByText('Welcome to Workbench')).toBeVisible()
})
