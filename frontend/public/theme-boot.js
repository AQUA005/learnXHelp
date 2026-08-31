/**
 * Resolves the stored theme before the first paint.
 *
 * A separate file rather than an inline snippet: the Content-Security-Policy
 * is `script-src 'self'`, and weakening it to 'unsafe-inline' for four lines
 * would trade a real protection for a stylistic one. Loaded blocking from the
 * page head, so it still runs before anything is painted -- which is the whole
 * point, since somebody who chose dark should never see a white page while the
 * bundle arrives.
 *
 * The storage key is shared with src/lib/theme.tsx. Changing one means
 * changing both.
 */
(function () {
  try {
    var choice = localStorage.getItem('learnx.theme') || 'system'
    var dark =
      choice === 'dark' ||
      (choice === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches)
    document.documentElement.dataset.theme = dark ? 'dark' : 'light'
  } catch (error) {
    // Storage can be blocked outright; the light theme is the default anyway.
    document.documentElement.dataset.theme = 'light'
  }
})()
