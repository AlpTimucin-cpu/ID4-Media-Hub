/*
 * Mola'nın mevcut navigator.html dosyasına EK olarak yüklenir.
 * Bu dosya Mola haritasını, rota alternatiflerini ve mevcut callback'leri bozmaz.
 * Önkoşul: navigator.html'de map=L.map(...) satırından hemen sonra
 *           window.id4LiveLeafletMap = map;
 * eklenmiş olmalı ve ID4_LIVE_ATTACH_ONLY = true ayarlanmalı.
 */
(function (w, d) {
  'use strict';
  var mod = w.ID4LiveMap;
  if (!mod || !w.id4LiveLeafletMap || !mod.attachExistingMap(w.id4LiveLeafletMap)) {
    console.warn('ID4LiveMap: mevcut Mola haritasına erişilemiyor'); return;
  }

  // Üretimdeki arayüzün sağ tarafına küçük bir canlı bilgi kartı.
  var target = d.querySelector('.cockpit-right');
  if (target && !d.getElementById('id4-live-addon')) {
    var panel = d.createElement('div');
    panel.id = 'id4-live-addon'; panel.className = 'cockpit-panel';
    // Sabit metin: dış kaynaklı veri innerHTML ile yazılmıyor.
    panel.innerHTML = '<div class="id4-addon-head">CANLI YOL VERİSİ</div>' +
      '<div class="id4-addon-row">GPS hız: <b id="id4-speed">—</b> km/sa · Batarya: <b id="id4-soc">—</b></div>' +
      '<div id="id4-soc-track"><div id="id4-soc-bar"></div></div>' +
      '<div class="id4-addon-row">Hava: <span id="id4-weather">—</span></div>' +
      '<div class="id4-addon-row">Enerji: <span id="id4-range">—</span></div>' +
      '<div class="id4-addon-row">İstasyon: <span id="id4-station-count">—</span> · İhbar: <span id="id4-incident-count">—</span></div>' +
      '<div class="id4-addon-row" id="id4-nearest-dc">—</div>' +
      '<div id="id4-status">Yerel ihbar katmanı hazır.</div>';
    target.appendChild(panel);
  }
  var speed = null, soc = null, speedAt = 0, socAt = 0;
  function syncCar() {
    var now = Date.now();
    mod.updateCarData(now - speedAt <= 30000 ? speed : null,
                      now - socAt <= 30000 ? soc : null);
  }
  function wrap(name, after) {
    var original = w[name];
    if (typeof original !== 'function') return;
    w[name] = function (packet) {
      var result = original.apply(this, arguments); // Mevcut Mola çalışmaya devam eder.
      try { after(packet); } catch (e) { console.warn('ID4Live addon ' + name, e); }
      return result;
    };
  }

  wrap('molaRoute', function (p) {
    if (!p || !Array.isArray(p.coordinates)) return;
    // Mola native rota çizimi aynen kalır; çift çizgi/çift fitBounds oluşmaz.
    mod.ingestNativeRoute({ coordinates: p.coordinates,
      distanceKm: p.distanceKm, durationMin: p.durationMin,
      stopIncluded: p.stopIncluded === true });
  });
  wrap('molaPosition', function (p) {
    // Yerini bilinmeyen veya eski GPS'i kullanma.
    if (!p || !Number.isFinite(p.lat) || !Number.isFinite(p.lon) ||
        !Number.isFinite(p.timeMs) || !Number.isFinite(p.accuracy) ||
        p.accuracy > 60 || Date.now() - p.timeMs < 0 || Date.now() - p.timeMs > 30000) return;
    mod.updateLocation(p.lat, p.lon);
    if (Number.isFinite(p.speedKmh) && p.speedKmh >= 0 && p.speedKmh <= 300) {
      speed = p.speedKmh; speedAt = Date.now();
    }
    syncCar();
  });
  wrap('molaTelemetry', function (p) {
    if (p && p.fresh === true && p.soc != null) {
      var value = Number.parseFloat(String(p.soc).replace(',', '.'));
      if (Number.isFinite(value) && value >= 0 && value <= 100) {
        soc = value; socAt = Date.now();
      } else { soc = null; socAt = 0; }
    } else { soc = null; socAt = 0; }
    syncCar();
  });
  wrap('molaStatus', function (p) {
    if (p && /durduruldu|iptal edildi/i.test(String(p.detail || ''))) mod.clearRoute();





  });
  // En yakın DC için istasyonların TAM veri kümesi bağımsız verilmelidir:
  // window.ID4LiveMap.setStations(allEpdkStations)
  // window.molaStations paketleri çoğu zaman sadece görünür ekran/rota alt kümesidir;
  // bunları bütün Türkiye sanarak en yakın istasyon iddiası yapılmaz.
})(window, document);
