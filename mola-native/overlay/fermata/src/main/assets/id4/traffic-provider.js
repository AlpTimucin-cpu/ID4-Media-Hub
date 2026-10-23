/* Official map SDK adapters. API keys are optional and supplied by the
 * Android build, not included in this repository. A successful map swap must
 * come from a real Google/Yandex SDK; no scraped tiles or simulated traffic.
 */
(function(global){
'use strict';
var active='osm',providerMap=null,traffic=null,routeLine=null,stationObjects=[],positionMarker=null;
var requested=0,loaded={},loading={},lastTrafficRefresh=0;
var leafletMap=null,tomtomTiles=[],tomtomTimer=null,tomtomPending=null;
var configured=global.ID4_TRAFFIC_CONFIG||{};
var holder=function(){return document.getElementById('traffic-provider-map')};
function status(name,detail,ready){
 if(typeof global.molaTrafficStatus==='function')
  global.molaTrafficStatus({provider:name,status:ready?'active':'unavailable',detail:detail});
}
function validKey(key){return typeof key==='string'&&/^[A-Za-z0-9_\\-]{15,200}$/.test(key)}
function keyFor(which){return configured[which==='google'?'googleKey':which==='yandex'?'yandexKey':'tomtomKey']||''}
function scriptOnce(which,key){
 if(loaded[which])return Promise.resolve();
 if(loading[which])return loading[which];
 loading[which]=new Promise(function(resolve,reject){
  var script=document.createElement('script');
  var host=which==='google'?'https://maps.googleapis.com/maps/api/js?key=':
    'https://api-maps.yandex.ru/2.1/?lang=tr_TR&apikey=';
  script.src=host+encodeURIComponent(key);
  script.async=true;
  script.onload=function(){loaded[which]=true;resolve()};
  script.onerror=function(){delete loading[which];reject(new Error('SDK yüklenemedi'))};
  document.head.appendChild(script);
 });
 return loading[which];
}
function reset(){
 if(tomtomTimer!==null){clearInterval(tomtomTimer);tomtomTimer=null}
 tomtomTiles.forEach(function(layer){if(leafletMap&&leafletMap.removeLayer)
   leafletMap.removeLayer(layer)});
 tomtomTiles=[];
 if(tomtomPending){var abort=tomtomPending;tomtomPending=null;abort(false)}
 if(traffic&&typeof traffic.setMap==='function')traffic.setMap(null);
 if(providerMap&&active==='yandex'&&typeof providerMap.destroy==='function'){
  providerMap.destroy();
 }
 if(providerMap&&active==='google'){
  if(routeLine)routeLine.setMap(null);
  stationObjects.forEach(function(m){if(m&&m.setMap)m.setMap(null)});
  if(positionMarker&&positionMarker.setMap)positionMarker.setMap(null);
 }
 traffic=null;routeLine=null;providerMap=null;stationObjects=[];positionMarker=null;lastTrafficRefresh=0;
 if(holder())holder().replaceChildren();
 document.body.classList.remove('provider-active');
}
function routePoints(coords){
 return Array.isArray(coords)?coords.filter(function(p){
  return Array.isArray(p)&&p.length>1&&Number.isFinite(p[0])&&Number.isFinite(p[1]);
 }).slice(0,2400):[];
}
var currentRoute=[],currentStations=[],lastPosition=null,lastCenter=[38.9,35.1],lastZoom=7;
function renderData(){
 if(!providerMap||!holder())return;
 if(active==='google'){
  if(routeLine)routeLine.setMap(null);
  stationObjects.forEach(function(marker){if(marker&&marker.setMap)marker.setMap(null)});
  if(positionMarker&&positionMarker.setMap)positionMarker.setMap(null);
  positionMarker=null;
  stationObjects=[];
  if(currentRoute.length>=2){
   routeLine=new global.google.maps.Polyline({map:providerMap,
    path:currentRoute.map(function(v){return {lat:v[0],lng:v[1]}}),
    strokeColor:'#258ee9',strokeWeight:6,strokeOpacity:.95});
  }
  currentStations.slice(0,100).forEach(function(station){
   if(!station||!Number.isFinite(station.lat)||!Number.isFinite(station.lon))return;
   var marker=new global.google.maps.Marker({map:providerMap,
    position:{lat:station.lat,lng:station.lon},
    title:String(station.title||'Şarj istasyonu').slice(0,80)});
   if(marker.addListener)marker.addListener('click',function(){
    if(typeof global.molaTrafficStationClick==='function')
     global.molaTrafficStationClick(station);
   });
   stationObjects.push(marker);
  });
  if(lastPosition)positionMarker=new global.google.maps.Marker({map:providerMap,
   position:{lat:lastPosition[0],lng:lastPosition[1]},title:'Aracım · GPS'});
 }else if(active==='yandex'){
  if(routeLine)providerMap.geoObjects.remove(routeLine);
  stationObjects.forEach(function(obj){providerMap.geoObjects.remove(obj)});
  if(positionMarker)providerMap.geoObjects.remove(positionMarker);
  positionMarker=null;stationObjects=[];
  if(currentRoute.length>=2){
   routeLine=new global.ymaps.Polyline(currentRoute,{},{
    strokeColor:'#258ee9',strokeWidth:6,strokeOpacity:.93});
   providerMap.geoObjects.add(routeLine);
  }
  currentStations.slice(0,100).forEach(function(station){
   if(!station||!Number.isFinite(station.lat)||!Number.isFinite(station.lon))return;
   var obj=new global.ymaps.Placemark([station.lat,station.lon],{
    hintContent:String(station.title||'Şarj istasyonu').slice(0,80)});
   if(obj.events&&obj.events.add)obj.events.add('click',function(){
    if(typeof global.molaTrafficStationClick==='function')
     global.molaTrafficStationClick(station);
   });
   providerMap.geoObjects.add(obj);stationObjects.push(obj);
  });
  if(lastPosition){
   positionMarker=new global.ymaps.Placemark(lastPosition,{
    hintContent:'Aracım · GPS'});
   providerMap.geoObjects.add(positionMarker);
  }
 }
}
function openGoogle(){
 if(!global.google||!global.google.maps||!global.google.maps.Map)
  throw new Error('Google Haritalar SDK başlatılamadı');
 active='google';
 providerMap=new global.google.maps.Map(holder(),{
  center:{lat:lastCenter[0],lng:lastCenter[1]},zoom:lastZoom,
  disableDefaultUI:true,clickableIcons:false,gestureHandling:'greedy'});
 traffic=new global.google.maps.TrafficLayer();
 traffic.setMap(providerMap);
 renderData();
}
function openYandex(){
 return new Promise(function(resolve,reject){
  if(!global.ymaps||!global.ymaps.ready)return reject(new Error('Yandex SDK yok'));
  global.ymaps.ready(function(){
   try{
    active='yandex';
    providerMap=new global.ymaps.Map(holder(),{
     center:lastCenter,zoom:lastZoom,controls:[]});
    // Source: Yandex JS API 2.1 official traffic.provider.Actual example.
    traffic=new global.ymaps.traffic.provider.Actual({autoUpdate:true},
      {infoLayerShown:true});
    traffic.setMap(providerMap);
    lastTrafficRefresh=Date.now();
    // Actual Yandex congestion level is supplied by its licensed SDK;
    // never estimate the 0-10 number from OSRM duration or map imagery.
    function publishTrafficLevel(){
     if(active!=='yandex'||!traffic||!traffic.state||!traffic.state.get)return;
     var ready=traffic.state.get('isInited');
     var level=Number(traffic.state.get('level'));
     var time=Number(traffic.state.get('timestamp'))*1000;
     if(ready===true && Number.isInteger(level)&&level>=0&&level<=10 &&
        Number.isFinite(time)&&Math.abs(Date.now()-time)<60*60*1000)
      if(typeof global.molaTrafficStatus==='function')
       global.molaTrafficStatus({provider:'yandex',status:'active',level:level,
        observedAt:time,detail:'Yandex trafik '+level+'/10 · lisanslı canlı kaynak'});
    }
    if(traffic.state&&traffic.state.events&&traffic.state.events.add)
     traffic.state.events.add('change',publishTrafficLevel);
    publishTrafficLevel();
    renderData();resolve();
   }catch(e){reject(e)}
  });
 });
}

/* TomTom Orbis Traffic API v2; official transparent PNG tiles on existing OSM.
 * One successfully loaded FLOW tile is required before declaring the overlay
 * ready. OSRM route times remain traffic-free and are never adjusted. */
function openTomTom(seq){
 if(!leafletMap||!global.L||!global.L.tileLayer){
  status('tomtom','Leaflet haritası hazır değil; trafik açılamadı.',false);
  return Promise.resolve(false);
 }
 var key=keyFor('tomtom');
 var root='https://api.tomtom.com/maps/orbis/traffic/';
 var q='?apiVersion=2&style=dark&tileSize=256&key='+encodeURIComponent(key);
 var flow=global.L.tileLayer(root+'flow/raster/tile/{z}/{x}/{y}'+q,
  {tileSize:256,opacity:.8,zIndex:340,maxZoom:19,noWrap:true,attribution:'© TomTom'});
 var incidents=global.L.tileLayer(root+'incidents/raster/tile/{z}/{x}/{y}'+q,
  {tileSize:256,opacity:.95,zIndex:341,maxZoom:19,noWrap:true,attribution:'© TomTom'});
 reset();active='tomtom';tomtomTiles=[flow,incidents];
 status('tomtom','TomTom trafik döşemeleri kontrol ediliyor…',false);
 return new Promise(function(resolve){
  var finished=false,errors=0,confirmed=false;
  function finish(ok){
   if(finished)return;finished=true;
   if(tomtomPending===finish)tomtomPending=null;
   resolve(ok);
  }
  tomtomPending=finish;
  var timeout=setTimeout(function(){
   if(finished)return;
   if(seq===requested&&!confirmed){
    reset();active='osm';
    status('tomtom','TomTom trafik zaman aşımı: OSM haritası korundu.',false);
   }
   finish(false);
  },12000);
  function loadedFlow(){
   if(seq!==requested||active!=='tomtom'||finished)return;
   confirmed=true;clearTimeout(timeout);
   status('tomtom','TomTom Orbis trafik yoğunluğu ve yol olayları katmanı yüklendi. OSRM ETA trafiksizdir.',true);
   finish(true);
  }
  function failedFlow(){
   if(seq!==requested||confirmed||finished)return;
   if(++errors>=3){
    clearTimeout(timeout);reset();active='osm';
    status('tomtom','TomTom trafik döşemesi alınamadı (izin, kota veya ağ). OSM açık.',false);
    finish(false);
   }
  }
  flow.on('tileload',loadedFlow).on('tileerror',failedFlow);
  try{
   flow.addTo(leafletMap);
   if(active!=='tomtom'||seq!==requested)return;
   incidents.addTo(leafletMap);
   if(active==='tomtom')tomtomTimer=setInterval(function(){
     if(seq===requested&&active==='tomtom'){
      if(typeof flow.redraw==='function')flow.redraw();
      if(typeof incidents.redraw==='function')incidents.redraw();
     }
   },300000);
  }catch(e){
   clearTimeout(timeout);reset();active='osm';
   status('tomtom','TomTom katmanı başlatılamadı; OSM açık.',false);
   finish(false);
  }
 });
}

function activate(name,center,zoom){
 var seq=++requested;
 if(name==='osm'){
  reset();active='osm';status('osm','OpenStreetMap haritası açık; canlı trafik katmanı kapalı.',false);
  return Promise.resolve(true);
 }
 if(name!=='google'&&name!=='yandex'&&name!=='tomtom')return Promise.resolve(false);
 if(!validKey(keyFor(name))){
  status(name,'Canlı trafik için '+(name==='google'?'Google Maps':name==='yandex'?'Yandex Maps':'TomTom Orbis')+
   ' API anahtarı ve uygun lisans gerekir. OSM haritası korunuyor.',false);
  return Promise.resolve(false);
 }
 if(Array.isArray(center)&&center.length===2&&center.every(Number.isFinite))
  lastCenter=center.slice(0,2);
 if(Number.isFinite(zoom))lastZoom=Math.min(18,Math.max(4,Math.round(zoom)));
 if(name==='tomtom')return openTomTom(seq);
 // A failed load must not turn the working map blank.
 return scriptOnce(name,keyFor(name)).then(function(){
  if(seq!==requested)return false;
  reset();
  // Both SDKs must initialize on an already sized/visible map container;
  // a hidden div produces a blank map even with a valid API key.
  document.body.classList.add('provider-active');
  return Promise.resolve(name==='google'?openGoogle():openYandex()).then(function(){
   if(seq!==requested)return false;
   document.body.classList.add('provider-active');
   status(name,(name==='google'?'Google':'Yandex')+
    ' resmî trafik haritası açıldı. Yoğunluk sağlayıcıdan gelir.',true);
   return true;
  });
 }).catch(function(e){
  if(seq!==requested)return false;
  reset();active='osm';
  status(name,'Trafik haritası açılamadı: '+String(e.message||e).slice(0,90)+
   '. OpenStreetMap kullanılmaya devam ediyor.',false);
  return false;
 });
}
global.Id4TrafficProviders={
 activate:activate,
 getActive:function(){return active},
 configured:function(){return {google:validKey(keyFor('google')),yandex:validKey(keyFor('yandex')),tomtom:validKey(keyFor('tomtom'))}},
 attachLeaflet:function(map){leafletMap=map},
 setTomTomKey:function(key){
  if(typeof key!=='string'||(key!==''&&!validKey(key)))return false;
  if(active==='tomtom'){requested++;reset();active='osm'}
  configured.tomtomKey=key;
  status('tomtom',key?'TomTom anahtarı ayarlandı, katmanı açarak doğrula.':
    'TomTom API anahtarı yok, canlı trafik kapalı.',false);
  return true;
 },
 updateRoute:function(points){currentRoute=routePoints(points);renderData()},
 updateStations:function(items){currentStations=Array.isArray(items)?items.slice(0,120):[];renderData()},
 updatePosition:function(x,follow){
  if(!x||!Number.isFinite(x.lat)||!Number.isFinite(x.lon))return;
  lastPosition=[x.lat,x.lon];
  if(!providerMap)return;
  if(!positionMarker){renderData();return}
  if(active==='google'){
   if(positionMarker.setPosition)positionMarker.setPosition({lat:x.lat,lng:x.lon});
   if(follow&&providerMap.panTo)providerMap.panTo({lat:x.lat,lng:x.lon});
  }else if(active==='yandex'){
   if(positionMarker.geometry&&positionMarker.geometry.setCoordinates)
    positionMarker.geometry.setCoordinates(lastPosition);
   if(follow&&providerMap.setCenter)providerMap.setCenter(lastPosition);
   // Yandex Actual documents that autoUpdate may stop without map
   // mousemove; car users rarely touch the map. Refresh on fresh GPS
   // at most once per 4 minutes, not for every position packet.
   if(traffic&&typeof traffic.update==='function' &&
      Date.now()-lastTrafficRefresh>=240000){
    lastTrafficRefresh=Date.now();
    try{traffic.update()}catch(error){}
   }
  }
 },
 updateViewport:function(center,zoom){
  if(!Array.isArray(center)||!center.every(Number.isFinite))return;
  lastCenter=center.slice(0,2);
  if(Number.isFinite(zoom))lastZoom=Math.min(18,Math.max(4,zoom));
 },
 close:function(){return activate('osm')}
};
})(window);
