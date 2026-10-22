/* Test screenshot-inspired cockpit with an isolated in-memory DOM, no OBD demo. */
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const html=fs.readFileSync(process.argv[2],'utf8');
const src=fs.readFileSync(process.argv[3],'utf8');
const css=fs.readFileSync(process.argv[4],'utf8');
assert(html.includes('href="cockpit.css"'),'cockpit CSS not linked');
assert(html.includes('src="cockpit.js"'),'cockpit JS not linked');
assert(css.includes('orientation:landscape'),'landscape CSS missing');
assert(!src.includes('68%')&&!src.includes('318 km')&&!src.includes('355 V'),
 'reference screenshot numbers may not be hardcoded');
const nodes={};
class MockNode {
 constructor(tag){
  this.tag=tag;this.children=[];this.style={};this.textContent='';this.className='';
  this.classList={set:new Set(),add(x){this.set.add(x)},remove(x){this.set.delete(x)},
    contains(x){return this.set.has(x)},toggle(x){if(this.set.has(x)){this.set.delete(x);return false}
      this.set.add(x);return true}};
 }
 set id(v){this._id=v;nodes[v]=this}
 get id(){return this._id}
 appendChild(x){this.children.push(x);return x}
 setAttribute(){}
 addEventListener(){}
 click(){if(this.onclick)this.onclick()}
}
nodes.map=new MockNode('div');
const sheet=new MockNode('section');
nodes['compact-weather']=new MockNode('div');
[1,5,10,15].forEach(i=>{
 let e=new MockNode('span');e.textContent='+'+i+' dk · hava bekleniyor';
 nodes['compact-weather'].appendChild(e);
});
nodes['to']=new MockNode('input');nodes.to.value='Ankara, Türkiye';
nodes['route-edit']=new MockNode('button');
let invoked=0,routeCalls=0,obdCalls=0;
nodes['route-edit'].onclick=()=>{invoked++};
nodes['connect-obd']=new MockNode('button');
nodes['connect-obd'].onclick=()=>{obdCalls++};
const body=new MockNode('body');
const doc={body,
 getElementById:id=>nodes[id]||null,
 createElement:tag=>new MockNode(tag),
 querySelector:s=>s==='.sheet'?sheet:null};
const win={
 setInterval:fn=>{interval=fn},
 molaRoute:()=>{routeCalls++},
 molaTelemetry:()=>{},
 molaWeather:()=>{},
 molaWeatherError:()=>{},
 molaStations:()=>{},
 molaStationError:()=>{}
};
let interval;
const context={document:doc,window:win,console,Date,Number,Math,String,Array,
 setInterval:fn=>{interval=fn}};
vm.createContext(context);
vm.runInContext(src,context,{timeout:3000});
assert.equal(nodes['id4-hud-soc'].textContent,'— %');
assert.equal(nodes['id4-hud-range'].textContent,'— km');
assert.equal(nodes['id4-hud-energy'].textContent,'— kWh');
win.molaTelemetry({fresh:true,soc:'68.0 %',voltage:'355.0 V',temperature:'24.0 °C',
 chargeMode:'Sürüş',status:'OBD bağlı'});
assert.equal(nodes['id4-hud-fill'].style.width,'68%');
assert(nodes['id4-hud-voltage'].textContent.includes('355.0 V'));
win.molaTelemetry({fresh:false,status:'Bağlantı kesildi'});
assert.equal(nodes['id4-hud-soc'].textContent,'— %');
assert.equal(nodes['id4-hud-fill'].style.width,'0%');
win.molaRoute({coordinates:[[34.6,37.9],[30.7,36.8]],distanceKm:530.2,durationMin:411,
 energyLowKwh:84.8,energyHighKwh:127.2,steps:['Sağa dön'],provider:'OSRM',
 stopIncluded:false});
assert.equal(routeCalls,1);
assert(body.classList.contains('id4-route-visible'));
assert(nodes['id4-hud-summary'].textContent.includes('530.2 km'));
assert(nodes['id4-hud-energy'].textContent.includes('84.8–127.2'));
assert.equal(nodes['id4-hud-station-count'].textContent,'İstasyon verisi bekleniyor');
win.molaStations({kind:'route',items:[{title:'İstasyon 1'},{title:'İstasyon 2'}]});
assert(nodes['id4-hud-station-count'].textContent.includes('2 EPDK'));
nodes['compact-weather'].children[0].textContent='+1 dk · 16°C';
win.molaWeather({cards:[]});
assert(nodes['id4-hud-weather'].textContent.includes('16°C'));
nodes['id4-hud-obd'].click();
assert.equal(obdCalls,1);
nodes['id4-hud-reset'].click();
assert.equal(invoked,1);
assert(!body.classList.contains('id4-route-visible'));
console.log('PASS: no fake metrics; real SoC only; disconnect clears values; route, weather, stations and actions bound.');
