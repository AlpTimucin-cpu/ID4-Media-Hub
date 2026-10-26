// Exercise the actual packaged page and Leaflet, including provider startup.
// Remote requests are blocked: this test never presents fixture data as live telemetry.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
(async () => {
 const browser = await chromium.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome', args: ['--no-sandbox', '--allow-file-access-from-files', '--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
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
  // Regression: the 30/70 split is geometry, not a screenshot of a map.
  // The original #map remains a single interactive Leaflet instance.
  const split = await page.evaluate(() => {
   const map = document.querySelector('#map').getBoundingClientRect();
   const rail = document.querySelector('#id4-tesla-side').getBoundingClientRect();
   const dockButtons = ['dock-route','dock-stations','dock-apps',
      'dock-journal','dock-layers'].map(id => document.getElementById(id));
   return {mapLeft:map.left, mapWidth:map.width, railRight:rail.right,
    dockVisible:dockButtons.every(button => {
     const style=getComputedStyle(button),rect=button.getBoundingClientRect();
     return style.display!=='none' && rect.width>0 && rect.height>0;
    })};
  });
  assert(Math.abs(split.mapLeft-width*.30)<=2,
   'Map must begin at 30% viewport width: '+width+'x'+height);
  assert(Math.abs(split.mapWidth-width*.70)<=2,
   'Map must retain 70% viewport width: '+width+'x'+height);
  assert(split.railRight<=split.mapLeft+2,
   'Telemetry rail overlaps real map: '+width+'x'+height);
  assert.equal(split.dockVisible,true,'Five functional dock controls visible');
  await page.screenshot({ path: path.join(out, width + 'x' + height + '.png') });
  assert.deepEqual(issues, [], 'Vehicle cards clipped at ' + width + 'x' + height);
  assert.match(await page.locator('#header-traffic-status').innerText(), /anahtar gerekli/);
  assert.equal(await page.locator('#co-soc').innerText(), '— %');
  // Avoid duplicating live telemetry as a third navigation card.
  // The existing addon still runs; only its redundant UI is hidden.
  assert.equal(await page.locator('#id4-live-addon').evaluate(
    e => getComputedStyle(e).display), 'none',
    'new left instruments must replace visual addon card in landscape');
  // The premium overlay must show no manufactured SoC, range or power.
  assert.equal(await page.locator('#id4-side-soc').innerText(), '— %');
  assert.equal(await page.locator('#id4-side-range').innerText(), '— km');
  assert.equal(await page.locator('#id4-side-power').innerText(), '— kW');
  // On the wide premium cockpit, the new always-visible search pill
  // forwards to the original tested route tab. Compact layouts keep it.
  const routeControl = width >= 1180 ? '#ev-premium-search' : '#co-route-tab';
  assert.equal(await page.locator(routeControl).isVisible(),true,
    'real route entry must be visible at '+width+'x'+height);
  await page.locator(routeControl).click();
  assert.equal(await page.locator('#to').isVisible(), true);
  await page.locator('#to').fill('Şırnak');
  await page.waitForTimeout(450);
  assert.equal(await page.locator('#to-list').isVisible(), true);
  console.log('PASS cockpit and search', width, height);
 }
 assert.deepEqual(errors, [], 'Page JavaScript errors');
 } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
