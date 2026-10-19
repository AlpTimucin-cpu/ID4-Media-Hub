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
  addEventListener(name,fn){this.handlers[name]=fn},
  appendChild(child){this.children.push(child)},
  replaceChildren(){this.children=[]},
  setAttribute(){},
  get length(){return this.children.length}
 };
}
function get(id){return elements[id]||(elements[id]=el());}
const messages=[],timeouts=[];
const calls={suggest:[],route:[],location:0,stop:0};
const L={
 map(){return {setView(){return this},on(){return this},removeLayer(){},
  fitBounds(){},zoomIn(){},zoomOut(){},panTo(){}}},
 tileLayer(){return {addTo(){return this}}},
 circleMarker(){return {addTo(){return this},bindPopup(){return this},setLatLng(){}}},
 polyline(){return {addTo(){return this},getBounds(){return []}}}
};
const win={
 MolaNative:{
  suggest(field,q,id){calls.suggest.push({field,q,id})},
  route(...args){calls.route.push(args)},
  location(){calls.location++},stop(){calls.stop++},
  voice(){},back(){},stations(){},sensors(){}
 }
};
const context={
 window:win,document:{getElementById:get,createElement:el},
 L,setTimeout(fn){timeouts.push(fn);return timeouts.length},
 clearTimeout(){},console,Number,Array,Object,String,Math,
 };
vm.createContext(context);vm.runInContext(inline,context,{timeout:2000});
function typing(field,query){
 get(field).value=query;get(field).handlers.input();
 while(timeouts.length)timeouts.shift()();
 return calls.suggest[calls.suggest.length-1];
}
const first=typing('from','Niğde');
assert.equal(first.field,'from');assert.equal(first.q,'Niğde');
win.molaSuggestions({field:'from',requestId:first.id,items:[
 {label:'Niğde, Türkiye',lat:37.966,lon:34.675}
]});
assert.equal(get('from-list').children.length,1,'origin suggestions not visible');
get('from-list').children[0].onclick();
const next=typing('to','Antalya');
assert.equal(next.field,'to');assert.equal(next.q,'Antalya');
win.molaSuggestions({field:'to',requestId:next.id-1,
 items:[{label:'Wrong stale match',lat:36.9,lon:30.7}]});
assert.equal(get('to-list').children.length,0,'outdated suggestions accepted');
win.molaSuggestions({field:'to',requestId:next.id,items:[
 {label:'Antalya Eğitim ve Araştırma Hastanesi',lat:36.893,lon:30.68},
 {label:'Antalya, Türkiye',lat:36.895,lon:30.7}
]});
assert.equal(get('to-list').children.length,2,'destination suggestions not visible');
get('to-list').children[0].onclick();
get('plan').onclick();
assert.equal(calls.route.length,1,'manual route never called');
assert.equal(calls.route[0][0],37.966);
assert.equal(calls.route[0][1],34.675);
assert.equal(calls.route[0][2],36.893);
assert.equal(calls.route[0][3],30.68);
assert.equal(calls.route[0][4],false,'manual route must not demand GPS');
assert.equal(calls.location,0,'GPS triggered without user choice');
get('swap').onclick();get('plan').onclick();
assert.equal(calls.route[1][0],36.893);
assert.equal(calls.route[1][2],37.966);
assert(html.includes('NEREDEN') && html.includes('NEREYE'));
console.log('PASS: dual manual endpoints, autocomplete, stale results, route and swap (no GPS needed).');
