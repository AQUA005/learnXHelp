import { useEffect, useRef } from 'react'

/**
 * The ground the application is painted on.
 *
 * Five slow blurred circles behind a fixed layer, so every translucent surface
 * has something to be translucent against. Mounted once at the root rather
 * than per shell: it must not repaint when a route changes, and the signed-out
 * pages sit on the same ground as the signed-in ones.
 *
 * It also drifts a little against the page as you scroll. Because the layer is
 * fixed it would otherwise be perfectly still, and a background that never
 * moves reads as a picture rather than as depth. The shift is small and
 * capped: this is parallax, not a ride.
 *
 * Decorative, so it is hidden from assistive technology, and everything here
 * stops for anyone who has asked for less motion.
 */
export default function Backdrop() {
  const ground = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const element = ground.current
    if (!element) return
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return

    let frame = 0
    function onScroll() {
      // Coalesced into one frame: scroll fires far more often than the screen
      // is painted, and each handler here writes a style.
      if (frame) return
      frame = requestAnimationFrame(() => {
        frame = 0
        const shift = Math.min(window.scrollY * 0.06, 40)
        element?.style.setProperty('--parallax', `${shift}px`)
      })
    }

    window.addEventListener('scroll', onScroll, { passive: true })
    onScroll()
    return () => {
      window.removeEventListener('scroll', onScroll)
      if (frame) cancelAnimationFrame(frame)
    }
  }, [])

  return (
    <div className="backdrop" ref={ground} aria-hidden="true">
      <div className="blob blob-1" />
      <div className="blob blob-2" />
      <div className="blob blob-3" />
      <div className="blob blob-4" />
      <div className="blob blob-5" />
    </div>
  )
}
