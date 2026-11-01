/**
 * The name the interface shows, in one place so it is easy to change (decision D51). The product is called "Leadership Intelligent
 * Dashboard"; the sidebar and the sign-in page use the short mark "Helm" with the line below it. Nothing else in the code spells
 * the name out, and the static page title in index.html is the product name.
 */
export const brand = {
  /** The short name beside the logo. */
  name: 'Helm',
  /** The line under the short name. */
  tagline: 'Leadership Intelligence',
  /** The full product name, used for the document title. */
  productName: 'Leadership Intelligent Dashboard',
} as const;
