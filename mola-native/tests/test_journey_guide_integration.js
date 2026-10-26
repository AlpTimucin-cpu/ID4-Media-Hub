// Guard the provided route / native OBD / progress integration guide.
// No extra map, no simulated telemetry, and no second JS-to-Android bridge.
'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const root=path.resolve(__dirname,'../overlay/fermata/src/main');
const html=fs.readFileSync(path.join(root,'assets/id4/navigator.html'),'utf8');
const live=fs.readFileSync(path.join(root,'assets/id4/id4-live-map.js'),'utf8');
const adapter=fs.readFileSync(path.join(root,'assets/id4/id4-navigator-adapter.js'),'utf8');
const android=fs.readFileSync(path.join(root,
  'java/me/aap/fermata/ev/Id4NavigatorActivity.java'),'utf8');
// The app already has a single Leaflet map and genuine OSRM geometry.
// Do not paste an unrelated map-canvas sample from the technical guide.
assert.equal((html.match(/L\.map\('map'/g)||[]).length,1,
  'one Leaflet map belongs to navigator');
assert.match(adapter,/attachExistingMap/);
assert.match(html,/routeProgressGeometry\(/);
assert.match(html,/showRouteGpsProgress\(/);
assert.match(html,/x\.accuracy>60/);
assert.match(html,/Date\.now\(\)-x\.timeMs>30000/);
assert.match(html,/var pct=Math\.max\(0,Math\.min\(100,position\*100\)\)/);
assert.match(html,/var iconPct=Math\.min\(92,Math\.max\(0,pct\)\)/);
assert.match(html,/data\.roadKm\*\(1-position\)/);
// Existing native callback owns the source of truth; never poll a fabricated
// JSON payload with static 120km/h or 68% battery values.
assert.match(android,/new Id4LiveTelemetry\(/);
assert.match(android,/send\("molaTelemetry",obdPacket\(data\)\)/);
assert.match(html,/window\.molaTelemetry=function/);
assert.doesNotMatch(html,/AndroidBridge\.getLiveOBDData\(/);
assert.doesNotMatch(live,/getLiveOBDData\(/);
// Keep native fallback & vehicle safety rules unchanged.
assert.match(android,/addJavascriptInterface\(new Bridge\(\),"MolaNative"\)/);
assert.match(android,/videoLaunchBlockedByMotion/);
console.log('PASS: one map, native data, live OSRM geometry, GPS-driven bounded progress');
