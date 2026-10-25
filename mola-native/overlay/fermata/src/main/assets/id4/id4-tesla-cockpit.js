/* ID.4 premium display observer — zero changes to native message contracts.
 * Observes existing Mola callbacks AFTER id4-navigator-adapter.js initializes.
 * NEVER synthesizes vehicle SoC, speed, gear, weather or charging stations.
 * An invalid/stale packet withdraws the displayed value instead of freezing it.
 */
(function(root){
  'use strict';
  var S={soc:null,socAt:0,speed:null,gpsAt:0,gear:null,
         temp:null,wind:null,weatherAt:0};
  var TELEMETRY_TTL=30000,GPS_TTL=30000,WEATHER_TTL=5400000;
  function finite(v){return typeof v==='number'&&Number.isFinite(v);}
  function socNumber(v){
    if(finite(v))return v>=0&&v<=100?v:null;
    if(typeof v!=='string')return null;
    var m=/^\s*(\d{1,3}(?:[.,]\d+)?)\s*%?\s*$/.exec(v);
    if(!m)return null;
    var n=Number(m[1].replace(',','.'));return finite(n)&&n>=0&&n<=100?n:null;
  }
  function validAge(t,ttl,now){
    return finite(t)&&t>0&&now>=t&&now-t<ttl;
  }
  function setText(id,text){
    try { var el=root.document&&root.document.getElementById(id);
      if(el)el.textContent=text; } catch(_ignored){}
  }
  function render(){
    try {
      var now=Date.now();
      var soc=validAge(S.socAt,TELEMETRY_TTL,now)?S.soc:null;
      var speed=validAge(S.gpsAt,GPS_TTL,now)?S.speed:null;
      var weather=validAge(S.weatherAt,WEATHER_TTL,now);
      var low=finite(soc)&&soc<20;
      setText('id4-drive-speed',finite(speed)?String(Math.round(speed)):'—');
      setText('id4-drive-gear','Vites: '+(validAge(S.socAt,TELEMETRY_TTL,now)&&S.gear?S.gear:'—'));
      setText('id4-side-soc',finite(soc)?String(Math.round(soc))+' %':'— %');
      setText('id4-side-temp',weather?Math.round(S.temp)+'°C':'— °C');
      setText('id4-side-wind',weather?Math.round(S.wind)+' km/sa':'— km/sa');
      var bar=root.document&&root.document.getElementById('id4-side-bar-fill');
      if(bar){bar.style.width=finite(soc)?soc+'%':'0%';
        bar.setAttribute('role','progressbar');bar.setAttribute('aria-valuemin','0');
        bar.setAttribute('aria-valuemax','100');
        if(finite(soc))bar.setAttribute('aria-valuenow',String(soc));
        else bar.removeAttribute('aria-valuenow');
      }
      var impact=weather&&(S.temp<0||S.wind>30);
      var hint=root.document&&root.document.getElementById('id4-energy-impact');
      if(hint){hint.hidden=!(impact||low);
        hint.textContent=low?'⚠ Batarya <%20 · Şarj seçeneklerini kontrol et':
          impact?'⚡ Menzil Etkisi · hava koşulları (model)':'';}
      if(root.document&&root.document.body)root.document.body.classList.toggle('id4-low-battery',!!low);
      setText('id4-drive-provenance',
        'Hız: '+(finite(speed)?'GPS':'veri yok')+
        ' · SoC: '+(finite(soc)?'OBD':'veri yok')+
        ' · Hava: '+(weather?'Open-Meteo model':'tahmin yok'));
    } catch(err){try{root.console.warn('ID4 cockpit render',err)}catch(_ignored){}}
  }
  function onTelemetry(d){
    try {
      var fresh=!!d&&d.fresh===true,n=fresh?socNumber(d.soc):null;
      S.soc=n;S.socAt=n===null?0:Date.now();
      // chargeMode is NOT gear. Only accept separately verified gear state.
      S.gear=fresh&&d.gearVerified===true&&
        ['P','R','N','D','B'].indexOf(d.gear)!==-1?d.gear:null;
    } catch(_ignored){S.soc=null;S.socAt=0;S.gear=null}
    render();
  }
  function onPosition(x){
    try {
      var now=Date.now();
      var valid=!!x&&finite(x.lat)&&finite(x.lon)&&x.lat>=35&&x.lat<=43&&
        x.lon>=25&&x.lon<=46&&finite(x.timeMs)&&now>=x.timeMs&&
        now-x.timeMs<GPS_TTL&&finite(x.accuracy)&&x.accuracy<=35&&
        Number.isInteger(x.speedKmh)&&x.speedKmh>=0&&x.speedKmh<=252;
      S.speed=valid?x.speedKmh:null;S.gpsAt=valid?x.timeMs:0;
    } catch(_ignored){S.speed=null;S.gpsAt=0}
    render();
  }
  function onWeather(w){
    try {
      var now=Date.now();
      var valid=!!w&&w.modelBased===true&&finite(w.temperature)&&
        w.temperature>=-75&&w.temperature<=65&&finite(w.wind)&&
        w.wind>=0&&w.wind<=250&&finite(w.receivedAt)&&
        finite(w.modelTime)&&w.modelTime>0&&
        validAge(w.receivedAt,WEATHER_TTL,now)&&
        Math.abs(now-w.modelTime*1000)<=WEATHER_TTL;
      if(valid){S.temp=w.temperature;S.wind=w.wind;S.weatherAt=w.receivedAt;}
      else{S.temp=null;S.wind=null;S.weatherAt=0;}
    } catch(_ignored){S.temp=null;S.wind=null;S.weatherAt=0}
    render();
  }
  function onWeatherError(){
    // Existing Mola callback controls provider errors. Never keep a weather
    // label if its original timestamp has expired.
    render();
  }
  function observe(name,handler){
    var previous=root[name];
    if(typeof previous!=='function')return false;
    root[name]=function(packet){
      var output;
      try{output=previous.apply(this,arguments)}
      catch(err){try{root.console.error('Mola callback '+name,err)}catch(_ignored){}}
      try{handler(packet)}catch(err){
        try{root.console.warn('ID4 observer '+name,err)}catch(_ignored){}
      }
      return output;
    };
    return true;
  }
  function start(){
    if(!root.document||!root.document.getElementById('id4-tesla-side'))return false;
    observe('molaTelemetry',onTelemetry);
    observe('molaPosition',onPosition);
    observe('molaCurrentWeather',onWeather);
    observe('molaCurrentWeatherError',onWeatherError);
    render();
    root.setInterval(render,1000);
    // CSS modifies the width of the ORIGINAL Leaflet map; recalculate once.
    try{root.requestAnimationFrame(function(){
      var map=root.id4LiveLeafletMap;
      if(map&&typeof map.invalidateSize==='function')map.invalidateSize({animate:false});
    })}catch(_ignored){}
    return true;
  }
  // Export narrow pure verification hooks for offline regression tests.
  root.ID4TeslaCockpit={start:start,render:render,
    parseSoc:socNumber,validAge:validAge,
    state:function(){return {soc:S.soc,socAt:S.socAt,
      speed:S.speed,gpsAt:S.gpsAt,gear:S.gear,
      temperature:S.temp,wind:S.wind,weatherAt:S.weatherAt}}};
  start();
})(window);
