/** The logo: a four-point spark on the accent colour. Decorative; the name next to it carries the meaning. */
export function BrandMark({ className = 'size-8' }: { className?: string }) {
  return (
    <span aria-hidden="true" className={`grid place-items-center rounded-lg bg-accent text-on-accent ${className}`}>
      <svg viewBox="0 0 24 24" className="size-[60%]" fill="currentColor">
        <path d="M12 2.5c.4 4.6 2.2 7.1 6.5 9.5-4.3 2.4-6.1 4.9-6.5 9.5-.4-4.6-2.2-7.1-6.5-9.5 4.3-2.4 6.1-4.9 6.5-9.5Z" />
        <path d="M19 3c.15 1.7.8 2.6 2.4 3.5-1.6.9-2.25 1.8-2.4 3.5-.15-1.7-.8-2.6-2.4-3.5C18.2 5.6 18.85 4.7 19 3Z" opacity="0.7" />
      </svg>
    </span>
  );
}
