// Offline regression test for the actual Mola packaged WebView module.
// All measurements and coordinates here are test fixtures, never APK content.
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const asset=path.resolve(__dirname,'../overlay/fermata/src/main/assets/id4');
const source=fs.readFileSync(path.join(asset,'id4-live-map.js'),'utf8');
const adapter=fs.readFileSync(path.join(asset,'id4-navigator-adapter.js'),'utf8');
assert.match(adapter,/wrap\('molaStations'/,'real native route packet bridge missing');
assert.match(adapter,/mod\.setRouteStations\(p\)/,'route EPDK packet not delivered');
const els=new Map();
function el(id) {
 if(!els.has(id))els.set(id,{id,style:{},textContent:'',children:[],
  addEventListener(){},append(...v){this.children.push(...v)},
  appendChild(v){this.children.push(v)}});
 return els.get(id);
}
const doc={readyState:'loading',addEventListener(){},
 getElementById(id){return el(id)},
 createElement(){return {style:{},textContent:'',children:[],append(){},appendChild(){},addEventListener(){}}}};
const toRad=v=>v*Math.PI/180;
function metres(a,b){
 const dlat=toRad(a[0]-b[0]),dlon=toRad(a[1]-b[1]);
 const h=Math.sin(dlat/2)**2+Math.cos(toRad(a[0]))*Math.cos(toRad(b[0]))*Math.sin(dlon/2)**2;
 return 12742000*Math.asin(Math.min(1,Math.sqrt(h)));
}
const marker=()=>({addTo(m){m.addLayer(this);return this},bindPopup(){return this},setLatLng(){return this}});
const layerGroup=()=>({values:[],addTo(m){m.addLayer(this);return this},
 addLayer(v){this.values.push(v)},clearLayers(){this.values=[]},
 getLayers(){return this.values}});
const L={latLng(a){return {p:a,distanceTo(b){return metres(a,b.p)}}},
 layerGroup,divIcon(){return {}},marker,circleMarker:marker,
 polyline(){return {getBounds(){return {}}}},
 popup(){return {setLatLng(){return this},setContent(){return this},openOn(){return this}}}};
const map={addLayer(){},removeLayer(){},on(){},closePopup(){},
 fitBounds(){},setView(){},getBounds(){return {getSouth(){return 36},getWest(){return 28},getNorth(){return 42},getEast(){return 44}}}};
const win={ID4_LIVE_ATTACH_ONLY:true,
 localStorage:{getItem(){return null},setItem(){}},
 setTimeout,clearTimeout,setInterval(){},addEventListener(){},dispatchEvent(){},
 fetch:async function(url){
   assert.match(url,/api\.open-meteo\.com\/v1\/forecast/);
   return {ok:true,json:async()=>({current:{temperature_2m:-5,
     wind_speed_10m:40,time:'2026-10-25T12:00'}})};
 }};
const ctx={window:win,document:doc,L,Map,Date,Math,Number,String,Array,
 Set,JSON,Promise,Event,AbortController,console};
vm.runInNewContext(source,ctx,{timeout:2000,filename:'id4-live-map.js'});
(async()=>{
 const m=win.ID4LiveMap;
 assert(m.attachExistingMap(map));
 assert(m.ingestNativeRoute({coordinates:[[35,39],[35.1,39.1]],distanceKm:15,durationMin:20}));
 // Native packet is a route subset; count may exceed the sent item count.
 assert.equal(m.setRouteStations({kind:'route',total:7,items:[
  {id:'official-1',lat:39.03,lon:35.03,ccs:true,title:'EPDK CCS'},
  {cluster:true,lat:39.04,lon:35.04,count:6}
 ]}),1);
 assert.equal(el('id4-station-count').textContent,'7 (rota)');
 assert.equal(m.getState().corridorStations,1);
 m.updateLocation(39.02,35.02);
 m.updateCarData(50,19);
 assert.match(el('id4-nearest-dc').textContent,/Tam EPDK listesi/);
 // The optional full-country dataset enables nearest-DC lookup.
 assert.equal(m.setStations([
  {id:'near',lat:39.02,lon:35.02,dc:true,title:'Nearest DC'},
  {id:'far',lat:40,lon:36,dc:true,title:'Distant DC'}
 ]),2);
 assert.match(el('id4-nearest-dc').textContent,/Nearest DC/);
 m.updateCarData(30,21);
 assert.equal(el('id4-nearest-dc').textContent,'—');
 m.updateCarData(0,0);
 assert.equal(el('id4-soc').textContent,'0.0%');
 await new Promise(resolve=>setTimeout(resolve,40));
 assert.equal(m.getState().weather.multiplier,1.15);
 console.log('PASS: native route stations, no fictitious nearest DC, 0% SoC and live weather factor');
})().catch(error=>{console.error(error);process.exitCode=1});
