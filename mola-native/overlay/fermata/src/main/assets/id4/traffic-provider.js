/* Official map SDK adapters. API keys are optional and supplied by the
 * Android build, not included in this repository. A successful map swap must
 * come from a real Google/Yandex SDK; no scraped tiles or simulated traffic.
 */
(function(global){
'use strict';
var active='osm',providerMap=null,traffic=null,routeLine=null,stationObjects=[],positionMarker=null;
var requested=0,loaded={},loading={};
var configured=global.ID4_TRAFFIC_CONFIG||{};
var holder=function(){return document.getElementById('traffic-provider-map')};
function status(name,detail,ready){
 if(typeof global.molaTrafficStatus==='function')
  global.molaTrafficStatus({provider:name,status:ready?'active':'unavailable',detail:detail});
}
function validKey(key){return typeof key==='string'&&/^[A-Za-z0-9_\\-]{15,200}$/.test(key)}
function keyFor(which){return configured[which==='google'?'googleKey':'yandexKey']||''}
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
 if(traffic&&typeof traffic.setMap==='function')traffic.setMap(null);
 if(providerMap&&active==='yandex'&&typeof providerMap.destroy==='function'){
  providerMap.destroy();
 }
 if(providerMap&&active==='google'){
  if(routeLine)routeLine.setMap(null);
  stationObjects.forEach(function(m){if(m&&m.setMap)m.setMap(null)});
  if(positionMarker&&positionMarker.setMap)positionMarker.setMap(null);
 }
 traffic=null;routeLine=null;providerMap=null;stationObjects=[];positionMarker=null;
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
   stationObjects.push(new global.google.maps.Marker({map:providerMap,
    position:{lat:station.lat,lng:station.lon},
    title:String(station.title||'Şarj istasyonu').slice(0,80)}));
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
    traffic=new global.ymaps.traffic.provider.Actual({},{infoLayerShown:true});
    traffic.setMap(providerMap);
    renderData();resolve();
   }catch(e){reject(e)}
  });
 });
}
function activate(name,center,zoom){
 var seq=++requested;
 if(name==='osm'){
  reset();active='osm';status('osm','OpenStreetMap haritasına dönüldü.',true);
  return Promise.resolve(true);
 }
 if(name!=='google'&&name!=='yandex')return Promise.resolve(false);
 if(!validKey(keyFor(name))){
  status(name,'Canlı trafik için '+(name==='google'?'Google Maps':'Yandex Maps')+
   ' API anahtarı ve uygun lisans gerekir. OSM haritası korunuyor.',false);
  return Promise.resolve(false);
 }
 if(Array.isArray(center)&&center.length===2&&center.every(Number.isFinite))
  lastCenter=center.slice(0,2);
 if(Number.isFinite(zoom))lastZoom=Math.min(18,Math.max(4,Math.round(zoom)));
 // A failed load must not turn the working map blank.
 return scriptOnce(name,keyFor(name)).then(function(){
  if(seq!==requested)return false;
  reset();
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
 configured:function(){return {google:validKey(keyFor('google')),yandex:validKey(keyFor('yandex'))}},
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
