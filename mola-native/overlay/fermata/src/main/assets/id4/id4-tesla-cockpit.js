/* ID.4 premium cockpit — render ONLY known native/model data.
 * PROTECTED: do not mutate ID4NativeBridge, id4-live-map.js, legacy Mola map,
 * national EPDK data, OSRM routing or video/media modules.
 * The existing co-* route HUD owns ETA, maneuver, charging decisions and
 * progress projection. This module is a defensive, idempotent visual observer.
 */
(function(root){
'use strict';
var TTL=30000, WEATHER_TTL=5400000;
var S={soc:null,socAt:0,speed:null,gpsAt:0,gear:null,
  voltage:null,batteryTemp:null,chargeMode:null,powerKw:null,consumption:null,
  temp:null,wind:null,weatherAt:0,routeActive:false,
  routeKm:null,routeCoords:null,energyLow:null,energyHigh:null,
  suggested:null,maneuverAt:0};
var started=false,stopSignature='';
function finite(v){return typeof v==='number'&&Number.isFinite(v)}
function range(v,min,max){return finite(v)&&v>=min&&v<=max?v:null}
function safeStr(v,limit){
 if(typeof v!=='string')return null;
 var s=v.trim();
 return s&&s.length<=limit&&s.indexOf('<')<0?s:null;
}
function socNumber(v){
 try {
  if(finite(v))return range(v,0,100);
  if(typeof v!=='string')return null;
  var m=/^\s*(\d{1,3}(?:[.,]\d+)?)\s*%?\s*$/.exec(v);
  return m?range(Number(m[1].replace(',','.')),0,100):null;
 }catch(_){return null}
}
function measure(v,unit,min,max){
 // Native bridge generally emits strings such as '370 V' or '23 °C'.
 // Parse the ENTIRE field, not parseFloat('23 garbage').
 try{
  if(finite(v))return range(v,min,max);
  if(typeof v!=='string')return null;
  var m=/^\s*(-?\d+(?:[.,]\d+)?)\s*(V|°C|kW|kWh\/100 km)?\s*$/.exec(v);
  if(!m||m[2]&&m[2]!==unit)return null;
  return range(Number(m[1].replace(',','.')),min,max);
 }catch(_){return null}
}
function validAge(t,ttl,now){
 return finite(t)&&t>0&&now>=t&&now-t<ttl;
}
function el(id){try{return root.document&&root.document.getElementById(id)}catch(_){return null}}
function set(id,txt){
 try{var node=el(id);if(node)node.textContent=String(txt)}catch(_){}
}
function hidden(id,flag){
 try{var n=el(id);if(n)n.hidden=!!flag}catch(_){}
}
function validGps(x){
 try{
  var now=Date.now();
  return !!x&&range(x.lat,35,43)!==null&&range(x.lon,25,46)!==null&&
   validAge(x.timeMs,TTL,now)&&finite(x.accuracy)&&
   x.accuracy>=0&&x.accuracy<=35&&Number.isInteger(x.speedKmh)&&
   range(x.speedKmh,0,252)!==null;
 }catch(_){return false}
}
function clearRoute(){
 S.routeActive=false;S.routeKm=null;S.routeCoords=null;S.suggested=null;
 S.energyLow=null;S.energyHigh=null;S.maneuverAt=0;
 try{var n=el('id4-route-stop-markers');if(n)n.textContent=''}catch(_){}
 hidden('id4-road-badge',true);
}
function validRoutePoint(p){
 return Array.isArray(p)&&p.length>=2&&range(p[1],35,43)!==null&&
  range(p[0],25,46)!==null;
}
function distanceFromRouteKm(lat,lon,coordinates){
 // Bounding data is a hint, not proof of corridor coverage. Enforce <=5 km
 // independently before showing a charge suggestion pin.
 if(!Array.isArray(coordinates)||coordinates.length<2||coordinates.length>100000)return Infinity;
 var cos=Math.cos(lat*Math.PI/180);
 var best=Infinity;
 var step=Math.max(1,Math.ceil(coordinates.length/2400));
 var prev=coordinates[0];
 if(!validRoutePoint(prev))return Infinity;
 for(var i=step;i<coordinates.length+step;i+=step){
  var item=coordinates[Math.min(coordinates.length-1,i)];
  if(!validRoutePoint(item))return Infinity;
  var ax=(prev[0]-lon)*111.2*cos, ay=(prev[1]-lat)*111.2;
  var bx=(item[0]-lon)*111.2*cos, by=(item[1]-lat)*111.2;
  var dx=bx-ax,dy=by-ay,div=dx*dx+dy*dy;
  var t=div?Math.max(0,Math.min(1,-(ax*dx+ay*dy)/div)):0;
  best=Math.min(best,Math.hypot(ax+dx*t,ay+dy*t));
  prev=item;
 }
 return best;
}
function routeIsVisible(){
 try{var c=el('vehicle-cockpit');return !!(c&&c.classList&&c.classList.contains('has-route'))}
 catch(_){return S.routeActive}
}
function renderStop(){
 try {
  var holder=el('id4-route-stop-markers');
  if(!holder)return;
  var st=S.suggested;
  var signature=st&&S.routeKm?String(st.id)+':'+st.km+':'+S.routeKm:'';
  if(!st){
    // The previous route's candidate must disappear immediately after a new
    // route, provider error or invalid station payload.
    stopSignature=''; holder.textContent=''; return;
  }
  if(signature===stopSignature)return;
  stopSignature=signature; holder.textContent='';
  if(!finite(S.routeKm)||S.routeKm<=0||
     !finite(st.km)||st.km<0||st.km>S.routeKm)return;
  var ratio=st.km/S.routeKm*100;
  var pin=root.document.createElement('span');
  pin.className='id4-route-stop-pin';
  pin.style.left=ratio.toFixed(2)+'%';
  pin.title='Rota CCS adayı · '+Math.round(st.km)+' km · müsaitlik doğrulanmadı';
  holder.appendChild(pin);
  // Show no additional symbols unless positions are supported by real packets.
 }catch(err){try{root.console.warn('ID4 stop marker refused',err)}catch(_){}}
}
function render(){
 try{
  var now=Date.now();
  var fresh=validAge(S.socAt,TTL,now);
  var soc=fresh?S.soc:null;
  var gps=validAge(S.gpsAt,TTL,now)?S.speed:null;
  var weather=validAge(S.weatherAt,WEATHER_TTL,now);
  var low=finite(soc)&&soc<20;
  set('id4-drive-speed',finite(gps)?Math.round(gps):'—');
  set('id4-drive-gear','Vites: '+(fresh&&S.gear?S.gear:'—'));
  set('id4-side-soc',finite(soc)?Math.round(soc)+' %':'— %');
  set('id4-side-temp',weather?Math.round(S.temp)+'°C':'— °C');
  set('id4-side-wind',weather?Math.round(S.wind)+' km/sa':'— km/sa');
  set('id4-side-voltage',fresh&&finite(S.voltage)?Math.round(S.voltage)+' V':'— V');
  set('id4-side-battery-temp',fresh&&finite(S.batteryTemp)?
    Math.round(S.batteryTemp)+' °C':'— °C');
  set('id4-side-mode',fresh&&S.chargeMode?S.chargeMode:'—');
  set('id4-side-power',fresh&&finite(S.powerKw)?S.powerKw.toFixed(1)+' kW':'— kW');
  set('id4-side-consumption',fresh&&finite(S.consumption)?
    S.consumption.toFixed(1)+' kWh/100 km':'— kWh/100 km');
  var model=el('co-range');
  // The original native page produces this explicitly-labelled model range
  // from fresh BMS SoC; do NOT recompute with guessed pack capacity.
  var modelText=model&&typeof model.textContent==='string'?model.textContent.trim():'';
  set('id4-side-range',fresh&&/^\d+–\d+ km$/.test(modelText)?modelText:'— km');
  var active=S.routeActive;
  // When existing Mola wipes the route locally it removes 'has-route' without
  // emitting a new callback. Clean stale markers on our next visual tick.
  var cockpit=el('vehicle-cockpit');
  if(cockpit&&cockpit.classList&&
     typeof cockpit.classList.contains==='function'&&
     !cockpit.classList.contains('has-route')&&S.routeActive){
    clearRoute(); active=false;
  }
  set('id4-side-energy',active&&finite(S.energyLow)&&finite(S.energyHigh)?
   S.energyLow.toFixed(1)+'–'+S.energyHigh.toFixed(1)+' kWh':'— kWh');
  var bar=el('id4-side-bar-fill');
  if(bar){
    bar.style.width=finite(soc)?soc+'%':'0%';
    bar.setAttribute('role','progressbar');
    bar.setAttribute('aria-valuemin','0');bar.setAttribute('aria-valuemax','100');
    if(finite(soc))bar.setAttribute('aria-valuenow',String(soc));
    else bar.removeAttribute('aria-valuenow');
  }
  var impact=weather&&(S.temp<0||S.wind>30);
  set('id4-energy-impact',low?'⚠ SoC <%20':
    impact?'⚡ Menzil etkisi (hava modeli)':'');
  hidden('id4-energy-impact',!(low||impact));
  if(root.document&&root.document.body&&root.document.body.classList)
    root.document.body.classList.toggle('id4-low-battery',!!low);
  set('id4-drive-provenance','SoC '+(fresh?'OBD':'yok')+
      ', Hız '+(finite(gps)?'GPS':'yok')+
      ', Hava '+(weather?'model':'yok'));
  if(active)renderStop();
 }catch(err){try{root.console.warn('ID4 cockpit render failed',err)}catch(_){}}
}
function onTelemetry(d){
 try{
  var now=Date.now(),fresh=!!d&&d.fresh===true;
  // Use the native validator's fresh flag; reject optional timestamps if stale.
  if(fresh&&finite(d.measuredAtMs)&&!validAge(d.measuredAtMs,TTL,now))fresh=false;
  var soc=fresh?socNumber(d.soc):null;
  S.soc=soc;S.socAt=soc===null?0:now;
  S.gear=fresh&&d.gearVerified===true&&['P','R','N','D','B'].indexOf(d.gear)!==-1?d.gear:null;
  // All optional readings are invalidated together when the native OBD frame
  // expires or reconnects; unsupported PIDs remain —, never fake 28 kW.
  S.voltage=fresh?measure(d.voltage,'V',0,1000):null;
  S.batteryTemp=fresh?measure(d.temperature,'°C',-50,110):null;
  S.chargeMode=fresh?safeStr(d.chargeMode,32):null;
  S.powerKw=fresh&&d.powerKwVerified===true?range(d.powerKw,-400,400):null;
  S.consumption=fresh&&d.consumptionVerified===true?
    range(d.consumptionKwh100,0,90):null;
 }catch(_){S.soc=null;S.socAt=0;S.gear=null;
   S.voltage=null;S.batteryTemp=null;S.chargeMode=null;
   S.powerKw=null;S.consumption=null;}
 render();
}
function onPosition(p){
 try{
  var valid=validGps(p);
  S.speed=valid?p.speedKmh:null;S.gpsAt=valid?p.timeMs:0;
 }catch(_){S.speed=null;S.gpsAt=0}
 render();
}
function onWeather(w){
 try{
  var now=Date.now();
  var ok=!!w&&w.modelBased===true&&finite(w.temperature)&&
     range(w.temperature,-75,65)!==null&&
     range(w.wind,0,250)!==null&&validAge(w.receivedAt,WEATHER_TTL,now)&&
     finite(w.modelTime)&&Math.abs(now-w.modelTime*1000)<WEATHER_TTL;
  S.temp=ok?w.temperature:null;S.wind=ok?w.wind:null;S.weatherAt=ok?w.receivedAt:0;
 }catch(_){S.temp=null;S.wind=null;S.weatherAt=0}
 render();
}
function onRoute(p){
 try{
  if(!p||!Array.isArray(p.coordinates)||p.coordinates.length<2||
    !finite(p.distanceKm)||p.distanceKm<=0||p.distanceKm>5000||
    !finite(p.durationMin)||p.durationMin<=0||p.durationMin>10000){
    clearRoute();render();return;
  }
  // One route only: the Mola original has already drawn the polyline.
  S.routeActive=true;S.routeKm=p.distanceKm;S.routeCoords=p.coordinates;
  S.suggested=null;stopSignature='';
  try{var oldPin=el('id4-route-stop-markers');if(oldPin)oldPin.textContent=''}catch(_){}
  S.energyLow=range(p.energyLowKwh,0,2000);
  S.energyHigh=range(p.energyHighKwh,0,2000);
  if(S.energyLow===null||S.energyHigh===null||S.energyLow>S.energyHigh){
    S.energyLow=null;S.energyHigh=null;
  }
  hidden('id4-road-badge',true);S.maneuverAt=0;
 }catch(_){clearRoute()}
 render();
}
function onStations(p){
 try {
  // Country/viewport records NEVER enter our route progress overlay.
  if(!p||p.kind!=='route'||!Array.isArray(p.items))return;
  S.suggested=null;stopSignature='';
  var s=p.suggested;
  if(S.routeActive&&p.chargeModel==='charge-likely'&&p.stopIncluded!==true&&
      s&&typeof s==='object'&&
      range(s.lat,35,43)!==null&&range(s.lon,25,46)!==null&&
      finite(s.routeProgressKm)&&S.routeKm&&s.routeProgressKm>=0&&
      s.routeProgressKm<=S.routeKm&&
      distanceFromRouteKm(s.lat,s.lon,S.routeCoords)<=5){
    S.suggested={id:String(s.id||s.title||'charge').slice(0,64),
      km:s.routeProgressKm};
  }
  renderStop();
 }catch(err){
  S.suggested=null;stopSignature='';
  try{root.console.warn('ID4 route charge hint rejected',err)}catch(_){}
 }
 render();
}
function onManeuver(m){
 try{
  var ok=S.routeActive&&m&&typeof m.instruction==='string'&&
     finite(m.fixTimeMs)&&validAge(m.fixTimeMs,TTL,Date.now())&&
     range(m.accuracy,0,60)!==null&&range(m.straightLineMeters,0,200000)!==null;
  // Road badge only if Native supplied it. Do not invent D100/12 km.
  var road=ok?safeStr(m.roadRef||m.roadName,18):null;
  set('id4-road-badge',road||'');
  hidden('id4-road-badge',!road);
  S.maneuverAt=road?Date.now():0;
 }catch(_){hidden('id4-road-badge',true);S.maneuverAt=0}
}
function onStatus(p){
 try{
  if(p&&/durduruldu|iptal edildi/i.test(String(p.detail||'')))clearRoute();
 }catch(_){}
 render();
}
function observe(name,after){
 try{
  var original=root[name];
  if(typeof original!=='function')return false;
  if(original.__id4TeslaObserver===true)return true;
  var wrapped=function(packet){
    var out;
    try{out=original.apply(this,arguments)}
    catch(err){try{root.console.error('Mola callback '+name,err)}catch(_){}}
    try{after(packet)}
    catch(err){try{root.console.warn('ID4 visual callback '+name,err)}catch(_){}}
    return out;
  };
  wrapped.__id4TeslaObserver=true;
  root[name]=wrapped;return true;
 }catch(err){try{root.console.warn('ID4 observer registration',err)}catch(_){}
  return false}
}
function tick(){
 try{
  if(S.maneuverAt&&!validAge(S.maneuverAt,TTL,Date.now())){
    S.maneuverAt=0;hidden('id4-road-badge',true);
  }
  render();
 }catch(err){try{root.console.warn('ID4 visual tick',err)}catch(_){}}
}
function start(){
 try{
  if(started)return true;
  if(!el('id4-tesla-side'))return false;
  observe('molaTelemetry',onTelemetry);
  observe('molaPosition',onPosition);
  observe('molaCurrentWeather',onWeather);
  observe('molaCurrentWeatherError',tick);
  observe('molaRoute',onRoute);
  observe('molaStations',onStations);
  observe('molaManeuver',onManeuver);
  observe('molaStatus',onStatus);
  tick();
  try{root.setInterval(tick,1000)}catch(_){}
  try{root.requestAnimationFrame(function(){
    try{var map=root.id4LiveLeafletMap;
      if(map&&typeof map.invalidateSize==='function')map.invalidateSize({animate:false})
    }catch(_){}
  })}catch(_){}
  started=true;return true;
 }catch(err){try{root.console.warn('ID4 cockpit startup failed',err)}catch(_){}
  return false}
}
root.ID4TeslaCockpit={start:start,render:render,parseSoc:socNumber,
  validAge:validAge,state:function(){return {
    soc:S.soc,socAt:S.socAt,speed:S.speed,gpsAt:S.gpsAt,gear:S.gear,
    temperature:S.temp,wind:S.wind,weatherAt:S.weatherAt,
    routeActive:S.routeActive,routeKm:S.routeKm,suggested:S.suggested
  }}};
start();
})(window);
