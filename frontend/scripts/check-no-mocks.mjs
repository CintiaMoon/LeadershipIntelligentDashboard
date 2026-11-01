// Fails when test-only code could end up in a production build: no production source may import test helpers or the
// mock service worker, and the built bundle must not contain fixture data or the mock library.
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

const problems = [];

function walk(dir, visit) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) walk(path, visit);
    else visit(path);
  }
}

walk('src', (path) => {
  const normalized = path.replace(/\\/g, '/');
  if (!/\.(ts|tsx)$/.test(normalized) || /\.test\.(ts|tsx)$/.test(normalized) || normalized.startsWith('src/tests/')) return;
  const text = readFileSync(path, 'utf8');
  if (/from ['"](@\/tests|\.\.?\/.*tests)\//.test(text) || /from ['"]msw/.test(text)) problems.push(`${normalized} imports test-only code`);
});

const markers = ['11111111-1111-1111-1111-111111111111', 'setupServer', 'HttpResponse', 'mockServiceWorker'];
try {
  walk('dist', (path) => {
    if (!/\.(js|html|css)$/.test(path)) return;
    const text = readFileSync(path, 'utf8');
    for (const marker of markers) if (text.includes(marker)) problems.push(`${path.replace(/\\/g, '/')} contains "${marker}"`);
  });
} catch {
  problems.push('dist/ is missing: run npm run build first');
}

if (problems.length > 0) {
  console.error(problems.join('\n'));
  process.exit(1);
}
console.log('no test-only code in the production sources or the build');
