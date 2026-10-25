// Live provider integration check; no fixture map, telemetry or simulated traffic.
const { chromium } = require('playwright');
const path = require('node:path');
const fs = require('node:fs');
const assert = require('node:assert/strict');
(async () => {
 const browser = await chromium.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome',
  args: ['--no-sandbox','--allow-file-access-from-files','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader'] });
 try {
  const page = await browser.newPage({viewport:{width:1536,height:864}});
  const errors=[], oldRaster=[];
  page.on('pageerror',e=>errors.push(e.message));
  page.on('request',r=>{if(/tile\.openstreetmap\./.test(r.url()))oldRaster.push(r.url())});
  await page.goto('file://'+path.resolve(__dirname,'../overlay/fermata/src/main/assets/id4/navigator.html'));
  await page.waitForFunction(()=>window.Id4BaseMap.status().online,{},{timeout:60000});
  assert.equal(await page.evaluate(()=>window.Id4BaseMap.status().source),'OpenFreeMap');
  assert.equal(await page.locator('.maplibregl-canvas').count(),1);
  assert.match(await page.locator('#map-credit').innerText(),/OpenMapTiles/);
  const out=process.env.ID4_SCREENSHOTS||'/tmp/id4-layout';fs.mkdirSync(out,{recursive:true});
  await page.waitForTimeout(1500);
  await page.screenshot({path:path.join(out,'openfreemap-turkiye.png')});
  // Pan/zoom the actual Leaflet map; vector layer must follow it and load detail.
  await page.evaluate(()=>map.setView([41.015,28.979],11,{animate:false}));
  await page.waitForTimeout(7000);
  await page.screenshot({path:path.join(out,'openfreemap-istanbul.png')});
  assert.deepEqual(oldRaster,[],'Old OSM raster tile server must never be contacted');
  assert.deepEqual(errors,[],'Runtime JavaScript errors');
  console.log('PASS: live OpenFreeMap vector canvas, Turkey, Istanbul, no legacy raster');
 } finally {await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
