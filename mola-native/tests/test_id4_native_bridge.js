// Offline contract regression tests for the actual packaged Android WebView
// adapter. Fake packets are TEST fixtures only, never vehicle measurements.
'use strict';
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const source=fs.readFileSync(path.resolve(__dirname,
  '../overlay/fermata/src/main/assets/id4/id4-navigator-adapter.js'),'utf8');
const order=[],calls=[];
let routeOriginalThrows=false;
const originalAndroidBridge=Object.freeze({nativeMethod(){return 'preserved'}});
const addon={
  attachExistingMap(map){calls.push('attach');return !!map},
  ingestNativeRoute(p){calls.push('route');return !!p},
  setRouteStations(p){calls.push('stations');return p.items.length},
  updateLocation(lat,lon){calls.push('gps');return true},
  updateCarData(speed,soc){calls.push(['telemetry',speed,soc]);return true},
  clearRoute(){calls.push('clear')},
  validateEPDKDataContract(p){
    calls.push('validate');
    return {ok:!!(p.metadata && p.metadata.coverage==='country' &&
      p.stations.length===5000)};
  },
  setStations(stations,metadata){calls.push('country');return stations.length},
  getState(){return {soc:18,position:null,countryStationsVerified:false}}
};
const w={ID4LiveMap:addon,id4LiveLeafletMap:{addLayer(){}},
 AndroidBridge:originalAndroidBridge};
for(const name of ['molaRoute','molaStations','molaPosition','molaTelemetry','molaStatus']){
 w[name]=()=>{
  order.push(name);
  if(name==='molaRoute' && routeOriginalThrows)throw new Error('test callback failure');
 };
}
const d={querySelector(){return null},getElementById(){return null}};
vm.runInNewContext(source,{window:w,document:d,console,Date,Number,String,
 Array,JSON,Object,Math,RegExp}, {filename:'id4-navigator-adapter.js',timeout:3000});
assert.strictEqual(w.AndroidBridge,originalAndroidBridge,'do not replace Java interface');
assert(w.ID4NativeBridge,'typed JS entrypoint exists');
const bridge=w.ID4NativeBridge;
assert.equal(bridge.receive('untrusted',{}),false);
assert.equal(bridge.receive('telemetry','{invalid'),false);
assert.equal(bridge.receive('telemetry',[]),false);
assert.equal(bridge.receive('route',JSON.stringify({coordinates:[[35,39],[36,40]]})),true);
assert.deepEqual(order,['molaRoute']);
assert(calls.includes('route'));
assert.equal(bridge.receive('stations',{kind:'route',items:[{id:'x'}]}),true);
assert(calls.includes('stations'));
assert(!calls.includes('country'),'route subset must never become national data');
assert.equal(bridge.epdkCountry({metadata:{coverage:'route'},stations:[]}),false);
assert.equal(bridge.epdkCountry({metadata:{coverage:'country'},stations:[]}),false);
const national=Array.from({length:5000},(_,i)=>({id:'test-'+i}));
assert.equal(bridge.epdkCountry({metadata:{coverage:'country'},stations:national}),true);
assert(calls.includes('country'),'only validated full-country may reach core');
assert.equal(bridge.receive('telemetry',{fresh:true,soc:'18.3'}),true);
assert(calls.some(p=>Array.isArray(p)&&p[0]==='telemetry'&&p[2]===18.3));
assert.equal(bridge.receive('telemetry',{fresh:false,soc:'12'}),true);
assert(calls.some(p=>Array.isArray(p)&&p[0]==='telemetry'&&p[2]===null),
 'stale SoC must become null');
assert.equal(bridge.receive('gps',{
 lat:39,lon:35,timeMs:Date.now()-999999,accuracy:8,speedKmh:65
}),true);
const gpsCount=calls.filter(x=>x==='gps').length;
assert.equal(gpsCount,0,'old GPS does not enter addon');
assert.equal(bridge.receive('gps',{
 lat:39,lon:35,timeMs:Date.now(),accuracy:8,speedKmh:65
}),true);
assert.equal(calls.filter(x=>x==='gps').length,1);
routeOriginalThrows=true;
assert.equal(bridge.receive('route',{coordinates:[[35,39],[36,40]]}),true,
 'a Mola callback exception must not kill the bridge');
assert.equal(bridge.snapshot().soc,18);
console.log('PASS: typed Android bridge, malformed packets, stale SoC/GPS, no country escalation');
