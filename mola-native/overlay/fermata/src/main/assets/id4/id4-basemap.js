/* Resilient ID.4 Leaflet basemap. Only current visible tiles are requested.
 * OSMF tiles: https://operations.osmfoundation.org/policies/tiles/
 * OSM France: https://www.openstreetmap.fr/usage/
 * Built-in Natural Earth public-domain country outlines appear until tiles load.
 * Never represent an outline-only map as a street-level offline navigator. */
(function(g){
'use strict';
var L=g.L,map=null,layer=null,outline=null,cityMarkers=[];
var notify=function(){},state='uninitialized',success=false,errors=0;
var timer=null,epoch=0,srcIndex=0,hasStarted=false,cityPoints=[];
var tomtomKey='',dayMode=false;
var communitySources=[
 {name:'OpenStreetMap',url:'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
  attribution:'© OpenStreetMap contributors',maxZoom:19},
 {name:'OSM Fransa',url:'https://{s}.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png',
  attribution:'© OpenStreetMap contributors · © OpenStreetMap France',maxZoom:19}
];
function validKey(value){
 return typeof value==='string'&&/^[A-Za-z0-9_-]{15,200}$/.test(value);
}
function sources(){
 if(!validKey(tomtomKey))return communitySources;
 return [{
  name:'TomTom Orbis',
  url:'https://api.tomtom.com/maps/orbis/display/raster/tile/{z}/{x}/{y}'+
      '?apiVersion=2&style='+(dayMode?'street-light':'street-dark')+
      '&tileSize=256&geopoliticalView=TR&key='+encodeURIComponent(tomtomKey),
  attribution:'© TomTom · Map data © OpenStreetMap contributors',
  maxZoom:19
 }].concat(communitySources);
}
function emit(kind,detail){
 state=kind;
 notify({status:kind,detail:detail,source:sources()[srcIndex]?.name||'',
  online:kind==='online'});
}
function makeOutline(){
 if(!map||!L||!L.geoJSON||!g.ID4_OFFLINE_COUNTRIES)return;
 if(map.createPane && !map.getPane('id4-country-outline')){
  var pane=map.createPane('id4-country-outline');
  pane.style.zIndex='275';pane.style.pointerEvents='none';
 }
 outline=L.geoJSON(g.ID4_OFFLINE_COUNTRIES,{
  interactive:false,pane:map.getPane&&map.getPane('id4-country-outline')?'id4-country-outline':'overlayPane',
  style:function(f){return {
   color:f.properties?.name==='Turkey'?'#36819c':'#24566d',
   weight:f.properties?.name==='Turkey'?1.6:1,
   opacity:1,fillColor:f.properties?.name==='Turkey'?'#114055':'#0b3143',
   fillOpacity:.93
  }}
 });
 cityPoints.filter(function(c){
  return Array.isArray(c)&&c.length>=3&&
    ['Ankara','İstanbul','İzmir','Antalya','Konya','Bursa','Eskişehir',
     'Kayseri','Adana','Samsun','Gaziantep','Trabzon','Niğde','Kocaeli',
     'Mersin','Diyarbakır','Erzurum'].includes(c[0]);
 }).forEach(function(c){
  if(!L.marker||!L.divIcon)return;
  var marker=L.marker([c[1],c[2]],{interactive:false,
   pane:map.getPane&&map.getPane('id4-country-outline')?'id4-country-outline':'markerPane',
   icon:L.divIcon({className:'id4-offline-city',
    html:'<span>'+String(c[0]).replace(/&/g,'&amp;').replace(/</g,'&lt;')+'</span>',
    iconSize:[82,18],iconAnchor:[41,9]})});
  cityMarkers.push(marker);
 });
}
function outlineVisible(visible){
 if(!outline||!map)return;
 if(visible){
  if(!map.hasLayer||!map.hasLayer(outline))outline.addTo(map);
  cityMarkers.forEach(function(m){if(!map.hasLayer||!map.hasLayer(m))m.addTo(map)});
 }else{
  if(map.hasLayer&&map.hasLayer(outline))map.removeLayer(outline);
  cityMarkers.forEach(function(m){if(map.hasLayer&&map.hasLayer(m))map.removeLayer(m)});
 }
}
function discard(){
 if(timer!==null){clearTimeout(timer);timer=null}
 if(layer&&map&&map.removeLayer)map.removeLayer(layer);
 layer=null;
}
function exhausted(){
 discard();outlineVisible(true);
 emit('offline','Çevrimiçi harita yüklenemedi · yalnız ülke sınırları · Yeniden dene');
}
function attempt(i){
 if(!map||!L||!L.tileLayer)return exhausted();
 discard();
 srcIndex=i;var generation=++epoch;success=false;errors=0;
 var options=sources();
 if(i>=options.length)return exhausted();
 outlineVisible(true);
 emit('loading',options[i].name+' haritası yükleniyor…');
 var source=options[i];
 var tile=L.tileLayer(source.url,{maxZoom:source.maxZoom,attribution:source.attribution,
  minZoom:2,noWrap:false,keepBuffer:1,updateWhenIdle:true,detectRetina:false});
 layer=tile;
 tile.on('tileload',function(){
  if(generation!==epoch||success)return;
  success=true;
  if(timer!==null){clearTimeout(timer);timer=null}
  outlineVisible(false);
  emit('online',source.name+' haritası açık');
 });
 tile.on('tileerror',function(){
  if(generation!==epoch||success)return;
  if(++errors>=3)attempt(i+1);
 });
 timer=setTimeout(function(){
  if(generation!==epoch||success)return;
  attempt(i+1);
 },9500);
 try{tile.addTo(map)}catch(e){attempt(i+1)}
}
g.Id4BaseMap={
 init:function(m,fn,cities){
  map=m;notify=typeof fn==='function'?fn:notify;
  cityPoints=Array.isArray(cities)?cities:[];
  makeOutline();outlineVisible(true);hasStarted=true;
  attempt(0);
 },
 retry:function(){if(!map)return false;attempt(0);return true},
 setTomTomKey:function(value){
  if(typeof value!=='string'||(value!==''&&!validKey(value)))return false;
  if(tomtomKey===value)return true;
  tomtomKey=value;
  if(map)attempt(0);
  return true;
 },
 setDay:function(value){
  dayMode=!!value;
  if(map&&validKey(tomtomKey))attempt(0);
 },
 status:function(){return {status:state,online:state==='online',
  source:sources()[srcIndex]?.name||'',fallbackOnly:state==='offline'}},
 sources:function(){return sources().map(function(x){return x.name})},
 close:function(){epoch++;discard();outlineVisible(false);hasStarted=false;
  emit('uninitialized','Harita kapatıldı.')}
};
})(window);
