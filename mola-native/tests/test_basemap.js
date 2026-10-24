// Offline-only integration test for packaged interactive basemap and real failover.
// These mocked tile events are not a claim of working live Android internet.
'use strict';
const assert=require('assert'),fs=require('fs'),vm=require('vm'),path=require('path');
const assets=path.join(__dirname,'../overlay/fermata/src/main/assets/id4');
const geographyJs=fs.readFileSync(path.join(assets,'id4-offline-geography.js'),'utf8');
const overlayPatch=fs.readFileSync(path.join(__dirname,'../patch_mola.py'),'utf8');
const copyLoop=overlayPatch.match(/for f in \[([^\]]+)\]:\s*\n\s*target=root\/f.relative_to\(overlay\)/);
assert(copyLoop,"APK overlay copy list missing");
assert(copyLoop[1].includes('basemap_controller')&&
       copyLoop[1].includes('basemap_geography'),
       "basemap JS/geography must be COPIED into APK inputs");
 
const basemapJs=fs.readFileSync(path.join(assets,'id4-basemap.js'),'utf8');
assert(basemapJs.includes('communitySources'), 'public basemap must remain available without TomTom key');
const tiles=[],outlines=[],labels=[],statuses=[],allLayers=new Set(),panes={};
const map={
 createPane(name){const pane={style:{}};panes[name]=pane;return pane},
 getPane(name){return panes[name]||null},
 hasLayer(layer){return allLayers.has(layer)},
 removeLayer(layer){allLayers.delete(layer)},
 addLayer(layer){allLayers.add(layer)}
};
const L={
 geoJSON(data,options){
  assert(data?.type==='FeatureCollection'&&data.features.length>=15);
  assert(data.features.some(f=>f.properties.name==='Turkey'));
  const layer={data,options,addTo(m){outlines.push(this);m.addLayer(this);return this}};
  return layer;
 },
 marker(coords,options){
  return {coords,options,addTo(m){labels.push(this);m.addLayer(this);return this}};
 },
 divIcon(opts){return opts},
 tileLayer(url,opts){
  const layer={url,opts,events:{},on(name,fn){this.events[name]=fn;return this},
   addTo(m){tiles.push(this);m.addLayer(this);return this}};
  return layer;
 }
};
const scheduled=[];
const window={L};
const ctx={window,console,Array,Number,Math,
 setTimeout(fn){scheduled.push(fn);return scheduled.length},
 clearTimeout(){}};
vm.createContext(ctx);
vm.runInContext(geographyJs,ctx,{timeout:2500});
assert(window.ID4_OFFLINE_COUNTRIES.features.length>=15,'must ship geography, not blank');
vm.runInContext(basemapJs,ctx,{timeout:2500});
const controller=window.Id4BaseMap;
assert(controller&&typeof controller.init==='function');
controller.init(map,s=>statuses.push(s),[['Ankara',39.9208,32.8541],['İstanbul',41.0082,28.9784]]);
assert.equal(tiles.length,1,'only visible primary OSM tiles are requested');
assert(tiles[0].url.includes('tile.openstreetmap.org'));
assert.equal(controller.status().online,false);
assert(outlines.length===1&&allLayers.has(outlines[0]),
 'bundled geopolitical outline must show while tiles are loading');
assert(labels.length>=1,'real city coordinate fallback should render');
tiles[0].events.tileload();
assert.equal(controller.status().online,true);
assert(!allLayers.has(outlines[0]),'real tile success removes fallback overlay');
assert(statuses.some(s=>s.status==='online'&&s.source==='OpenStreetMap'));
controller.retry();
assert.equal(tiles.length,2,'retry reloads the first provider only on request');
tiles[1].events.tileerror();tiles[1].events.tileerror();tiles[1].events.tileerror();
assert.equal(tiles.length,3,'OSM error must fail over to OSM France');
assert(tiles[2].url.includes('tile.openstreetmap.fr/osmfr'));
assert.equal(controller.status().online,false,'failover does not fake a live map');
tiles[2].events.tileerror();tiles[2].events.tileerror();tiles[2].events.tileerror();
assert.equal(controller.status().status,'offline',
 'when both suppliers fail show explicit offline country outlines');
assert(allLayers.has(outlines[0]),'geography remains visible without internet');
assert(controller.retry(),'manual retry must work from offline map');
assert.equal(tiles.length,4);
tiles[3].events.tileload();
assert.equal(controller.status().online,true);
assert(!allLayers.has(outlines[0]));
assert(controller.setTomTomKey('TEST_TOMTOM_BASEMAP_KEY_12345678'),
 'runtime TomTom key must be accepted');
assert.equal(controller.sources()[0],'TomTom Orbis');
assert(tiles.at(-1).url.includes('/maps/orbis/display/raster/tile/'));
assert(tiles.at(-1).url.includes('street-dark'));
assert(tiles.at(-1).url.includes('geopoliticalView=TR'));
controller.setDay(true);
assert(tiles.at(-1).url.includes('street-light'));
for(let i=0;i<3;i++)tiles.at(-1).events.tileerror();
assert(tiles.at(-1).url.includes('tile.openstreetmap.org'),
 'TomTom denied: fallback to public OSM');
assert(controller.setTomTomKey(''),'TomTom key should be removable');
assert.equal(controller.sources()[0],'OpenStreetMap');
controller.close();
assert.equal(controller.status().status,'uninitialized');
console.log('PASS: real tile success, two-provider failures, geographic fallback, retry and attribution');
