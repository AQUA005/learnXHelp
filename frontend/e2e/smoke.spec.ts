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

/**
 * Signs in with an email address. `/` is the public homepage now, so the form
 * lives at its own route rather than standing in for the whole application.
 */
async function signIn(page: import('@playwright/test').Page, email: string) {
  await page.goto('/signin')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill('password')
  // Scoped to the form: the header link to this page carries the same name.
  await page.locator('form').getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('link', { name: 'Dashboard' })).toBeVisible()
}

async function signOut(page: import('@playwright/test').Page) {
  await page.getByRole('button', { name: 'Sign out' }).click()
  // Signing out lands on the public homepage.
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Dashboard' })).toHaveCount(0)
}

test('a visitor sees the universities and can reach one of them', async ({ page }) => {
  await page.goto('/')

  // The seeded university is published, so the homepage is never empty.
  const university = page.getByRole('link', { name: /University of Science and Technology/ })
  await expect(university).toBeVisible()

  await university.click()
  await expect(page.getByRole('heading', { level: 1, name: /University of Science and Technology/ }))
    .toBeVisible()

  // And from there into a signup scoped to that university.
  await page.getByRole('link', { name: 'Create an account' }).click()
  await expect(page).toHaveURL(/university=ustc-ac-bd/)
  await expect(page.getByLabel('Email')).toBeVisible()
})

test('an unlisted university is not found', async ({ page }) => {
  await page.goto('/u/no-such-place')
  await expect(page.getByRole('heading', { name: /isn't listed/ })).toBeVisible()
})

test('a student can sign in and reach every screen', async ({ page }) => {
  await signIn(page, 'student@learnx.help')

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
  await signIn(page, 'student@learnx.help')

  await page.getByRole('link', { name: 'Exams', exact: true }).click()
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
  await signIn(page, 'student@learnx.help')

  await expect(page.getByRole('link', { name: 'Administration' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Gradebook' })).toHaveCount(0)

  // Typing the address directly returns them to the dashboard.
  await page.goto('/admin')
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Good')
})

test('an administrator sees the administration screens', async ({ page }) => {
  await signIn(page, 'admin@learnx.help')

  // Their dashboard is the administrator's, not the student's.
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Good')
  await expect(page.getByText('Awaiting approval')).toBeVisible()

  // Scoped to the sidebar: the dashboard also links to this screen.
  await page.getByLabel('Sections').getByRole('link', { name: 'Administration' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Administration' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Account approvals' })).toBeVisible()

  // The class list is a way into each class rather than a place to act on it.
  await page.getByRole('button', { name: 'Classes' }).click()
  await page.getByRole('link', { name: 'Open' }).first().click()
  await expect(page.getByRole('button', { name: 'Roster' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Courses & teachers' })).toBeVisible()
})

test('the platform owner sees the platform, not a class', async ({ page }) => {
  await signIn(page, 'master@learnx.com')

  // A platform owner belongs to no university, so the class-scoped screens are
  // not offered to them at all.
  await expect(page.getByLabel('Sections').getByRole('link', { name: 'Class routine' }))
    .toHaveCount(0)
  await expect(page.getByLabel('Sections').getByRole('link', { name: 'My results' }))
    .toHaveCount(0)

  await page.getByLabel('Sections').getByRole('link', { name: 'Platform' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Platform' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Universities' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Site branding' })).toBeVisible()
})

test('the health probe answers without a session', async ({ request }) => {
  const response = await request.get('/actuator/health')
  expect(response.ok()).toBeTruthy()
  expect((await response.json()).status).toBe('UP')
})
