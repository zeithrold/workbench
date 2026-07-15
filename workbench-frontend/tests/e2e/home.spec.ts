import { expect, test } from './fixtures'

test('instance administrator can initialize, restore, sign out, and sign back in', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Initialize Workbench' })).toBeVisible()

  await page.getByLabel('Display name').fill('Bootstrap Admin')
  await page.getByLabel('Email address').fill('bootstrap-admin@example.test')
  await page.getByLabel(/^Password/).fill('secure-password-1')
  await page.getByLabel('Confirm password').fill('secure-password-1')
  await page.getByRole('button', { name: 'Initialize Workbench' }).click()

  await expect(page.getByRole('heading', { name: 'Workbench is initialized' })).toBeVisible()
  await expect(page.getByText('bootstrap-admin@example.test')).toBeVisible()

  await page.reload()
  await expect(page.getByRole('heading', { name: 'Workbench is initialized' })).toBeVisible()

  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('heading', { name: 'Welcome to Workbench' })).toBeVisible()

  await page.getByLabel('Email address').fill('bootstrap-admin@example.test')
  await page.getByRole('button', { name: 'Continue' }).click()
  await page.getByLabel(/^Password/).fill('secure-password-1')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'Workbench is initialized' })).toBeVisible()
})

test('an initialized tenant session can enter the application shell', async ({ page }) => {
  const tenant = { id: 'ten_1', name: 'Northstar Studio', slug: 'northstar' }
  const user = { id: 'usr_1', displayName: 'Alex', primaryEmail: 'alex@example.test' }

  await page.route('**/api/instance/setup-status', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ initialized: true, setupTokenRequired: false }),
  }))
  await page.route('**/api/session', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      user,
      activeTenant: tenant,
      sessionExpiresAt: '2099-01-01T00:00:00Z',
      adminScopes: [],
    }),
  }))
  await page.route('**/api/auth/memberships', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify([{ id: 'tmb_1', tenant, tenantAdmin: true }]),
  }))

  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Overview' })).toBeVisible()
  await expect(page.getByRole('banner').getByText('Northstar Studio')).toBeVisible()
})
