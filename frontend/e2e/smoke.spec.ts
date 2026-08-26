import { expect, test } from '@playwright/test'

/**
 * The path a student actually takes: sign in, see their day, open the routine
 * and the notes library, sit an exam, and sign out.
 *
 * Runs against the demo data the dev profile seeds.
 *
 * Page headings are addressed by level. A card heading can carry the same
 * words as the page it sits on, and does while its data loads, so matching on
 * text alone made the outcome depend on how quickly the server replied.
 */

async function signIn(page: import('@playwright/test').Page, username: string) {
  await page.goto('/')
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill('password')
  // Scoped to the form: the tab that selects this panel carries the same name.
  await page.locator('form').getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('link', { name: 'Home' })).toBeVisible()
}

async function signOut(page: import('@playwright/test').Page) {
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.locator('form').getByRole('button', { name: 'Sign in' })).toBeVisible()
}

test('a student can sign in and reach every screen', async ({ page }) => {
  await signIn(page, 'student')

  // The dashboard greets them and shows the day's summary.
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Good')

  await page.getByRole('link', { name: 'Class routine' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Class routine' })).toBeVisible()

  await page.getByRole('link', { name: 'Notes library' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Notes library' })).toBeVisible()

  await page.getByRole('link', { name: 'Announcements' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Announcements' })).toBeVisible()

  await page.getByRole('link', { name: 'My results' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'My results' })).toBeVisible()

  await signOut(page)
})

test('a student can sit an exam and see the result recorded', async ({ page }) => {
  await signIn(page, 'student')

  await page.getByRole('link', { name: 'Online exams' }).click()
  // The page heading, addressed by level so a card title can never collide.
  await expect(page.getByRole('heading', { level: 1, name: 'Exams' })).toBeVisible()

  // The seeded data always contains an exam. Asserting that rather than
  // skipping means a broken exam list fails here instead of passing quietly.
  await expect(page.getByText(/\d+ exams?/)).toBeVisible()

  const start = page.getByRole('link', { name: 'Start exam' }).first()
  if ((await start.count()) === 0) {
    // A previous run against the same database already sat this exam.
    await expect(page.getByText(/Submitted · \d+ marks?/)).toBeVisible()
    return
  }

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

  // Back on the list, the exam card now carries the mark. Matched on the
  // badge's wording rather than "Submitted" alone, which also appears in the
  // confirmation toast.
  await expect(page.getByText(/Submitted · \d+ marks?/)).toBeVisible()
  await expect(page.getByRole('link', { name: 'Start exam' })).toHaveCount(0)
})

test('a student cannot reach the administration screens', async ({ page }) => {
  await signIn(page, 'student')

  await expect(page.getByRole('link', { name: 'Account approvals' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Gradebook' })).toHaveCount(0)

  // Typing the address directly returns them to the dashboard.
  await page.goto('/admin')
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Good')
})

test('an administrator sees the administration screens', async ({ page }) => {
  await signIn(page, 'admin')

  await page.getByRole('link', { name: 'Account approvals' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Administration' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Account approvals' })).toBeVisible()
})

test('each role gets its own navigation, not a longer one', async ({ page }) => {
  await signIn(page, 'teacher')

  // A teacher's sidebar is built around teaching: no "My results", which only
  // a student has, and the marking screens instead.
  await expect(page.getByRole('link', { name: 'Routine & class tests' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Gradebook' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Note approvals' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'My results' })).toHaveCount(0)

  await signOut(page)
  await signIn(page, 'cr')

  // A class representative keeps the routine, so the entry says so.
  await expect(page.getByRole('link', { name: 'Routine & test slots' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Gradebook' })).toHaveCount(0)

  await signOut(page)
  await signIn(page, 'admin')

  // An administrator gets the admin screens as separate entries, and none of
  // the study ones.
  await expect(page.getByRole('link', { name: 'Master routine' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Notes library' })).toHaveCount(0)

  await page.getByRole('link', { name: 'Change history' }).click()
  await expect(page).toHaveURL(/\/admin\?view=audit$/)
  await expect(page.getByText('Changes to the routine and class tests')).toBeVisible()

  // Those entries all address /admin and differ only by the view they open,
  // so exactly one of them may be marked as the current page.
  const current = page.locator('.sidebar-nav [aria-current="page"]')
  await expect(current).toHaveCount(1)
  await expect(current).toHaveText('Change history')
})

test('the health probe answers without a session', async ({ request }) => {
  const response = await request.get('/actuator/health')
  expect(response.ok()).toBeTruthy()
  expect((await response.json()).status).toBe('UP')
})
