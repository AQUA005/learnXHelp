import { expect, test } from '@playwright/test'

/**
 * The path a student actually takes: sign in, see their day, open the routine
 * and the notes library, sit an exam, and sign out.
 *
 * Runs against the demo data the dev profile seeds.
 */

async function signIn(page: import('@playwright/test').Page, username: string) {
  await page.goto('/')
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill('password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('link', { name: 'Dashboard' })).toBeVisible()
}

test('a student can sign in and reach every screen', async ({ page }) => {
  await signIn(page, 'student')

  // The dashboard greets them and shows the day's summary.
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Good')

  await page.getByRole('link', { name: 'Class routine' }).click()
  await expect(page.getByRole('heading', { name: 'Class routine' })).toBeVisible()

  await page.getByRole('link', { name: 'Notes library' }).click()
  await expect(page.getByRole('heading', { name: 'Notes library' })).toBeVisible()

  await page.getByRole('link', { name: 'Announcements' }).click()
  await expect(page.getByRole('heading', { name: 'Announcements' })).toBeVisible()

  await page.getByRole('link', { name: 'My results' }).click()
  await expect(page.getByRole('heading', { name: 'My results' })).toBeVisible()

  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeVisible()
})

test('a student can sit an exam and see the result recorded', async ({ page }) => {
  await signIn(page, 'student')

  await page.getByRole('link', { name: 'Exams', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Exams' })).toBeVisible()

  const start = page.getByRole('link', { name: 'Start exam' }).first()
  test.skip(!(await start.isVisible()), 'No open exam in the seeded data')

  await start.click()
  await expect(page.getByText(/of \d+ answered/)).toBeVisible()

  // Answer every multiple-choice question with its first option.
  const groups = page.locator('.question')
  const count = await groups.count()
  for (let i = 0; i < count; i += 1) {
    const firstRadio = groups.nth(i).locator('input[type="radio"]').first()
    if (await firstRadio.isVisible()) {
      await firstRadio.check()
    } else {
      await groups.nth(i).locator('input[type="text"], input:not([type])').first().fill('answer')
    }
  }

  await page.getByRole('button', { name: 'Submit answers' }).click()

  // Back on the list, the exam now shows as submitted.
  await expect(page.getByText(/Submitted/)).toBeVisible()
})

test('a student cannot reach the administration screens', async ({ page }) => {
  await signIn(page, 'student')

  await expect(page.getByRole('link', { name: 'Administration' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Gradebook' })).toHaveCount(0)

  // Typing the address directly returns them to the dashboard.
  await page.goto('/admin')
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Good')
})

test('an administrator sees the administration screens', async ({ page }) => {
  await signIn(page, 'admin')

  await page.getByRole('link', { name: 'Administration' }).click()
  await expect(page.getByRole('heading', { name: 'Administration' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Account approvals' })).toBeVisible()
})

test('the health probe answers without a session', async ({ request }) => {
  const response = await request.get('/actuator/health')
  expect(response.ok()).toBeTruthy()
  expect((await response.json()).status).toBe('UP')
})
