/* Landscape-only presentation bound to existing Mola navigation/OBD callbacks.
 * No mock, fixed SoC, guessed range, power, charging time or traffic.
 */
(function(){
 'use strict';
 function byId(id){return document.getElementById(id)}
 function el(tag,cls,text){
  var node=document.createElement(tag);
  if(cls)node.className=cls;
  if(text!=null)node.textContent=String(text);
  return node;
 }
 function card(label,value,valueClass,id){
  var box=el('div','id4-hud-card');
  box.appendChild(el('div','id4-hud-label',label));
  var v=el('strong',valueClass||'id4-hud-value',value);
  if(id)v.id=id;
  box.appendChild(v);
  return box;
 }
 var map=byId('map'),sheet=document.querySelector('.sheet');
 if(!map||!sheet)return;

 var left=el('aside','id4-cockpit-left');left.setAttribute('aria-label','ID.4 araç verileri');
 var battery=card('BATARYA DOLULUK · OBD','— %','id4-hud-soc','id4-hud-soc');
 var bar=el('div','id4-hud-bar');
 var fill=el('span','');fill.id='id4-hud-fill';bar.appendChild(fill);
 battery.appendChild(bar);
 var obdStatus=el('div','id4-hud-note','OBD bağlı değil · gerçek ölçüm bekleniyor');
 obdStatus.id='id4-hud-status';battery.appendChild(obdStatus);
 left.appendChild(battery);
 var range=card('TAHMİNİ MENZİL','— km','id4-hud-range','id4-hud-range');
 range.appendChild(el('div','id4-hud-note','Doğrulanmış menzil/tüketim ölçümü gerekli'));
 left.appendChild(range);
 var tiles=el('div','id4-hud-grid');
 tiles.appendChild(card('HV GERİLİM','— V',null,'id4-hud-voltage'));
 tiles.appendChild(card('BATARYA SICAKLIĞI','— °C',null,'id4-hud-temp'));
 tiles.appendChild(card('ÇALIŞMA / ŞARJ','—',null,'id4-hud-mode'));
 tiles.appendChild(card('ANLIK GÜÇ','— kW'));
 tiles.appendChild(card('ORT. TÜKETİM','— kWh/100 km'));
 tiles.appendChild(card('YOL ENERJİSİ · MODEL','— kWh',null,'id4-hud-energy'));
 left.appendChild(tiles);

 var right=el('aside','id4-cockpit-right');right.setAttribute('aria-label','Rota ve yönlendirme');
 var nav=el('div','id4-hud-card');
 nav.appendChild(el('div','id4-hud-label','ROTA ÖNİZLEMESİ · CANLI TRAFİK YOK'));
 nav.appendChild(el('div','id4-hud-turn','Rota seçilmedi')).id='id4-hud-next';
 function stat(label,id){
  var row=el('div','id4-hud-stat');
  row.appendChild(el('span','',label));
  var value=el('b','','—');value.id=id;row.appendChild(value);nav.appendChild(row);
 }
 stat('Yol uzunluğu','id4-hud-distance');
 stat('Trafiksiz süre','id4-hud-time');
 stat('Şarj kayıtları','id4-hud-station-count');
 right.appendChild(nav);

 var bottom=el('section','id4-cockpit-bottom');
 bottom.setAttribute('aria-label','Yolculuk özeti');
 var row=el('div','id4-bottom-row');
 var destination=el('strong','','Hedef');destination.id='id4-hud-destination';
 var trip=el('span','', 'Rota bekleniyor');trip.id='id4-hud-summary';
 row.appendChild(destination);row.appendChild(trip);bottom.appendChild(row);
 var weather=el('div','id4-bottom-weather','+1 / +5 / +10 / +15 dk hava: veri bekleniyor');
 weather.id='id4-hud-weather';bottom.appendChild(weather);
 var actions=el('div','id4-bottom-actions');
 function action(text,id,callback){
  var button=el('button','',text);button.id=id;button.type='button';
  button.onclick=callback;actions.appendChild(button);
 }
 action('Yeni rota','id4-hud-reset',function(){
  var edit=byId('route-edit');if(edit)edit.click();
  document.body.classList.remove('id4-route-visible');
 });
 action('Rota & hava ayrıntıları','id4-hud-details',function(){
  if(!sheet)return;
  sheet.classList.toggle('details-open');
  sheet.scrollTop=0;
 });
 action('OBD bağla','id4-hud-obd',function(){
  var connect=byId('connect-obd');if(connect)connect.click();
 });
 bottom.appendChild(actions);
 document.body.appendChild(left);
 document.body.appendChild(right);
 document.body.appendChild(bottom);

 var lastFreshAt=0;
 function clearObd(status){
  byId('id4-hud-soc').textContent='— %';
  byId('id4-hud-fill').style.width='0%';
  byId('id4-hud-voltage').textContent='— V';
  byId('id4-hud-temp').textContent='— °C';
  byId('id4-hud-mode').textContent='—';
  byId('id4-hud-status').textContent=status||'OBD bağlı değil';
 }
 function formatted(value,fallback){
  var x=String(value==null?'':value).trim();
  return !x||x==='—'?fallback:x;
 }
 function updateWeather(){
  var items=byId('compact-weather');
  if(!items)return;
  var pieces=Array.prototype.map.call(items.children,function(x){
   return String(x.textContent||'').trim();
  });
  byId('id4-hud-weather').textContent=pieces.join('  ·  ');
 }
 function wrap(name,after){
  var original=window[name];
  if(typeof original!=='function')return;
  window[name]=function(payload){
   var result=original.apply(this,arguments);
   try{after(payload||{})}catch(e){/* decorative HUD cannot break navigation */}
   return result;
  };
 }
 wrap('molaTelemetry',function(d){
  if(!d.fresh){lastFreshAt=0;clearObd(String(d.status||'Ölçüm yok'));return;}
  lastFreshAt=Date.now();
  var soc=formatted(d.soc,'—');
  byId('id4-hud-soc').textContent=soc;
  var number=parseFloat(soc.replace(',','.'));
  byId('id4-hud-fill').style.width=
    Number.isFinite(number)&&number>=0&&number<=100?number+'%':'0%';
  byId('id4-hud-voltage').textContent=formatted(d.voltage,'— V');
  byId('id4-hud-temp').textContent=formatted(d.temperature,'— °C');
  byId('id4-hud-mode').textContent=formatted(d.chargeMode,'—');
  byId('id4-hud-status').textContent='Canlı OBD · deneysel MEB profili';
 });
 wrap('molaRoute',function(r){
  if(!Array.isArray(r.coordinates)||r.coordinates.length<2)return;
  document.body.classList.add('id4-route-visible');
  var label=byId('to')?String(byId('to').value).split(',')[0].trim():'';
  byId('id4-hud-destination').textContent=label.slice(0,35)||'Seçili hedef';
  byId('id4-hud-summary').textContent=
    String(r.distanceKm||'—')+' km  ·  '+String(r.durationMin||'—')+' dk';
  byId('id4-hud-distance').textContent=String(r.distanceKm||'—')+' km';
  byId('id4-hud-time').textContent=String(r.durationMin||'—')+' dk';
  byId('id4-hud-next').textContent=
    Array.isArray(r.steps)&&r.steps.length?String(r.steps[0]).slice(0,85):
      'Yol tarifi önizlemesi';
  byId('id4-hud-station-count').textContent=
    r.stopIncluded?'1 planlı mola':'İstasyon verisi bekleniyor';
  if(Number.isFinite(r.energyLowKwh)&&Number.isFinite(r.energyHighKwh)){
   byId('id4-hud-energy').textContent=
    r.energyLowKwh.toFixed(1)+'–'+r.energyHighKwh.toFixed(1)+' kWh*';
  }else byId('id4-hud-energy').textContent='— kWh';
  updateWeather();
 });
 wrap('molaWeather',updateWeather);
 wrap('molaWeatherError',updateWeather);
 wrap('molaStations',function(d){
  if(!document.body.classList.contains('id4-route-visible'))return;
  if(Array.isArray(d.items)){
   byId('id4-hud-station-count').textContent=
     d.items.length+' EPDK kayıt'+(d.items.length===1?'':'ları');
  }
 });
 wrap('molaStationError',function(){
  byId('id4-hud-station-count').textContent='Veri alınamadı';
 });
 ['from','to'].forEach(function(id){
  var input=byId(id);if(!input)return;
  input.addEventListener('input',function(){document.body.classList.remove('id4-route-visible')});
 });
 var originalEdit=byId('route-edit');
 if(originalEdit)originalEdit.addEventListener('click',function(){
  document.body.classList.remove('id4-route-visible');
 });
 window.setInterval(function(){
  if(lastFreshAt && Date.now()-lastFreshAt>25000){
   lastFreshAt=0;clearObd('OBD verisi güncel değil');
  }
 },5000);
})();
