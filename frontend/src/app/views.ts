/**
 * How a tabbed screen is addressed.
 *
 * Both the administration screens and the platform console put several screens
 * behind one route and distinguish them with a query parameter, so the sidebar
 * can link straight to one and a reload stays where it was. The parameter name
 * lives here rather than in either feature, because the navigation compares
 * entries from both.
 */
export const VIEW_PARAM = 'view'
