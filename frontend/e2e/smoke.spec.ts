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
  // Exact: the button that reveals what was typed is labelled "Show password",
  // which a substring match would also find.
  await page.getByLabel('Password', { exact: true }).fill('password')
  await page.getByRole('button', { name: 'Log in' }).click()
  await expect(page.getByRole('link', { name: 'Home' })).toBeVisible()
}

async function signOut(page: import('@playwright/test').Page) {
  await page.getByRole('button', { name: 'Sign out' }).click()
  // Signing out lands on the public homepage.
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Home' })).toHaveCount(0)
}

test('a visitor sees the universities and can reach one of them', async ({ page }) => {
  await page.goto('/')

  // The seeded university is published, so the homepage is never empty.
  const university = page.getByRole('link', { name: /University of Science and Technology/ })
  await expect(university).toBeVisible()

  await university.click()
  await expect(page.getByRole('heading', { level: 1, name: /University of Science and Technology/ }))
    .toBeVisible()

  // And from there into a signup scoped to that university, which opens on the
  // first question rather than on a form.
  await page.getByRole('link', { name: 'Create an account' }).click()
  await expect(page).toHaveURL(/university=ustc-ac-bd/)
  await expect(page.getByRole('heading', { level: 1, name: /joining as/i })).toBeVisible()
})

test('signing up is one question at a time', async ({ page }) => {
  await page.goto('/signup?university=ustc-ac-bd')

  // Who you are. The university was answered by the link that got us here, so
  // it is neither asked again nor counted, and the email box is nowhere in
  // sight yet.
  await expect(page.getByText('Step 1 of 5')).toBeVisible()
  await expect(page.getByLabel('Email')).toHaveCount(0)

  await page.getByRole('button', { name: /^Student/ }).click()
  await page.getByRole('button', { name: 'Continue' }).click()

  // Your name and email, and no more than that.
  await expect(page.getByRole('heading', { level: 1, name: /call you/i })).toBeVisible()
  await expect(page.getByLabel('Department')).toHaveCount(0)

  // A step cannot be left half answered.
  await expect(page.getByRole('button', { name: 'Continue' })).toBeDisabled()
  await page.getByLabel('Full name').fill('Ada Lovelace')
  await page.getByLabel('Email').fill('ada@learnx.help')
  await expect(page.getByRole('button', { name: 'Continue' })).toBeEnabled()

  // And going back does not lose what was typed.
  await page.getByRole('button', { name: 'Back' }).click()
  await page.getByRole('button', { name: 'Continue' }).click()
  await expect(page.getByLabel('Full name')).toHaveValue('Ada Lovelace')
})

test('the landing page is two actions and a list of universities', async ({ page }) => {
  await page.goto('/')

  // One heading, and the two ways in. Nothing else is asked of a visitor.
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Sign In' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Get Started' })).toBeVisible()

  // The ground is animated rather than a fixed gradient: the same element
  // reports a different filter a moment later.
  const filterNow = await page.evaluate(
    () => getComputedStyle(document.querySelector('.backdrop')!).filter,
  )
  await page.waitForTimeout(3000)
  const filterLater = await page.evaluate(
    () => getComputedStyle(document.querySelector('.backdrop')!).filter,
  )
  expect(filterLater).not.toBe(filterNow)

  await page.getByRole('link', { name: 'Get Started' }).click()
  await expect(page).toHaveURL(/\/signup/)
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
  await signIn(page, 'student@learnx.help')

  await expect(page.getByRole('link', { name: 'Account approvals' })).toHaveCount(0)
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
  await page.getByLabel('Sections').getByRole('link', { name: 'Account approvals' }).click()
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

test('each role gets its own navigation, not a longer one', async ({ page }) => {
  const nav = page.getByLabel('Sections')

  await signIn(page, 'teacher@learnx.help')

  // A teacher's sidebar is built around teaching: no "My results", which only
  // reports a student's own marks, and the marking screens instead.
  await expect(nav.getByRole('link', { name: 'Routine & class tests' })).toBeVisible()
  await expect(nav.getByRole('link', { name: 'Gradebook' })).toBeVisible()
  await expect(nav.getByRole('link', { name: 'Note approvals' })).toBeVisible()
  await expect(nav.getByRole('link', { name: 'My results' })).toHaveCount(0)

  await signOut(page)
  await signIn(page, 'cr@learnx.help')

  // A class representative keeps the routine, so the entry says so.
  await expect(nav.getByRole('link', { name: 'Routine & test slots' })).toBeVisible()
  await expect(nav.getByRole('link', { name: 'Gradebook' })).toHaveCount(0)

  await signOut(page)
  await signIn(page, 'admin@learnx.help')

  // An administrator's own screens are separate entries rather than one
  // "Administration" that lands on whichever tab happens to come first.
  await expect(nav.getByRole('link', { name: 'Master routine' })).toBeVisible()

  await nav.getByRole('link', { name: 'Change history' }).click()
  await expect(page).toHaveURL(/\/admin\?view=audit$/)
  await expect(page.getByText('Changes to the routine and class tests')).toBeVisible()

  // Those entries all address /admin and differ only by the view they open,
  // so exactly one of them may be marked as the current page.
  const current = nav.locator('[aria-current="page"]')
  await expect(current).toHaveCount(1)
  await expect(current).toHaveText('Change history')
})

test('the routine screen reads the sheet, and says so', async ({ page }) => {
  await signIn(page, 'student@learnx.help')
  await page.getByRole('link', { name: 'Class routine' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Class routine' })).toBeVisible()

  // The whole week is offered, whatever the sheet turns out to hold.
  await expect(page.getByRole('button', { name: /Wednesday/ })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Refresh' })).toBeVisible()

  // And the screen always states where its data stands: live, a saved copy, or
  // not configured. Asserted as a set so the test does not depend on whether
  // the machine running it can reach Google.
  await expect(page.locator('.routine-status')).toContainText(
    /Live|Saved copy|No sheet configured|could not be read|Reading the sheet/,
  )
})

test('a class representative can cancel a class for the whole class', async ({ page }) => {
  await signIn(page, 'cr@learnx.help')
  await page.goto('/schedule')

  await page.getByRole('button', { name: 'Post a change' }).click()
  // Scoped to the dialog throughout: the screen behind it has its own course
  // and time fields, for the classes a class keeps in LearnX itself.
  const dialog = page.getByRole('dialog', { name: 'Post a change' })
  await expect(dialog).toBeVisible()

  // Adding is always possible, even on a date the sheet has nothing for, so it
  // is the part that can be asserted without depending on the sheet's content.
  await dialog.getByLabel('Course').fill('CSE 9999')
  await dialog.getByRole('button', { name: 'Post to my class' }).click()

  await expect(page.getByText('Posted to your class')).toBeVisible()
  const posted = dialog.locator('.routine-manage-row').filter({ hasText: 'CSE 9999' })
  await expect(posted).toBeVisible()

  // And withdrawing it leaves the class where it started.
  await posted.getByRole('button', { name: 'Withdraw' }).click()
  await expect(page.getByText('Change withdrawn')).toBeVisible()
  await expect(dialog.locator('.routine-manage-row').filter({ hasText: 'CSE 9999' })).toHaveCount(0)
})

test('the theme is the viewer\'s choice, and it is remembered', async ({ page }) => {
  await signIn(page, 'student@learnx.help')

  // The browser reports a light machine, and nothing has been chosen yet.
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')

  await page.getByRole('button', { name: 'Dark' }).click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')

  // Surviving the reload is the point: the choice is read back in the page
  // head, before the first paint, rather than after the bundle mounts.
  await page.reload()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')

  // And handing the decision back to the machine returns it to light.
  await page.getByRole('button', { name: 'Auto' }).click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
})

test('the health probe answers without a session', async ({ request }) => {
  const response = await request.get('/actuator/health')
  expect(response.ok()).toBeTruthy()
  expect((await response.json()).status).toBe('UP')
})
