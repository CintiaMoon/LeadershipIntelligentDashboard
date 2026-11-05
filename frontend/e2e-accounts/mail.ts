import type { APIRequestContext } from '@playwright/test';

const MAILPIT = process.env.E2E_MAILPIT_URL ?? 'http://localhost:8025';

interface Listed {
  ID: string;
  Subject: string;
  To: Array<{ Address: string }>;
  Created: string;
}

/** The newest email to an address whose subject matches, read from Mailpit; waits for it to arrive. */
export async function latestMail(request: APIRequestContext, to: string, subject: RegExp, after: Date): Promise<string> {
  const deadline = Date.now() + 20_000;
  while (Date.now() < deadline) {
    const listing = await request.get(`${MAILPIT}/api/v1/messages?limit=100`);
    const messages = ((await listing.json()) as { messages: Listed[] }).messages ?? [];
    const found = messages.find((m) => m.To.some((t) => t.Address.toLowerCase() === to.toLowerCase()) && subject.test(m.Subject) && new Date(m.Created) >= after);
    if (found) {
      const detail = await request.get(`${MAILPIT}/api/v1/message/${found.ID}`);
      return ((await detail.json()) as { Text: string }).Text;
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`no email "${subject}" arrived for ${to}`);
}

/** The path and query of the first link in a message, e.g. /verify-email?token=abc. */
export function linkIn(text: string): string {
  const match = /https?:\/\/[^\s/]+(\/[^\s]*)/.exec(text);
  if (!match?.[1]) throw new Error(`no link in: ${text}`);
  return match[1];
}
