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
  classList:{_set:new Set(),add(c){this._set.add(c)},remove(c){this._set.delete(c)},contains(c){return this._set.has(c)},toggle(c){if(this._set.has(c)){this._set.delete(c);return false}this._set.add(c);return true}},
  addEventListener(name,fn){this.handlers[name]=fn},
  appendChild(child){this.children.push(child)},
  replaceChildren(){this.children=[]},
  setAttribute(){},
  get length(){return this.children.length}
 };
}
function get(id){return elements[id]||(elements[id]=el());}
const messages=[],timeouts=[],intervals=[];
const calls={suggest:[],route:[],routeVia:[],location:0,stop:0,selectObd:0,visible:[],saved:[],orientation:[],chosen:[],filtered:[]};
const drawnMarkers=[];
const mapActions={pans:[],events:{},tileEvents:{}};
const L={
 map(){return {setView(){return this},getZoom(){return 7},
  on(event,handler){mapActions.events[event]=handler;return this},removeLayer(){},
  fitBounds(bounds,opts){globalThis.lastRouteBoundsOptions=opts},
  getSize(){return {x:390,y:780}},
  getBounds(){return {getSouth(){return 36},getWest(){return 30},
     getNorth(){return 40},getEast(){return 35}}},
  invalidateSize(){return this},
  zoomIn(){},zoomOut(){},panTo(coords,opts){mapActions.pans.push({coords,opts})},closePopup(){}}},
 tileLayer(){return {addTo(){return this},
   on(name,callback){mapActions.tileEvents[name]=callback;return this}}},
 circleMarker(){return {popup:null,
  addTo(){drawnMarkers.push(this);return this},
  bindPopup(content){this.popup=content;return this},setLatLng(){}}},
 divIcon(options){return options},
 marker(coords,options){return {coords,options,popup:null,events:{},
  on(name,handler){this.events[name]=handler;return this},
  addTo(){drawnMarkers.push(this);return this},
  bindPopup(content){this.popup=content;return this},
  setLatLng(next){this.coords=next}}},
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
  visibleStationsFiltered(...args){calls.filtered.push(args)},
  saveStation(...args){calls.saved.push(args)},
  landscape(...args){calls.orientation.push(args)},
  chooseAlternative(index){calls.chosen.push(index)}
 }
};
const context={
 window:Object.assign(win,{innerWidth:390}),document:{
  getElementById:get,createElement:el,body:{classList:el().classList},
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
assert(html.includes('id="vehicle-cockpit"'),'landscape cockpit not on real map');
assert(html.includes('id="cockpit-tripbar"'),'image-matched bottom route HUD missing');
assert(html.includes('id="layer-all"')&&html.includes('id="layer-ccs"')&&
 html.includes('id="layer-route"')&&html.includes('id="layer-traffic"'),
 'selectable full Turkey, route chargers and honest traffic mode missing');
assert(html.includes('id="journey-points"'),
 'full route weather overlay should show the whole drive');
get('layer-all').onclick();
assert.equal(calls.filtered.length,1,
 'the all stations layer must request the visible EPDK area');
assert.equal(calls.filtered[0][4],false,'all stations must include AC sockets');
get('layer-ccs').onclick();
assert.equal(calls.filtered[1][4],true,'CCS selector must filter out AC-only stations');
win.molaStations({kind:'visible',items:[
 {cluster:true,lat:38,lon:34,count:800,ccsCount:280}
 ],total:800,clustered:true,updatedAt:'2026-10-22T11:00:00Z'});
assert(get('layer-status').textContent.includes('küme'),
 'nationwide cluster must be clearly distinguished from individual stations');
const cluster=drawnMarkers[drawnMarkers.length-1];
assert(typeof cluster.events.click==='function',
 'station grouping must zoom to show constituent station pins');
cluster.events.click();
assert(get('layer-status').textContent.includes('küme'),
 'zooming must not fabricate extra station features');
get('layer-traffic').onclick();
assert(get('traffic-choices').classList.contains('open'),
 'traffic selector must expose official licensed provider choices');
assert(get('traffic-help').textContent.includes('API anahtarı yok'),
 'without credentials the user must see the real provider limitation');
get('traffic-google').onclick();
assert(get('traffic-help').textContent.includes('SDK bileşeni bulunamadı'),
 'without an official SDK do not claim the traffic layer is live');
get('traffic-osm').onclick();
assert(get('layer-status').textContent.includes('korunuyor'),
 'missing provider must preserve the working map instead of faking traffic');
get('layer-route').onclick();

assert(html.includes('id="co-alt-toggle"')&&html.includes('id="co-alts-list"'),
 'charge-aware route chooser must be available without hiding the map');
assert(html.includes('id="co-cost"'),'auto-cost summary must appear on route bar');
assert(html.includes("className:'charge-map-pin'"),
 'real coordinates must use visible CCS lightning icons');
assert(html.includes("className:'vehicle-map-pin'"),
 'GPS position needs its own visible map marker');
assert(html.includes('.sheet.route-active:not(.details-open){display:none!important}'),
 'old legacy route sheet still covers landscape map');
assert(html.includes('id="co-trip-arrival"')&&html.includes('id="co-trip-charge-button"'),
 'trip bar missing transparent charge and model arrival elements');
assert(html.includes('id="map-credit"'),
 'OpenStreetMap attribution must be visible and not hidden by route sheet');
assert(typeof mapActions.tileEvents.tileerror==='function' &&
       typeof mapActions.tileEvents.tileload==='function',
 'offline/online tile states must be handled');
mapActions.tileEvents.tileerror();
assert(get('map-credit').textContent.includes('yüklenemedi'),
 'tile failure should tell the driver instead of showing a blank map');
mapActions.tileEvents.tileload();
assert(get('map-credit').textContent.includes('Çevrimiçi harita'),
 'loaded tiles must clear the temporary warning');
assert(html.includes('id="co-soc"')&&html.includes('id="co-voltage"')&&
 html.includes('id="co-energy"'),'transparent cockpit missing core measurements');
assert(html.includes('backdrop-filter:blur'),'cockpit overlays must be translucent');
assert(html.includes('text-shadow:-1px -1px 0 #000'),
 'large telemetry numbers must have high contrast black outline');
assert(html.includes('@media (orientation:landscape)'),
 'full-screen cockpit must be landscape responsive');
assert(html.includes('.sheet:not(.route-active):not(.search-open){display:none!important}'),
 'legacy sidebar must not obscure the map in landscape');
assert(html.includes('id="go-fullscreen"'),'portrait to landscape button missing');
assert(html.includes('id="co-focus"')&&html.includes('id="co-fit-route"'),
 'driving focus and route fit controls must exist');
get('co-focus').onclick();
assert(context.document.body.classList.contains('focus-drive'),
 'map-focused view must respond to a deliberate tap');
assert(get('co-focus').textContent.includes('Tüm göstergeler'),
 'user must be able to restore all vehicle measurements');
get('co-focus').onclick();
assert(!context.document.body.classList.contains('focus-drive'),
 'normal view must restore the full telemetry panel');
get('co-fit-route').onclick();
assert(get('status').textContent.includes('Önce rota'),
 'route fit must refuse to claim a non-existent route');
assert(html.includes('id="co-quick-route"')&&html.includes('id="co-quick-layers"'),
 'reference-style landscape route and layer shortcuts missing');
assert(!get('map-layers').classList.contains('open'),
 'all map filters must start collapsed to preserve the full-screen cockpit');
get('co-quick-layers').onclick();
assert(get('map-layers').classList.contains('open'),
 'map filter menu should open only on explicit tap');
get('layer-daynight').onclick();
assert.equal(get('layer-daynight').textContent,'◐ Gece haritası',
 'day/night option must work within map layer menu');
get('layer-daynight').onclick();
assert.equal(get('layer-daynight').textContent,'◐ Gündüz haritası',
 'day/night option should be reversible');
get('layer-close').onclick();
assert(!get('map-layers').classList.contains('open'),
 'single tap must restore unobstructed route map');
assert(html.includes('id="open-layers"'),
 'portrait phone must have a way to open charging and weather layers');
get('open-layers').onclick();
assert(get('map-layers').classList.contains('open'),
 'portrait layer shortcut did not open charging and weather filters');
get('layer-close').onclick();
get('go-fullscreen').onclick();
assert.equal(calls.orientation.length,1,'cannot request landscape from portrait');
assert.equal(calls.orientation[0][0],true,'landscape request must be explicit');
get('co-expand').onclick();
assert.equal(calls.orientation[1][0],false,'must be able to return to auto orientation');
get('co-route-tab').onclick();
assert(get('sheet').classList.contains('search-open'),
 'route search should be opt-in, not obscure initial cockpit');
get('co-route-tab').onclick();
assert(!get('sheet').classList.contains('search-open'),'route form should close');
get('open-route').onclick();
assert(get('sheet').classList.contains('search-open'),'route search shortcut broken');
get('open-route').onclick();
assert(html.includes('id="live-soc"'),'live battery HUD missing');
assert(html.includes('id="live-voltage"'),'live HV voltage HUD missing');
assert(html.includes('id="live-temp"'),'live battery temperature HUD missing');
get('connect-obd').onclick();
assert.equal(calls.selectObd,1,'OBD selection must be a direct user action');
win.molaTelemetry({fresh:true,soc:'64 %',voltage:'370 V',temperature:'23 °C',
 chargeMode:'Ready',status:'ELM327 bağlı'});
assert.equal(get('live-soc').textContent,'64 %');
assert.equal(get('co-trip-battery').textContent,'OBD SoC: 64 %',
 'trip bar should show fresh measured battery SoC only');
assert.equal(get('live-voltage').textContent,'370 V');
assert.equal(get('live-temp').textContent,'23 °C');
assert.equal(get('vehicle-soc').textContent,'64 %');
assert.equal(get('co-soc').textContent,'64 %','full-screen SoC does not use real packet');
assert(get('co-sensor-age').textContent.includes('ölçüm yeni'),
 'measured SoC must show verified freshness status');
assert.equal(get('co-soc-fill').style.width,'64%','battery progress is not linked to BMS SoC');
assert.equal(get('co-voltage').textContent,'370 V');
assert.equal(get('co-temp').textContent,'23 °C');
assert.equal(get('co-mode').textContent,'Ready');
assert.equal(get('co-range').textContent,'106–159 km',
 'only verified SoC may generate clearly-labelled model range');
assert(html.includes('MENZİL · PROFİL MODELİ (%15 REZERV)'),
 'the model-range HUD label must not claim real BMS range');
assert.equal(get('co-power').textContent,'— kW','unverified kW must stay blank');
get('co-connect').onclick();
assert.equal(calls.selectObd,2,'full-screen OBD button is not wired');
get('co-station-tab').onclick();
assert.equal(calls.filtered.length,3,
 'cockpit charger tab must query all currently visible EPDK stations');
calls.filtered.length=0; // isolate initial all/CCS map checks
win.molaTelemetry({fresh:false,status:'OBD bağlı değil'});
assert.equal(get('live-soc').textContent,'—','stale SoC was not cleared');
assert.equal(get('vehicle-soc').textContent,'—','stale vehicle SoC was not cleared');
assert.equal(get('co-soc').textContent,'— %','cockpit must clear stale SoC');
assert(get('co-sensor-age').textContent.includes('güncel değil'),
 'missing SoC must not claim a fresh measured value');
assert.equal(get('co-trip-battery').textContent,'OBD SoC: —',
 'trip bar should clear stale measured battery');
assert.equal(get('co-trip-arrival').textContent,'—',
 'stale OBD should withdraw SoC arrival model');
assert.equal(get('co-range').textContent,'— km','model range must disappear with stale SoC');
assert(html.includes('id="follow-map"'),'GPS follow button is missing');
get('follow-map').onclick();
assert.equal(calls.location,1,'GPS follow must request location only on explicit click');
assert.equal(get('follow-map').title,'GPS takibini kapat');
win.molaPosition({lat:37.9667,lon:34.6833,accuracy:8,timeMs:Date.now()});
assert.equal(mapActions.pans.length,1,'valid GPS must move followed map');
assert.equal(mapActions.pans[0].coords[0],37.9667);
const oldPanCount=mapActions.pans.length;
win.molaPosition({lat:37.5,lon:34.1,accuracy:8,timeMs:Date.now()-120000});
assert.equal(mapActions.pans.length,oldPanCount,'old GPS must never drag map');
mapActions.events.dragstart();
win.molaPosition({lat:37.8,lon:34.0,accuracy:8,timeMs:Date.now()});
assert.equal(mapActions.pans.length,oldPanCount,'user map drag should suspend following');
get('follow-map').onclick();
assert.equal(mapActions.pans.length,oldPanCount+1,'follow button must re-center on last good fix');
get('follow-map').onclick();
calls.location=0; // prior request was explicit; subsequent search/plan must not trigger GPS
assert.equal(get('co-voltage').textContent,'— V','cockpit must clear stale voltage');
assert.equal(get('co-soc-fill').style.width,'0%','battery bar must clear when stale');
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
win.molaJourneyWeather({cards:[
 {percent:0,etaMinutes:0,latitude:37.97,longitude:34.68,
  available:true,temperature:14,rainProbability:10,wind:8,weatherCode:1},
 {percent:25,etaMinutes:80,latitude:37.72,longitude:33.5,
  available:true,temperature:11,rainProbability:40,wind:16,weatherCode:51},
 {percent:50,etaMinutes:160,latitude:37.3,longitude:32.2,
  available:true,temperature:9,rainProbability:30,wind:10,weatherCode:3},
 {percent:75,etaMinutes:240,latitude:36.9,longitude:31.2,
  available:false},
 {percent:100,etaMinutes:320,latitude:36.9,longitude:30.7,
  available:true,temperature:20,rainProbability:5,wind:11,weatherCode:0}
]});
assert.equal(get('journey-points').children.length,5,
 'whole-journey forecast must show five source-backed segments');
assert(get('journey-points').children[1].children[1].textContent.includes('yağış %40'),
 'precipitation probability should come from Open-Meteo model');
assert(get('journey-points').children[3].children[1].textContent.includes('Tahmin yok'),
 'unknown forecast must never be fabricated');
get('layer-weather').onclick();
assert(!get('journey-weather').classList.contains('open'),
 'route-less weather cannot be presented as a real trip');
assert(get('weather-cards').children[0].children[1].textContent.includes('12°C'),
  'weather data did not update actual card');
assert(get('compact-weather').children[0].textContent.includes('12°C'),
  'compact weather did not update');
assert(get('weather-source').textContent.includes('model'),
  'model forecast not labeled');
assert(get('co-weather').textContent.includes('5 hava tahmini noktası'),
 'whole-trip forecast should replace earlier compact forecast label');
// Real route callback must update visible polyline, not merely route inputs.
// The older GPS fix from the follow-map test is now stale: route cannot invent progress.
win.molaPosition({lat:37.8,lon:34.0,accuracy:8,timeMs:Date.now()-120000});
win.molaRoute({
 coordinates:[[34.6833,37.9667],[33.8,37.4],[30.7056,36.8841]],
 distanceKm:510,durationMin:385,alternatives:2,provider:'OSRM test',
 energyLowKwh:81.6,energyHighKwh:122.4,chargingNeededByModel:true,
 navigationActive:false,steps:['Devam et','Sola dön']
});
assert(get('trip').style.display==='block','route summary not visible');
assert(get('compact-trip').textContent.includes('510 km'),'compact route summary missing');
assert.equal(get('co-route-km').textContent,'510 km','cockpit route distance not updated');
globalThis.lastRouteBoundsOptions=null;
get('co-fit-route').onclick();
assert(globalThis.lastRouteBoundsOptions && globalThis.lastRouteBoundsOptions.maxZoom===15,
 'full-route button must reframe the actual OSRM route');
assert(get('co-trip-kilometers').textContent.includes('510 km'),'bottom route bar distance missing');
win.molaJourneyWeather({cards:[
 {percent:0,latitude:37.97,longitude:34.68,available:true,
  temperature:14,rainProbability:10,wind:8,weatherCode:1},
 {percent:25,latitude:37.72,longitude:33.5,available:true,
  temperature:11,rainProbability:40,wind:16,weatherCode:51},
 {percent:50,latitude:37.3,longitude:32.2,available:true,
  temperature:9,rainProbability:30,wind:10,weatherCode:3},
 {percent:75,latitude:36.9,longitude:31.2,available:false},
 {percent:100,latitude:36.9,longitude:30.7,available:true,
  temperature:20,rainProbability:5,wind:11,weatherCode:0}
]});
const beforeWeatherPins=drawnMarkers.length;
get('layer-weather').onclick();
assert(get('journey-weather').classList.contains('open'),
 'weather window must open with a real route');
assert.equal(drawnMarkers.length,beforeWeatherPins+4,
 'weather markers should render only when requested');
get('journey-close').onclick();
assert(!get('journey-weather').classList.contains('open'),
 'route weather popup should close');
get('layer-weather').onclick();
assert.equal(drawnMarkers.length,beforeWeatherPins+8,
 'cached weather should re-render without another network request');
get('journey-close').onclick();
assert(get('vehicle-cockpit').classList.contains('has-route'),'trip bar not enabled for route');
assert.equal(get('co-trip-arrival').textContent,'—',
 'must not fabricate arrival SoC without current BMS measurement');
assert(get('co-eta').textContent!=='—','real route must derive a traffic-free local ETA');
win.molaAlternatives({selectedIndex:0,fastestIndex:0,shortestIndex:1,
 chargeAwareIndex:1,options:[
 {index:0,km:510,minutes:385,energyLowKwh:81.6,energyHighKwh:122.4,
  stationsKnown:true,ccsCount:22,socKnown:true,chargeNeededModel:true,
  chargeLowKwh:55,chargeHighKwh:96},
 {index:1,km:520,minutes:400,energyLowKwh:83.2,energyHighKwh:124.8,
  stationsKnown:true,ccsCount:35,reachableCcsModel:true,
  socKnown:true,chargeNeededModel:true,chargeLowKwh:61,
  chargeHighKwh:98,costMinTry:792,costMaxTry:1616,
  tariffOperator:'ZES',tariffChecked:'2026-10-22'}
 ]});
assert.equal(get('co-alts-list').children.length,2,
 'real OSRM alternatives should be selectable without manual route entry');
assert(get('co-alts-list').children[1].children[0].children[0].textContent.includes('CCS önerili'),
 'station-aware route must be labeled by model rather than distance alone');
assert(get('co-cost').textContent.includes('55–96 kWh'),
 'unknown operator must show charge energy, not invent a TL rate');
get('co-alt-toggle').onclick();
assert(get('co-alts').classList.contains('open'),
 'route chooser should open from the compact trip bar');
get('co-alts-list').children[1].onclick();
assert.equal(calls.chosen[0],1,
 'choosing alternative must use the cached native OSRM route index');
assert(!get('co-alts').classList.contains('open'),
 'after selection, the map needs to be visible');
win.molaAlternatives({selectedIndex:1,fastestIndex:0,shortestIndex:1,
 chargeAwareIndex:1,options:[
 {index:0,km:510,minutes:385,energyLowKwh:81.6,energyHighKwh:122.4,
  socKnown:true,chargeNeededModel:true,chargeLowKwh:55,chargeHighKwh:96},
 {index:1,km:520,minutes:400,energyLowKwh:83.2,energyHighKwh:124.8,
  stationsKnown:true,ccsCount:35,socKnown:true,chargeNeededModel:true,
  costMinTry:792,costMaxTry:1616,tariffOperator:'ZES',
  tariffChecked:'2026-10-22'}]});
assert(get('co-cost').textContent.includes('792–1616 TL'),
 'automatic estimate must use known verified network price range');
assert(get('co-cost-note').textContent.includes('soket'),
 'cost summary must explain it is not a live confirmed bill');

assert.equal(get('co-maneuver').textContent,'Devam et',
 'cockpit first maneuver must be drawn from OSRM instructions');
win.molaManeuver({instruction:'Sağa dön',straightLineMeters:420,
 fixTimeMs:Date.now(),accuracy:12});
assert(get('co-maneuver').textContent.includes('Sağa dön · yaklaşık 420 m'),
 'real GPS maneuver proximity must update HUD');
assert(get('co-maneuver').textContent.includes('kuş uçuşu'),
 'straight-line maneuver proximity must not claim road distance');
win.molaManeuver({instruction:'Eski talimat',straightLineMeters:30,
 fixTimeMs:Date.now()-80000,accuracy:12});
assert(!get('co-maneuver').textContent.includes('Eski talimat'),
 'old GPS fix must not overwrite current navigation guidance');
win.molaManeuver({instruction:'Yanlış konum',straightLineMeters:30,
 fixTimeMs:Date.now(),accuracy:170});
assert(!get('co-maneuver').textContent.includes('Yanlış konum'),
 'inaccurate GPS fix must never render as a real maneuver');
assert(get('route-progress-text').textContent.includes('GPS rota ilerlemesi yok'),
 'do not simulate trip progress without a GPS observation');
win.molaPosition({lat:37.4,lon:33.8,accuracy:8,timeMs:Date.now()});
assert(get('route-progress-text').textContent.includes('GPS yaklaşık %'),
 'GPS map route progression not displayed');
assert(get('co-trip-progress-label').textContent.includes('GPS yaklaşık %'),
 'new trip bar must use only verified GPS');
assert(parseFloat(get('co-trip-fill').style.width)>0,
 'trip progress bar must advance with real GPS fix');
assert(parseFloat(get('route-progress-fill').style.width)>0,
 'real GPS route progress bar did not update');
win.molaPosition({lat:37.4,lon:33.8,accuracy:8,timeMs:Date.now()-120000});
assert(get('route-progress-text').textContent.includes('GPS rota ilerlemesi yok'),
 'stale GPS location must be rejected');
assert.equal(get('co-trip-fill').style.width,'0%','stale GPS must reset trip progress');
win.molaPosition({lat:40.5,lon:31.7,accuracy:10,timeMs:Date.now()});
assert(get('route-progress-text').textContent.includes('rota dışında'),
 'off-route GPS must not make up distance progression');
assert.equal(get('co-reroute').style.display,'inline-block',
 'the user must be offered an explicit reroute when truly off-route');
assert.equal(get('co-energy').textContent,'81,6–122,4 kWh',
 'cockpit route energy not labeled as model');
assert(get('vehicle-cockpit').classList.contains('has-route'),
 'landscape route progress panel should replace placeholder');
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
assert(get('trip-energy').textContent.includes('güncel GPS ve OBD'),
 'old planning SoC must not override live-source requirements');
assert.equal(get('co-trip-arrival').textContent,'—',
 'stale or off-route GPS must suppress model arrival percentage');
win.molaRoute({coordinates:[[34.68,37.96],[34.9,37.8],[30.7,36.9]],
 distanceKm:513,durationMin:397,stopIncluded:true,
 energyLowKwh:82,energyHighKwh:123,steps:[]});
assert(get('compact-trip').textContent.includes('1 şarj molası'),
 'waypoint route must show charging stop label');
assert(get('co-eta').textContent.includes('şarj hariç'),
 'ETA must not imply unknown charging dwell time is included');
assert(get('trip-energy').textContent.includes('Mola şarj miktarı bilinmiyor'),
 'charging stop must never invent terminal SoC');
// Same-map charging: request viewport only on user action; never show invented availability.
get('charge-map').onclick();
assert.equal(calls.filtered.length,1,'filtered EPDK viewport bridge never invoked');
assert.equal(calls.filtered[0][0],36);
get('layer-route').onclick();
win.molaStations({kind:'route',items:[
 {lat:37.8,lon:34.9,title:'Deneme CCS',operator:'ŞarjAğı',
  sockets:'CCS 2 · Type 2 1',ccs:true,distanceKm:3.2},
 {lat:37.7,lon:34.7,title:'Deneme AC',operator:'ŞarjB',
  sockets:'CCS 0 · Type 2 2',ccs:false,distanceKm:4.1}
 ],total:2,updatedAt:'2026-10-20T08:00:00Z',
 snapshotAgeHours:48,liveAvailability:false,livePrices:false});
assert(get('station-summary').textContent.includes('2 rota yakınında'),
 'route CCS stations summary missing');
assert(get('station-note').textContent.includes('Soket durumu/fiyat doğrulanmadı'),
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
assert.equal(get('co-trip-charge-button').style.display,'inline-block',
 'CCS recommendation must be available from bottom car HUD');
assert(get('co-trip-charge-km').textContent.includes('75 km'),
 'CCS progress distance must be shown as approximate route model');
assert(get('station-suggestion-label').textContent.toLowerCase().includes('model'),
 'suggestion must explicitly identify model assumptions');
// Switching to nationwide stations does not cancel independent CCS route advice.
get('layer-all').onclick();
win.molaStations({kind:'route',items:[],total:1,
 updatedAt:'2026-10-20T08:00:00Z',snapshotAgeHours:48,
 chargeModel:'charge-likely',
 suggested:{lat:37.8,lon:34.9,title:'Deneme CCS',operator:'ŞarjAğı',
  ccs:true,routeProgressKm:75.2}});
assert.equal(get('co-trip-charge-button').style.display,'inline-block',
 'route charge advice must survive browsing all Turkish stations');
assert(get('station-suggestion').style.display==='flex',
 'non-route layers must not suppress model charging advice');
get('layer-route').onclick();
assert(get('layer-status').textContent.includes('rota yakınında'),
 'returning to route CCS must restore cached station corridor');
const savedBeforeCharge=calls.saved.length;
get('station-suggestion-choose').onclick();
assert.equal(calls.routeVia.length,1,'suggestion must invoke explicit charging waypoint route');
assert.equal(calls.saved.length,savedBeforeCharge+1,'suggested CCS operator should be recorded');
// Arrival suggestion is not authoritative when no fresh SoC is available.
win.molaStations({kind:'route',items:[],total:0,chargeModel:'no-soc',
 updatedAt:'2026-10-20T08:00:00Z',snapshotAgeHours:48});
assert(get('station-suggestion').style.display==='none',
 'no SoC must withdraw suggested charging stop');
assert.equal(get('co-trip-charge-button').style.display,'none',
 'bottom bar charging button must vanish when fresh SoC is missing');

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
assert(get('co-destination').textContent.includes('Deneme CCS'),
 'cockpit destination must update when charging station chosen');
get('route-edit').onclick();
assert(!get('vehicle-cockpit').classList.contains('has-route'),
 'route reset must also reset cockpit state');
assert.equal(get('co-route-km').textContent,'— km',
 'route reset must clear out-of-date dashboard distance');
assert.equal(get('co-trip-arrival').textContent,'—',
 'route reset must clear old trip arrival');
assert.equal(get('co-maneuver').textContent,'Rota bekleniyor',
 'old turn instructions must disappear when route is reset');
assert(get('route-progress-text').textContent.includes('GPS rota ilerlemesi yok'),
 'route reset must clear the progress bar');
// Arrival SoC must recompute from the CURRENT fresh BMS SoC and
// matched remaining route, never display stale/static route planning SoC.
win.molaRoute({
 coordinates:[[34.6833,37.9667],[34.6,37.95]],distanceKm:40,
 durationMin:50,energyLowKwh:6.4,energyHighKwh:9.6,
 steps:['İlerleyin'],navigationActive:false
});
win.molaPosition({lat:37.9667,lon:34.6833,accuracy:5,timeMs:Date.now()});
assert.equal(get('co-trip-arrival').textContent,'—',
 'without fresh BMS SoC, route GPS alone must not imply arrival SoC');
win.molaTelemetry({fresh:true,soc:'64 %',voltage:'370 V',temperature:'23 °C',
 chargeMode:'Ready',status:'ELM327 bağlı'});
assert.equal(get('co-trip-arrival').textContent,'%46–%52',
 'fresh BMS + 40km remaining should show 52kWh/16-24kWh model band');
win.molaTelemetry({fresh:true,soc:'70 %',voltage:'370 V',temperature:'23 °C',
 chargeMode:'Ready',status:'ELM327 bağlı'});
assert.equal(get('co-trip-arrival').textContent,'%52–%58',
 'arrival SoC should respond to actual SoC updates');
win.molaPosition({lat:37.9667,lon:34.6833,accuracy:5,timeMs:Date.now()-120000});
assert.equal(get('co-trip-arrival').textContent,'—',
 'stale GPS must hide predicted arrival despite a fresh SoC');
win.molaPosition({lat:37.9667,lon:34.6833,accuracy:5,timeMs:Date.now()});
assert.equal(get('co-trip-arrival').textContent,'%52–%58',
 'arrival model may return after a new accurate GPS fix');
win.molaTelemetry({fresh:false,status:'OBD bağlantısı koptu'});
assert.equal(get('co-trip-arrival').textContent,'—',
 'stale OBD must clear model arrival SoC');
win.molaRoute({
 coordinates:[[34.6833,37.9667],[34.6,37.95]],distanceKm:40,
 durationMin:50,stopIncluded:true,energyLowKwh:6.4,energyHighKwh:9.6,
 steps:['Şarj molasına devam'],navigationActive:false
});
win.molaTelemetry({fresh:true,soc:'70 %',voltage:'370 V',
 temperature:'23 °C',chargeMode:'Ready',status:'ELM327 bağlı'});
assert.equal(get('co-trip-arrival').textContent,'—',
 'model cannot predict arrival SoC after a charger without charge amount');
assert(get('co-trip-arrival-note').textContent.includes('Mola sonrası'),
 'missing post-charge amount must be explained');

// Explicit, not automatic, user-approved reroute from a recent GPS fix.
win.molaRoute({
 coordinates:[[34.6833,37.9667],[33.8,37.4]],
 distanceKm:145,durationMin:125,steps:['İlerle'],
 energyLowKwh:23.2,energyHighKwh:34.8
});
win.molaPosition({lat:40.5,lon:31.7,accuracy:9,timeMs:Date.now()});
assert.equal(get('co-reroute').style.display,'inline-block',
 'offroute reroute shortcut should only appear with fresh accurate GPS');
const routesBeforeTap=calls.route.length;
get('co-reroute').onclick();
assert.equal(calls.route.length,routesBeforeTap+1,
 'rerouting requires an explicit user action');
assert.equal(calls.route[routesBeforeTap][0],40.5,
 'reroute must use actual recent GPS latitude');
assert.equal(calls.route[routesBeforeTap][4],true,
 'manual GPS reroute should enable verified position guidance');
assert.equal(get('co-reroute').style.display,'none',
 'rerouting must hide the previous-route warning');

assert(html.includes('NEREDEN') && html.includes('NEREYE'));
console.log('PASS: 81-city offline fallback, failed Photon, Turkish accents, autocomplete, stale results, manual route and swap.');
