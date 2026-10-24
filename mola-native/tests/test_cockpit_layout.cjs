// Exercise the actual packaged page and Leaflet, including provider startup.
// Remote requests are blocked: this test never presents fixture data as live telemetry.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
(async () => {
 const browser = await chromium.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome', args: ['--no-sandbox', '--allow-file-access-from-files'] });
 try {
 const page = await browser.newPage();
 const errors = [];
 page.on('pageerror', e => errors.push(e.message));
 await page.route(/^https?:/, request => request.abort());
 const out = process.env.ID4_SCREENSHOTS || '/tmp/id4-layout';
 fs.mkdirSync(out, { recursive: true });
 for (const [width, height] of [[1536,864],[1536,691],[920,414],[873,393]]) {
  await page.setViewportSize({ width, height });
  await page.goto('file://' + path.resolve(__dirname, '../overlay/fermata/src/main/assets/id4/navigator.html'));
  await page.waitForTimeout(200);
  const issues = await page.evaluate(() => {
   const dock = document.querySelector('.drive-dock').getBoundingClientRect();
   const cards = [...document.querySelectorAll('.cockpit-left > .cockpit-panel:not(.cockpit-slim), .cockpit-smallgrid .cockpit-panel')];
   return cards.flatMap(e => {
    const r = e.getBoundingClientRect();
    return r.bottom > dock.top + 1 || r.top < 0 || r.width <= 0 || e.scrollHeight > e.clientHeight + 3 ? [e.textContent.trim()] : [];
   });
  });
  await page.screenshot({ path: path.join(out, width + 'x' + height + '.png') });
  assert.deepEqual(issues, [], 'Vehicle cards clipped at ' + width + 'x' + height);
  assert.match(await page.locator('#header-traffic-status').innerText(), /anahtar yok/);
  assert.equal(await page.locator('#co-soc').innerText(), '— %');
  // Open route search on the real page, not a mocked DOM.
  await page.locator('#co-route-tab').click();
  assert.equal(await page.locator('#to').isVisible(), true);
  await page.locator('#to').fill('Şırnak');
  await page.waitForTimeout(450);
  assert.equal(await page.locator('#to-list').isVisible(), true);
  console.log('PASS cockpit and search', width, height);
 }
 assert.deepEqual(errors, [], 'Page JavaScript errors');
 } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
