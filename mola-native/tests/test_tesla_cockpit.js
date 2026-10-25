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
const elements=new Map();
function element(id){
 if(!elements.has(id)) elements.set(id,{
   id,textContent:'',hidden:false,style:{},attrs:{},
   setAttribute(k,v){this.attrs[k]=v},
   removeAttribute(k){delete this.attrs[k]},
 });
 return elements.get(id);
}
const classes=new Set();
const original={
  molaPosition(){positionCalls++},
  molaTelemetry(){telemetryCalls++},
  molaCurrentWeather(){weatherCalls++},
  molaCurrentWeatherError(){},
};
const win={
 ...original,
 document:{
   getElementById:element,
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
console.log('PASS Tesla cockpit: measured-only, 30s TTL, low battery, weather model, callbacks');
