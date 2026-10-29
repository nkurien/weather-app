// Copies the web app into www/ for Capacitor to bundle. The site itself has
// no build step; this only exists because the app wants a clean web folder.
const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, '..');
const out = path.join(root, 'www');
const files = ['index.html', 'manifest.webmanifest', 'sw.js', 'icons'];

fs.rmSync(out, { recursive: true, force: true });
fs.mkdirSync(out);
files.forEach(f => fs.cpSync(path.join(root, f), path.join(out, f), { recursive: true }));
console.log('www/ ready: ' + files.join(', '));
