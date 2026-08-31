/**
 * The ground the application is painted on.
 *
 * Five slow blurred circles behind a fixed layer, so every translucent surface
 * has something to be translucent against. Mounted once at the root rather
 * than per shell: it must not repaint when a route changes, and the signed-out
 * pages sit on the same ground as the signed-in ones.
 *
 * Decorative, so it is hidden from assistive technology, and it stops moving
 * for anyone who has asked for less motion.
 */
export default function Backdrop() {
  return (
    <div className="backdrop" aria-hidden="true">
      <div className="blob blob-1" />
      <div className="blob blob-2" />
      <div className="blob blob-3" />
      <div className="blob blob-4" />
      <div className="blob blob-5" />
    </div>
  )
}
