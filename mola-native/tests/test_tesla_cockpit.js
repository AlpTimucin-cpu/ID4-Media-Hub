// Regression: the premium cockpit may observe but never replace native
// navigation, Mola media, OBD freshness or strict country EPDK rules.
'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,
 '../overlay/fermata/src/main/assets/id4/id4-tesla-cockpit.js'),'utf8');
const realNow=Date.now();
let now=realNow,interval=null,positionCalls=0,telemetryCalls=0,weatherCalls=0;
let routeCalls=0,stationCalls=0,maneuverCalls=0,statusCalls=0,routeUi=false;
const elements=new Map();
function element(id){
 if(!elements.has(id)) elements.set(id,{
   id,hidden:false,style:{},attrs:{},children:[],
   classList:{contains(k){return k==='has-route'&&routeUi}},
   setAttribute(k,v){this.attrs[k]=v},
   removeAttribute(k){delete this.attrs[k]},
   appendChild(n){this.children.push(n)},
 });
 const e=elements.get(id);
 if(!Object.getOwnPropertyDescriptor(e,'textContent')){
   e._text='';
   Object.defineProperty(e,'textContent',{
     get(){return this._text},
     set(value){this._text=String(value);this.children=[]}
   });
 }
 return e;
}
const classes=new Set();
const original={
  molaPosition(){positionCalls++},
  molaTelemetry(){telemetryCalls++},
  molaCurrentWeather(){weatherCalls++},
  molaCurrentWeatherError(){},
  molaRoute(){routeCalls++;routeUi=true},
  molaStations(){stationCalls++},
  molaManeuver(){maneuverCalls++},
  molaStatus(){statusCalls++;routeUi=false},
};
const win={
 ...original,
 document:{
   getElementById:element,
   createElement(tag){return {tag,className:'',style:{},title:'',children:[],
     attrs:{},setAttribute(k,v){this.attrs[k]=v},
     appendChild(n){this.children.push(n)}}},
   body:{classList:{toggle(name,on){on?classes.add(name):classes.delete(name)}}}
 },
 id4LiveLeafletMap:{invalidateSize(){win.mapResizes=(win.mapResizes||0)+1}},
 setInterval(fn){interval=fn;return 1},
 requestAnimationFrame(fn){fn()},
 console:{warn(){},error(){}},
};
const context=vm.createContext({window:win,Date:class extends Date {
 static now(){return now}
},Number,Math,String,Array});
vm.runInContext(source,context,{filename:'id4-tesla-cockpit.js'});
assert.equal(win.mapResizes,1,'one existing map was resized, not recreated');
// Re-entry must not stack multiple native callback listeners or timers.
assert.equal(win.ID4TeslaCockpit.start(),true);
assert.equal(win.mapResizes,1,'start() is idempotent');
// Missing/malformed packets are common when POCO F5 loses its ELM327 socket.
assert.doesNotThrow(()=>win.molaTelemetry(undefined));
assert.doesNotThrow(()=>win.molaPosition(null));
assert.doesNotThrow(()=>win.molaCurrentWeather({modelBased:false}));
assert.equal(element('id4-side-soc').textContent,'— %');
assert.equal(element('id4-drive-speed').textContent,'—');
// Reset counts: the malformed data still passes through Mola callback first.
telemetryCalls=0;positionCalls=0;weatherCalls=0;
assert.equal(win.ID4TeslaCockpit.parseSoc('64 %'),64);
assert.equal(win.ID4TeslaCockpit.parseSoc('105%'),null);
assert.equal(win.ID4TeslaCockpit.parseSoc('<img onerror=alert(1)>'),null);
assert.equal(element('id4-drive-speed').textContent,'—');
assert.equal(element('id4-drive-gear').textContent,'Vites: —');
assert.equal(element('id4-side-soc').textContent,'— %');
win.molaTelemetry({fresh:true,soc:'64 %',chargeMode:'Charging',gear:'P'});
assert.equal(telemetryCalls,1,'pre-existing callback runs');
assert.equal(element('id4-side-soc').textContent,'64 %');
assert.equal(element('id4-side-power').textContent,'— kW','no validated power PID');
assert.equal(element('id4-side-range').textContent,'— km','no fake 318km from artwork');
assert.equal(element('id4-drive-gear').textContent,'Vites: —',
 'never treat chargeMode or unverified gear as actual gear');
assert.equal(element('id4-side-bar-fill').style.width,'64%');
assert(!classes.has('id4-low-battery'));
win.molaTelemetry({fresh:true,soc:'19 %',gear:'D',gearVerified:true});
assert.equal(element('id4-drive-gear').textContent,'Vites: D');
assert(classes.has('id4-low-battery'),'low SoC visual alert');
assert.equal(element('id4-energy-impact').hidden,false);
win.molaTelemetry({fresh:true,soc:'20 %'});
assert(!classes.has('id4-low-battery'),'20% is not below 20%');
win.molaPosition({lat:38,lon:35,timeMs:now,accuracy:8,speedKmh:92});
assert.equal(positionCalls,1);
assert.equal(element('id4-drive-speed').textContent,'92');
win.molaPosition({lat:38,lon:35,timeMs:now-31000,accuracy:8,speedKmh:92});
assert.equal(element('id4-drive-speed').textContent,'—','stale GPS must clear');
win.molaPosition({lat:38,lon:35,timeMs:now,accuracy:8,speedKmh:0});
assert.equal(element('id4-drive-speed').textContent,'0',
 'zero only allowed for verified actual zero speed');
win.molaCurrentWeather({modelBased:true,temperature:-4,wind:33,precipitation:0,
 receivedAt:now,modelTime:Math.floor(now/1000)});
assert.equal(weatherCalls,1);
assert.equal(element('id4-side-temp').textContent,'-4°C');
assert.equal(element('id4-side-wind').textContent,'33 km/sa');
assert.equal(element('id4-energy-impact').hidden,false,
 'weather warning derives from actual provider packet');
now+=31000;interval();
assert.equal(element('id4-drive-speed').textContent,'—','GPS expires automatically');
assert.equal(element('id4-side-soc').textContent,'— %','OBD expires automatically');
assert.equal(element('id4-side-bar-fill').style.width,'0%');
assert(!classes.has('id4-low-battery'),'stale SoC may not drive a warning');
now+=5400001;interval();
assert.equal(element('id4-side-temp').textContent,'— °C',
 'expired weather estimate must not appear current');
assert.equal(element('id4-energy-impact').hidden,true);
win.molaTelemetry({fresh:false,soc:'17 %'});
assert.equal(element('id4-side-soc').textContent,'— %',
 'unverified SoC does not unlock DC suggestions or illuminate battery');
// Model and battery values are native-backed, never guessed from screenshots.
win.molaTelemetry({fresh:true,soc:'72 %',voltage:'355 V',temperature:'24 °C',
  chargeMode:'Sürüş',powerKw:28,consumptionKwh100:18.1});
assert.equal(element('id4-side-voltage').textContent,'355 V');
assert.equal(element('id4-side-battery-temp').textContent,'24 °C');
assert.equal(element('id4-side-mode').textContent,'Sürüş');
assert.equal(element('id4-side-power').textContent,'— kW','unsupported PID ignored');
assert.equal(element('id4-side-consumption').textContent,'— kWh/100 km');
win.molaTelemetry({fresh:true,soc:'72 %',powerKw:28,powerKwVerified:true,
  consumptionKwh100:18.1,consumptionVerified:true});
assert.equal(element('id4-side-power').textContent,'28.0 kW');
assert.equal(element('id4-side-consumption').textContent,'18.1 kWh/100 km');
// Native route is in lon/lat order. New HUD must NOT draw a second L.map.
win.molaRoute({coordinates:[[35,38],[35.5,38]],distanceKm:55,durationMin:52,
  energyLowKwh:14.5,energyHighKwh:20.3,
  steps:['Gerçek ilk manevra','200 m sonra sağa dön','İleride sola dön']});
assert.equal(element('ev-upcoming-maneuvers').children.length,2,
 'only subsequent OSRM instructions should be displayed');
assert.equal(element('ev-upcoming-maneuvers').children[0].children[1].textContent,
 '200 m sonra sağa dön');

assert.equal(routeCalls,1);
assert.equal(element('id4-side-energy').textContent,'14.5–20.3 kWh');
win.molaStations({kind:'route',items:[],chargeModel:'charge-likely',
  suggested:{id:'s1',lat:38,lon:35.25,routeProgressKm:27.5}});
assert.equal(stationCalls,1);
assert.equal(element('id4-route-stop-markers').children.length,1,
 'only genuine on-corridor native suggestion should get a route marker');
win.molaStations({kind:'route',items:[],chargeModel:'charge-likely',
  suggested:{id:'s2',lat:40,lon:35.25,routeProgressKm:27.5}});
assert.equal(element('id4-route-stop-markers').children.length,0,
 'station outside 5 km corridor must remove prior marker');
win.molaStations({kind:'visible',items:[{id:'national'}]});
assert.equal(element('id4-route-stop-markers').children.length,0,
 'country/viewport records are not route suggestions');
win.molaManeuver({instruction:'Sağa dön',roadRef:'D100',
  straightLineMeters:250,fixTimeMs:now,accuracy:8});
assert.equal(element('id4-road-badge').textContent,'D100');
assert.equal(element('id4-road-badge').hidden,false);
now+=31000;interval();
assert.equal(element('id4-road-badge').hidden,true,
 'stale maneuver must not remain visible');
win.molaStatus({detail:'Rota durduruldu'});
assert.equal(element('id4-route-stop-markers').children.length,0);
assert.equal(element('id4-side-energy').textContent,'— kWh');
assert.equal(element('ev-upcoming-maneuvers').children.length,0,
 'no stale turn instructions after route cancellation');
assert.equal(routeCalls,1);
console.log('PASS Tesla cockpit: real telemetry, 30s TTL, 5 km corridor, model/route reset, callbacks');
