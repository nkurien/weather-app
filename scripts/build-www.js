// Copies the web app into www/ for Capacitor to bundle. The site itself has
// no build step; this only exists because the app wants a clean web folder.
// The app is standalone, so the header (the name and the link back to
// nathankurien.com) is emptied and the page title is the app's name, in its
// copy of index.html, and Fraunces is bundled (fonts/) instead of loaded from
// Google Fonts, so the app works offline; the website keeps all three.
const fs = require('fs');
const path = require('path');

const TITLE = '<title>Weather | Nathan Kurien</title>';
const FONT_LINKS = /<link rel="preconnect" href="https:\/\/fonts\.googleapis\.com">\n<link rel="preconnect" href="https:\/\/fonts\.gstatic\.com" crossorigin>\n<link href="https:\/\/fonts\.googleapis\.com\/css2[^>]*>/;
const FONT_FACE = '<style>\n'
  + '@font-face{font-family:"Fraunces";font-style:normal;font-weight:100 900;font-display:swap;src:url(fonts/Fraunces.ttf) format("truetype")}\n'
  + '@font-face{font-family:"Fraunces";font-style:italic;font-weight:100 900;font-display:swap;src:url(fonts/Fraunces-Italic.ttf) format("truetype")}\n'
  + '</style>';
const HEADER = /<header class="top">[\s\S]*?<\/header>/;

function appHtml(html) {
  if (!html.includes(TITLE) || !HEADER.test(html) || !/Back to site/.test(html) || !FONT_LINKS.test(html)) throw new Error('index.html header changed; update scripts/build-www.js');
  // keep the (padded) element so the chips sit where they did under the old header
  return html.replace(TITLE, '<title>Dotter Weather</title>').replace(HEADER, '<header class="top"></header>').replace(FONT_LINKS, FONT_FACE);
}

module.exports = { appHtml };

if (require.main === module) {
  const root = path.join(__dirname, '..');
  const out = path.join(root, 'www');
  const files = ['manifest.webmanifest', 'sw.js', 'icons', 'fonts'];

  fs.rmSync(out, { recursive: true, force: true });
  fs.mkdirSync(out);
  files.forEach(f => fs.cpSync(path.join(root, f), path.join(out, f), { recursive: true }));
  fs.writeFileSync(path.join(out, 'index.html'), appHtml(fs.readFileSync(path.join(root, 'index.html'), 'utf8')));
  console.log('www/ ready: index.html (standalone), ' + files.join(', '));
}
