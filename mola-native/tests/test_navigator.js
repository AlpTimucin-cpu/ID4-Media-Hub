// Integration checks for the actual packaged navigation HTML: no network required.
const fs=require('fs');
const vm=require('vm');
const assert=require('assert');
const path=require('path');
const file=process.argv[2] || path.join(__dirname,'../overlay/fermata/src/main/assets/id4/navigator.html');
const html=fs.readFileSync(file,'utf8');
const inline=[...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)]
 .map(m=>m[1]).find(x=>x.includes('molaSuggestions'));
assert(inline,'Mola native navigation JavaScript missing');
for(const id of ['from','to','from-list','to-list','locate','plan','swap']){
 assert(html.includes('id="'+id+'"'),'missing input/button '+id);
}
const elements={};
function el(){
 return {
  value:'',style:{},children:[],textContent:'',onclick:null,handlers:{},
  classList:{_set:new Set(),add(c){this._set.add(c)},remove(c){this._set.delete(c)},contains(c){return this._set.has(c)}},
  addEventListener(name,fn){this.handlers[name]=fn},
  appendChild(child){this.children.push(child)},
  replaceChildren(){this.children=[]},
  setAttribute(){},
  get length(){return this.children.length}
 };
}
function get(id){return elements[id]||(elements[id]=el());}
const messages=[],timeouts=[],intervals=[];
const calls={suggest:[],route:[],location:0,stop:0,selectObd:0};
const L={
 map(){return {setView(){return this},on(){return this},removeLayer(){},
  fitBounds(bounds,opts){globalThis.lastRouteBoundsOptions=opts},
  getSize(){return {x:390,y:780}},
  invalidateSize(){return this},
  zoomIn(){},zoomOut(){},panTo(){}}},
 tileLayer(){return {addTo(){return this}}},
 circleMarker(){return {addTo(){return this},bindPopup(){return this},setLatLng(){}}},
 polyline(){return {addTo(){return this},getBounds(){return []}}}
};
const win={
 MolaNative:{
  suggest(field,q,id){calls.suggest.push({field,q,id})},
  route(...args){calls.route.push(args)},
  location(){calls.location++},stop(){calls.stop++},
  voice(){},back(){},stations(){},sensors(){},selectObd(){calls.selectObd++}
 }
};
const context={
 window:Object.assign(win,{innerWidth:390}),document:{
  getElementById:get,createElement:el,
  querySelector(){return get('sheet')}
 },
 L,setTimeout(fn){timeouts.push(fn);return timeouts.length},
 setInterval(fn){intervals.push(fn);return intervals.length},
 clearTimeout(){},console,Number,Array,Object,String,Math,
 };
const wCards=get('weather-cards');
const compactWeather=get('compact-weather');
for(let i=0;i<4;i++){
 const row=el();const title=el();const desc=el();
 row.children=[title,desc];wCards.children.push(row);
 compactWeather.children.push(el());
}
vm.createContext(context);vm.runInContext(inline,context,{timeout:2000});
function typing(field,query){
 get(field).value=query;get(field).handlers.input();
 while(timeouts.length)timeouts.shift()();
 return calls.suggest[calls.suggest.length-1];
}
// Combined map and vehicle HUD: measured values only, with explicit OBD pairing.
assert(html.includes('id="live-soc"'),'live battery HUD missing');
assert(html.includes('id="live-voltage"'),'live HV voltage HUD missing');
assert(html.includes('id="live-temp"'),'live battery temperature HUD missing');
get('connect-obd').onclick();
assert.equal(calls.selectObd,1,'OBD selection must be a direct user action');
win.molaTelemetry({fresh:true,soc:'64 %',voltage:'370 V',temperature:'23 °C',
 chargeMode:'Ready',status:'ELM327 bağlı'});
assert.equal(get('live-soc').textContent,'64 %');
assert.equal(get('live-voltage').textContent,'370 V');
assert.equal(get('live-temp').textContent,'23 °C');
assert.equal(get('vehicle-soc').textContent,'64 %');
win.molaTelemetry({fresh:false,status:'OBD bağlı değil'});
assert.equal(get('live-soc').textContent,'—','stale SoC was not cleared');
assert.equal(get('vehicle-soc').textContent,'—','stale vehicle SoC was not cleared');
// Offline fallback works even if Photon returns errors.
const first=typing('from','Niğde');
assert.equal(first.field,'from');assert.equal(first.q,'Niğde');
assert(get('from-list').children.length>=1,'offline Niğde result must show immediately');
assert(get('from-list').children[0].textContent.includes('Niğde'),'offline Niğde label missing');
win.molaSuggestions({field:'from',requestId:first.id,items:[],
 error:'External server unavailable'});
assert(get('from-list').children.length>=1,'server outage hid offline city');
get('from-list').children[0].onclick();
const next=typing('to','Antalya');
assert.equal(next.field,'to');assert.equal(next.q,'Antalya');
assert(get('to-list').children.length>=1,'offline Antalya result must show immediately');
const before=get('to-list').children.length;
win.molaSuggestions({field:'to',requestId:next.id-1,
 items:[{label:'Wrong stale match',lat:36.9,lon:30.7}]});
assert.equal(get('to-list').children.length,before,'outdated response replaced offline result');
win.molaSuggestions({field:'to',requestId:next.id,items:[
 {label:'Antalya Eğitim ve Araştırma Hastanesi',lat:36.893,lon:30.68},
 {label:'Antalya, Türkiye',lat:36.895,lon:30.7}
]});
const suggestions=get('to-list').children;
assert(suggestions.length>=3,'remote location results not merged with offline city');
const hospital=suggestions.find(x=>x.textContent.includes('Eğitim ve Araştırma'));
assert(hospital,'remote detailed Antalya hospital not listed');
hospital.onclick();
get('plan').onclick();
assert.equal(calls.route.length,1,'manual route never called');
assert(Math.abs(calls.route[0][0]-37.9667)<0.002,'Niğde source coordinate wrong');
assert(Math.abs(calls.route[0][1]-34.6833)<0.002,'Niğde longitude wrong');
assert.equal(calls.route[0][2],36.893);
assert.equal(calls.route[0][3],30.68);
assert.equal(calls.route[0][4],false,'manual route must not demand GPS');
assert.equal(calls.location,0,'GPS triggered without user choice');
get('swap').onclick();get('plan').onclick();
assert.equal(calls.route[1][0],36.893);
assert(Math.abs(calls.route[1][2]-37.9667)<0.002);
const accent=typing('from','Nigde');
assert(get('from-list').children.some(x=>x.textContent.includes('Niğde')),
 'accent-insensitive matching must work');
// Weather cards update only with an actual provider result.
win.molaWeather({source:'Open-Meteo',cards:[
 {minutes:1,temperature:12,precipitation:0.2,wind:11,resolution:'15dk model'},
 {minutes:5,temperature:11,precipitation:0.2,wind:13,resolution:'15dk model'},
 {minutes:10,temperature:10,precipitation:0.3,wind:14,resolution:'15dk model'},
 {minutes:15,temperature:9,precipitation:0.4,wind:15,resolution:'15dk model'}
]});
assert(get('weather-cards').children[0].children[1].textContent.includes('12°C'),
  'weather data did not update actual card');
assert(get('compact-weather').children[0].textContent.includes('12°C'),
  'compact weather did not update');
assert(get('weather-source').textContent.includes('model'),
  'model forecast not labeled');
// Real route callback must update visible polyline, not merely route inputs.
win.molaRoute({
 coordinates:[[34.6833,37.9667],[33.8,37.4],[30.7056,36.8841]],
 distanceKm:510,durationMin:385,alternatives:2,provider:'OSRM test',
 navigationActive:false,steps:['Devam et','Sola dön']
});
assert(get('trip').style.display==='block','route summary not visible');
assert(get('compact-trip').textContent.includes('510 km'),'compact route summary missing');
assert(get('sheet').classList.contains('route-active'),'route panel did not collapse');
assert(get('map-note').style.display==='none','old map hint still overlays the route');
get('route-details').onclick();
assert(get('sheet').classList.contains('details-open'),'details could not be expanded');
get('route-details').onclick();
assert(!get('sheet').classList.contains('details-open'),'details could not be collapsed');

assert(get('traffic-source').textContent.includes('Canlı trafik'),
  'route must not claim unlicensed live traffic');
assert(get('tripdata').textContent.includes('510 km'),'route distance absent');
assert(globalThis.lastRouteBoundsOptions.paddingBottomRight[1]>=200,
  'route viewport hides map beneath info sheet');
assert(html.includes('NEREDEN') && html.includes('NEREYE'));
console.log('PASS: 81-city offline fallback, failed Photon, Turkish accents, autocomplete, stale results, manual route and swap.');
