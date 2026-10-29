// Copies the web app into www/ for Capacitor to bundle. The site itself has
// no build step; this only exists because the app wants a clean web folder.
// The app is standalone, so the header (the name and the link back to
// nathankurien.com) is emptied and the page title is the app's name, in its
// copy of index.html; the website keeps both.
const fs = require('fs');
const path = require('path');

const TITLE = '<title>Weather | Nathan Kurien</title>';
const HEADER = /<header class="top">[\s\S]*?<\/header>/;

function appHtml(html) {
  if (!html.includes(TITLE) || !HEADER.test(html) || !/Back to site/.test(html)) throw new Error('index.html header changed; update scripts/build-www.js');
  // keep the (padded) element so the chips sit where they did under the old header
  return html.replace(TITLE, '<title>Dotter Weather</title>').replace(HEADER, '<header class="top"></header>');
}

module.exports = { appHtml };

if (require.main === module) {
  const root = path.join(__dirname, '..');
  const out = path.join(root, 'www');
  const files = ['manifest.webmanifest', 'sw.js', 'icons'];

  fs.rmSync(out, { recursive: true, force: true });
  fs.mkdirSync(out);
  files.forEach(f => fs.cpSync(path.join(root, f), path.join(out, f), { recursive: true }));
  fs.writeFileSync(path.join(out, 'index.html'), appHtml(fs.readFileSync(path.join(root, 'index.html'), 'utf8')));
  console.log('www/ ready: index.html (standalone), ' + files.join(', '));
}
