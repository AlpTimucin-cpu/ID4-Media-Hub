// Guard the actual APK asset graph: skin + unchanged route/media bridge.
'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const dir=path.resolve(__dirname,'../overlay/fermata/src/main/assets/id4');
const read=name=>fs.readFileSync(path.join(dir,name),'utf8');
const html=read('navigator.html'),css=read('eya-cockpit-3070.css');
const js=read('id4-tesla-cockpit.js');
assert.match(html,/<title>Elektrikli Yol Arkadaşım/);
assert.match(html,/data-eya-layout="cockpit-v4"/);
assert.equal((html.match(/id="map"/g)||[]).length,1,'must keep exactly one Leaflet map');
assert.equal((html.match(/window\.id4LiveLeafletMap=map/g)||[]).length,1);
for(const file of ['eya-cockpit-3070.css','id4-tesla-cockpit.js',
                   'id4-live-map.js','id4-navigator-adapter.js',
                   'traffic-provider.js','id4-mola-media.js']){
 assert(html.includes((file.endsWith('.css')?'href="':'src="')+file+'"'),file+' not loaded');
}
for(const id of ['dock-route','dock-stations','dock-apps','dock-journal','dock-layers',
 'co-trip-track','co-trip-fill','co-trip-arrival','co-maneuver',
 'id4-drive-speed','id4-side-soc','id4-side-range','id4-route-stop-markers']){
 assert(html.includes('id="'+id+'"'),'lost existing control '+id);
}
for(const label of ['Rota','İstasyon','Medya','Yolculuklar','Ayarlar']){
 assert(html.includes('class="dock-label">'+label+'</span>'),'missing real tab '+label);
}
assert.match(css,/left:30%!important/);
assert.match(css,/width:70%!important/);
assert.match(css,/#26d5ff/);
assert.match(css,/#2385ff/);
assert.match(css,/#45ecc7/);
assert.match(css,/#vehicle-cockpit:not\(\.has-route\) \.cockpit-tripbar\s*\{display:none!important\}/);
assert.match(css,/\.tripbar-track\.gps-confirmed::after/);
assert.match(css,/prefers-reduced-motion:reduce/);
assert.match(css,/@media \(orientation:portrait\), \(max-width:639px\)/);
assert.match(js,/function markDockActive\(/);
assert.match(js,/observe\('molaTelemetry',onTelemetry\)/);
assert.match(js,/observe\('molaPosition',onPosition\)/);
assert.match(js,/observe\('molaRoute',onRoute\)/);
assert.match(js,/observe\('molaStations',onStations\)/);
assert.match(js,/validAge\(S\.socAt,TTL,now\)/);
assert(!/^\s*(?:L\.map\(|window\.L\.map\()/m.test(js),'overlay must not create a second Leaflet map');
console.log('PASS: cockpit v4 linked to the working navigator, native OBD and media; route/GPS fail-safe');
