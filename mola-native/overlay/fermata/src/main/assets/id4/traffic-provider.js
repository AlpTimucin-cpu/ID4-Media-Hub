/* Official TomTom Orbis v2 live-traffic overlay for the native Leaflet/OSM map.
 * Google/Yandex live SDKs removed: not confirmed functional in this application.
 * Never display simulated traffic or change OSM route ETA without live routing.
 */
(function(global){
'use strict';
var active='osm',requested=0,leafletMap=null,tomtomTiles=[],
    tomtomTimer=null,tomtomPending=null,lastCenter=[38.9,35.1],lastZoom=7;
var config=global.ID4_TRAFFIC_CONFIG||{};
function validKey(key){return typeof key==='string'&&/^[A-Za-z0-9_-]{15,200}$/.test(key)}
function keyFor(){return config.tomtomKey||''}
function status(provider,detail,ready){
 if(typeof global.molaTrafficStatus==='function')
  global.molaTrafficStatus({provider:provider,status:ready?'active':'unavailable',detail:detail});
}
function reset(){
 if(tomtomTimer!==null){clearInterval(tomtomTimer);tomtomTimer=null}
 if(leafletMap&&leafletMap.removeLayer)tomtomTiles.forEach(function(layer){
   leafletMap.removeLayer(layer)
 });
 tomtomTiles=[];
 if(tomtomPending){var abort=tomtomPending;tomtomPending=null;abort(false)}
}
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
    status('tomtom','TomTom trafik zaman aşımı: OpenFreeMap haritası korundu.',false);
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
    status('tomtom','TomTom trafik döşemesi alınamadı (izin, kota veya ağ). OpenFreeMap açık.',false);
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
   status('tomtom','TomTom katmanı başlatılamadı; OpenFreeMap açık.',false);
   finish(false);
  }
 });
}


function activate(which,center,zoom){
 var seq=++requested;
 if(which==='osm'){
  reset();active='osm';
  status('osm','OpenStreetMap açık · trafik katmanı kapalı.',false);
  return Promise.resolve(true);
 }
 if(which!=='tomtom')return Promise.resolve(false);
 if(!validKey(keyFor())){
  status('tomtom','TomTom API anahtarı gerekli; OSM korunuyor.',false);
  return Promise.resolve(false);
 }
 if(Array.isArray(center)&&center.length===2&&center.every(Number.isFinite))
  lastCenter=center.slice();
 if(Number.isFinite(zoom))lastZoom=Math.max(4,Math.min(19,zoom));
 return openTomTom(seq);
}
global.Id4TrafficProviders={
 activate:activate,
 getActive:function(){return active},
 configured:function(){return {tomtom:validKey(keyFor())}},
 attachLeaflet:function(map){leafletMap=map},
 setTomTomKey:function(key){
  if(typeof key!=='string'||(key!==''&&!validKey(key)))return false;
  requested++;reset();active='osm';config.tomtomKey=key;
  status('tomtom',key?
     'TomTom anahtarı kaydedildi; rota oluşturulduğunda trafik otomatik yüklenecek.':
     'TomTom anahtarı temizlendi.',false);
  // The route, not a settings screen or app launch, starts paid traffic tiles.
  return true;
 },
 updateViewport:function(c,z){
  if(Array.isArray(c)&&c.length===2&&c.every(Number.isFinite))lastCenter=c.slice();
  if(Number.isFinite(z))lastZoom=z;
 },
 updateRoute:function(){},updateStations:function(){},updatePosition:function(){},
 close:function(){return activate('osm')}
};
})(window);
