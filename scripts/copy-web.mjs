// Copies the Circuitries web app into ./www for Capacitor.
// Source folder: $WEB_SRC, or ../Circuitries (sibling clone), or ./source (CI checkout).
import { cpSync, existsSync, rmSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

const candidates = [process.env.WEB_SRC, 'source', '../Circuitries'].filter(Boolean).map(p => resolve(p));
const src = candidates.find(p => existsSync(resolve(p, 'index.html')) && existsSync(resolve(p, 'js/app.js')));
if (!src) {
  console.error('Circuitries web app not found. Tried:\n  ' + candidates.join('\n  '));
  process.exit(1);
}
const out = resolve('www');
rmSync(out, { recursive: true, force: true });
mkdirSync(out);
for (const item of ['index.html', 'manifest.webmanifest', 'css', 'js', 'icons']) {
  if (existsSync(resolve(src, item))) cpSync(resolve(src, item), resolve(out, item), { recursive: true });
}
console.log('Copied web app from ' + src + ' to ' + out);
