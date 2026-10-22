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
const calls={suggest:[],route:[],routeVia:[],location:0,stop:0,selectObd:0,visible:[],saved:[]};
const drawnMarkers=[];
const L={
 map(){return {setView(){return this},on(){return this},removeLayer(){},
  fitBounds(bounds,opts){globalThis.lastRouteBoundsOptions=opts},
  getSize(){return {x:390,y:780}},
  getBounds(){return {getSouth(){return 36},getWest(){return 30},
     getNorth(){return 40},getEast(){return 35}}},
  invalidateSize(){return this},
  zoomIn(){},zoomOut(){},panTo(){},closePopup(){}}},
 tileLayer(){return {addTo(){return this}}},
 circleMarker(){return {popup:null,
  addTo(){drawnMarkers.push(this);return this},
  bindPopup(content){this.popup=content;return this},setLatLng(){}}},
 polyline(){return {addTo(){return this},getBounds(){return []}}}
};
const win={
 MolaNative:{
  suggest(field,q,id){calls.suggest.push({field,q,id})},
  route(...args){calls.route.push(args)},
  routeVia(...args){calls.routeVia.push(args)},
  location(){calls.location++},stop(){calls.stop++},
  voice(){},back(){},stations(){},sensors(){},selectObd(){calls.selectObd++},
  visibleStations(...args){calls.visible.push(args)},
  saveStation(...args){calls.saved.push(args)}
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
get('from-list').children[0].onclick();
const finalCity=typing('to','Antalya');
assert(finalCity.field==='to');
get('to-list').children[0].onclick();
assert(get('from').value.includes('Niğde'));
assert(get('to').value.includes('Antalya'));
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
 energyLowKwh:81.6,energyHighKwh:122.4,chargingNeededByModel:true,
 navigationActive:false,steps:['Devam et','Sola dön']
});
assert(get('trip').style.display==='block','route summary not visible');
assert(get('compact-trip').textContent.includes('510 km'),'compact route summary missing');
assert(get('trip-energy').textContent.includes('81.6–122.4 kWh'),
 'automatic route energy model not displayed');
assert(get('trip-energy').textContent.includes('şarj'),
 'route model should flag needed charging without fabricated SoC');
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
win.molaRoute({
 coordinates:[[34.6833,37.9667],[34.7000,37.9800]],
 distanceKm:12,durationMin:15,alternatives:1,provider:'OSRM test',
 energyLowKwh:1.92,energyHighKwh:2.88,
 arrivalSocLower:74.5,arrivalSocUpper:76.0,chargingNeededByModel:false,steps:[]
});
assert(get('trip-energy').textContent.includes('Varış SoC ~%75–%76'),
 'recent BMS SoC arrival range missing');
win.molaRoute({coordinates:[[34.68,37.96],[34.9,37.8],[30.7,36.9]],
 distanceKm:513,durationMin:397,stopIncluded:true,
 energyLowKwh:82,energyHighKwh:123,steps:[]});
assert(get('compact-trip').textContent.includes('1 şarj molası'),
 'waypoint route must show charging stop label');
assert(get('trip-energy').textContent.includes('Mola şarj miktarı bilinmiyor'),
 'charging stop must never invent terminal SoC');
// Same-map charging: request viewport only on user action; never show invented availability.
get('charge-map').onclick();
assert.equal(calls.visible.length,1,'EPDK viewport bridge never invoked');
assert.equal(calls.visible[0][0],36);
win.molaStations({kind:'route',items:[
 {lat:37.8,lon:34.9,title:'Deneme CCS',operator:'ŞarjAğı',
  sockets:'CCS 2 · Type 2 1',ccs:true,distanceKm:3.2},
 {lat:37.7,lon:34.7,title:'Deneme AC',operator:'ŞarjB',
  sockets:'CCS 0 · Type 2 2',ccs:false,distanceKm:4.1}
 ],total:2,updatedAt:'2026-10-20T08:00:00Z',
 snapshotAgeHours:48,liveAvailability:false,livePrices:false});
assert(get('station-summary').textContent.includes('2 rota yakınında'),
 'route CCS stations summary missing');
assert(get('station-note').textContent.includes('Canlı soket doluluğu/fiyat yok'),
 'snapshot incorrectly claims real-time status');
assert(get('station-suggestion').style.display==='none',
 'missing SoC and suggestion must not produce fake charging recommendation');
win.molaStations({kind:'route',items:[],total:1,
  updatedAt:'2026-10-20T08:00:00Z',snapshotAgeHours:48,
  chargeModel:'charge-likely',
  suggested:{lat:37.8,lon:34.9,title:'Deneme CCS',operator:'ŞarjAğı',
    ccs:true,routeProgressKm:75.2},
  liveAvailability:false,livePrices:false});
assert(get('station-suggestion').style.display==='flex',
 'charge suggestion should appear in single-screen route summary');
assert(get('station-suggestion-label').textContent.toLowerCase().includes('model'),
 'suggestion must explicitly identify model assumptions');
const savedBeforeCharge=calls.saved.length;
get('station-suggestion-choose').onclick();
assert.equal(calls.routeVia.length,1,'suggestion must invoke explicit charging waypoint route');
assert.equal(calls.saved.length,savedBeforeCharge+1,'suggested CCS operator should be recorded');
// Arrival suggestion is not authoritative when no fresh SoC is available.
win.molaStations({kind:'route',items:[],total:0,chargeModel:'no-soc',
 updatedAt:'2026-10-20T08:00:00Z',snapshotAgeHours:48});
assert(get('station-suggestion').style.display==='none',
 'no SoC must withdraw suggested charging stop');

const popupMarker=drawnMarkers.find(x=>x.popup && x.popup.children &&
 x.popup.children.some(c=>c.textContent==='Deneme CCS'));
assert(popupMarker,'station marker popup was not rendered');
assert.equal(popupMarker.popup.children[3].textContent,'Şarj molası olarak ekle');
popupMarker.popup.children[3].onclick();
assert.equal(calls.routeVia.length,2,'CCS station should be usable as a waypoint');
assert.equal(calls.routeVia[1][2],37.8,'waypoint latitude wrong');
assert.equal(calls.routeVia[1][3],34.9,'waypoint longitude wrong');
assert(Math.abs(calls.routeVia[1][4]-36.8841)<0.002,
 'original final destination must be kept');
popupMarker.popup.children[2].onclick();
assert.equal(calls.saved.length,3,'suggested and chosen waypoints plus destination must persist operator');
assert.equal(calls.saved[0][1],'ŞarjAğı');
assert.equal(get('to').value,'Deneme CCS','chosen charger not set as destination');
assert.equal(calls.route.length,3,'choosing a station must replan the route automatically');
assert.equal(calls.route[2][2],37.8,'new route target latitude wrong');
assert.equal(calls.route[2][3],34.9,'new route target longitude wrong');
win.molaStationError({detail:'Ağ erişimi yok'});
assert(get('station-summary').textContent.includes('alınamadı'),
 'station failure must be visible');
assert(html.includes('NEREDEN') && html.includes('NEREYE'));
console.log('PASS: 81-city offline fallback, failed Photon, Turkish accents, autocomplete, stale results, manual route and swap.');
