/*
 * Mola'nın mevcut navigator.html dosyasına EK olarak yüklenir.
 * Bu dosya Mola haritasını, rota alternatiflerini ve mevcut callback'leri bozmaz.
 * Önkoşul: navigator.html'de map=L.map(...) satırından hemen sonra
 *           window.id4LiveLeafletMap = map;
 * eklenmiş olmalı ve ID4_LIVE_ATTACH_ONLY = true ayarlanmalı.
 */
(function (w, d) {
  'use strict';
  var mod = w && w.ID4LiveMap;
  try {
    if (!mod || !w.id4LiveLeafletMap ||
        !mod.attachExistingMap(w.id4LiveLeafletMap)) {
      console.warn('ID4LiveMap: mevcut Mola haritasına erişilemiyor');
      return;
    }
  } catch (error) {
    // A partially initialized map must not take down Mola's TV or navigator.
    console.warn('ID4LiveMap attach failed; leaving Mola untouched', error);
    return;
  }

  // Üretimdeki arayüzün sağ tarafına küçük bir canlı bilgi kartı.
  var target = null;
  try { target = d.querySelector('.cockpit-right'); }
  catch (e) { console.warn('ID4 cockpit selector unavailable', e); }
  try {
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
  } catch (e) { console.warn('ID4 HUD panel unavailable', e); }
  var speed = null, soc = null, speedAt = 0, socAt = 0;
  function syncCar() {
    var now = Date.now();
    // Reject a time jump backwards; never revive stale measurements.
    var freshSpeed = speedAt > 0 && now >= speedAt && now - speedAt <= 30000;
    var freshSoc = socAt > 0 && now >= socAt && now - socAt <= 30000;
    mod.updateCarData(freshSpeed ? speed : null, freshSoc ? soc : null);
  }
  function wrap(name, after) {
    var original = w[name];
    if (typeof original !== 'function') return;
    w[name] = function (packet) {
      var result;
      try {
        // Native Mola callback owns the UI, routes and media modules.
        result = original.apply(this, arguments);
      } catch (e) {
        console.warn('Mola native callback failed: ' + name, e);
        try {
          var label = d.getElementById('id4-status');
          if (label) label.textContent = 'Mola verisi işlenemedi: ' + name;
        } catch (_) { /* DOM may be unavailable. */ }
      }
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
  // A native route packet is only a subset of EPDK; never pass it to setStations().
  wrap('molaStations', function (p) {
    if (p && p.kind === 'route') mod.setRouteStations(p);
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
    if (p && /durduruldu|iptal edildi/i.test(String(p.detail || '')))
      mod.clearRoute();
  });

  // Native can call this from evaluateJavascript using a safely quoted JSON
  // string. Do not overwrite the actual Java @JavascriptInterface
  // window.AndroidBridge. This is an optional JS endpoint.
  function normalizePacket(raw) {
    if (typeof raw === 'string') {
      if (raw.length > 2 * 1024 * 1024) return null;
      try { raw = JSON.parse(raw); } catch (_) { return null; }
    }
    return raw && typeof raw === 'object' && !Array.isArray(raw) ?
      raw : null;
  }
  var commands = Object.freeze({
    route:'molaRoute', stations:'molaStations',
    gps:'molaPosition', telemetry:'molaTelemetry', status:'molaStatus'
  });
  w.ID4NativeBridge = Object.freeze({
    receive: function (type, raw) {
      try {
        if (typeof type !== 'string' ||
            !Object.prototype.hasOwnProperty.call(commands, type)) return false;
        var packet = normalizePacket(raw);
        if (!packet) return false;
        // Legacy caller spelling is intentionally preserved for the cockpit
        // contract. It does NOT enable or bypass Android Auto's parked-only
        // video restrictions. It only opens this APK's original media UI.
        if (type === 'status' &&
            packet.command === 'LAUNCH_MOLA_32_NO_PARK') {
          if (Object.keys(packet).length !== 1) return false;
          if (!w.MolaNative || typeof w.MolaNative.openMedia !== 'function')
            return false;
          w.MolaNative.openMedia(); // Android @JavascriptInterface, allowlisted.
          return true;
        }
        // Reject command-like packets that are not an explicit allowlisted
        // media handoff: arbitrary Java bridge method calls are forbidden.
        if (Object.prototype.hasOwnProperty.call(packet, 'command'))
          return false;
        var callback = w[commands[type]];
        if (typeof callback !== 'function') return false;
        callback(packet); // Original Mola first, addon second.
        return true;
      } catch (error) {
        console.warn('ID4 native packet refused:', error); return false;
      }
    },
    snapshot: function () {
      try { return mod.getState(); }
      catch (_) { return {soc:null,position:null,countryStationsVerified:false}; }
    },
    epdkCountry: function (raw) {
      function reject() {
        // Fail closed: do NOT keep highlighting a previous national DC set
        // after the latest full-country transfer is malformed/rejected.
        try { mod.setStations(null, null); } catch (_) { /* Already safe. */ }
        return false;
      }
      try {
        // Must be a verified COUNTRY batch; a Mola route subset is NEVER
        // promoted into the national DC index. No assumed status or kW.
        var packet = normalizePacket(raw);
        if (!packet || !Array.isArray(packet.stations) || !packet.metadata ||
            packet.metadata.coverage !== 'country') return reject();
        var verdict = mod.validateEPDKDataContract(packet);
        if (!verdict || !verdict.ok) return reject();
        if (mod.setStations(packet.stations, packet.metadata) <= 0)
          return reject();
        return true;
      } catch (error) {
        console.warn('ID4 EPDK batch refused:', error);
        return reject();
      }
    }
  });
})(window, document);
