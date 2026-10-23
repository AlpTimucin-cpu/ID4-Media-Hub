// Contract tests for the official-key-only traffic map adapters.
const assert=require('assert');
const vm=require('vm');
const fs=require('fs');
const path=require('path');
const script=fs.readFileSync(path.join(__dirname,
 '../overlay/fermata/src/main/assets/id4/traffic-provider.js'),'utf8');
function world(keys,shouldFail){
 const flags=new Set(),holder={replaceChildren(){this.empty=true}};
 const created={scripts:[],googleMaps:0,googleTraffic:0,yandexMaps:0,yandexTraffic:0,
  routes:0,stations:0,states:[],holder};
 function cls(){return {
  add(v){flags.add(v)},remove(v){flags.delete(v)},contains(v){return flags.has(v)}
 }}
 const mapGlobals={
  google:{maps:{
   Map:class{constructor(node,opts){created.googleMaps++;assert.strictEqual(node,holder);this.opts=opts}},
   TrafficLayer:class{setMap(m){if(m)created.googleTraffic++}},
   Polyline:class{constructor(){created.routes++}setMap(){}},
   Marker:class{constructor(){created.stations++}setMap(){}}
  }},
  ymaps:{
   ready(fn){fn()},
   Map:class{constructor(node,opts){created.yandexMaps++;assert.strictEqual(node,holder);
    this.geoObjects={add(x){},remove(x){}}}destroy(){}},
   Polyline:class{},Placemark:class{},
   traffic:{provider:{Actual:class{setMap(m){if(m)created.yandexTraffic++}}}}
  }
 };
 const win={
  ID4_TRAFFIC_CONFIG:keys,
  molaTrafficStatus(s){created.states.push(s)},
  ...mapGlobals
 };
 const doc={body:{classList:cls()},getElementById(id){return id==='traffic-provider-map'?holder:null},
  head:{appendChild(tag){created.scripts.push(tag.src);
   if(shouldFail)tag.onerror();
   else tag.onload()}}
  ,createElement(){return {src:'',async:false,onload:null,onerror:null}}};
 const ctx={window:win,document:doc,Promise,Array,Math,Number,String,Error};
 vm.runInNewContext(script,ctx,{filename:'traffic-provider.js'});
 return {api:win.Id4TrafficProviders,created,flags};
}
(async()=>{
 const none=world({googleKey:'',yandexKey:''},false);
 assert.strictEqual(none.api.configured().google,false);
 assert.strictEqual(none.api.configured().yandex,false);
 assert.strictEqual(await none.api.activate('google',[39,35],7),false);
 assert.strictEqual(none.created.scripts.length,0,'missing key cannot load paid traffic SDK');
 assert.strictEqual(none.api.getActive(),'osm');
 assert(none.created.states[0].detail.includes('API anahtarı'),'no fake traffic');
 const valid=world({googleKey:'GOOGLE_TEST_KEY_123456789',
  yandexKey:'YANDEX_TEST_KEY_123456789'},false);
 valid.api.updateRoute([[39,35],[39.1,35.2]]);
 valid.api.updateStations([{lat:39,lon:35,title:'Gerçek örnek test istasyonu'}]);
 valid.api.updatePosition({lat:39.01,lon:35.02},false);
 assert.strictEqual(await valid.api.activate('google',[39,35],8),true);
 assert.strictEqual(valid.api.getActive(),'google');
 assert.strictEqual(valid.created.googleMaps,1);
 assert.strictEqual(valid.created.googleTraffic,1);
 assert.strictEqual(valid.created.routes,1);
 assert.strictEqual(valid.created.stations,2,
  'provider should retain both station and GPS position pins');
 assert(valid.flags.has('provider-active'));
 assert(valid.created.scripts[0].startsWith('https://maps.googleapis.com/'),
   'Google SDK must come from official host');
 assert.strictEqual(await valid.api.activate('yandex',[39,35],8),true);
 assert.strictEqual(valid.api.getActive(),'yandex');
 assert.strictEqual(valid.created.yandexMaps,1);
 assert.strictEqual(valid.created.yandexTraffic,1);
 assert(valid.created.scripts[1].startsWith('https://api-maps.yandex.ru/2.1/'),
  'Yandex must use official traffic provider');
 assert.strictEqual(await valid.api.close(),true);
 assert.strictEqual(valid.api.getActive(),'osm');
 assert(!valid.flags.has('provider-active'),'base map must restore after closing');
 const blocked=world({googleKey:'GOOGLE_TEST_KEY_123456789',
  yandexKey:''},true);
 assert.strictEqual(await blocked.api.activate('google',[38,35],7),false);
 assert.strictEqual(blocked.api.getActive(),'osm','failed SDK must not hide OSM map');
 assert(!blocked.flags.has('provider-active'));
 console.log('PASS: Google/Yandex official traffic adapters, key gate and OSM fallback');
})().catch(e=>{console.error(e);process.exitCode=1});
