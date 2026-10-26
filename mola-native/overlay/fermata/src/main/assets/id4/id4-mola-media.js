/* ID.4 in-place media catalogue.
 * Native Mola owns playback/effects. We do not embed DRM players,
 * replace Android Auto media services, or spoof parked state. */
(function (root, doc) {
 'use strict';
 var cards = [["tv","📺 TV","Canlı TV ve IPTV","tv"],["youtube","▶ YouTube","ID.4 YouTube oynatıcısı","youtube"],["mirror","▣ Ekran Yansıtma","ID.4 ekran yansıtma","mirror"],["netflix","NETFLIX","Resmî uygulama","netflix"],["disney","Disney+","Resmî uygulama","disney"],["prime","prime video","Resmî uygulama","prime"],["media","▣ Medya","Yerel video ve müzik","media"],["radio","◉ Radyo","İnternet radyoları","radio"],["podcast","♫ Podcastlar","Podcast bölümleri","podcast"],["web","◎ Web","Tarayıcı ve medya","web"],["windasist","WindAsist","ID.4 kısayolu","windasist"],["mola","ID.4 Yol Arkadaşım","ID.4 ana ekranı","mola"],["shortcuts","＋ Kısayol Ekle","Kısayolları düzenle","shortcuts"],["settings","⚙ Ayarlar","ID.4 uygulama ayarları","settings"],["equalizer","▥ Ekolayzır","ID.4 ses efektleri","equalizer"]];
 var overlay = null, opened = false;
 function node(tag, cls, txt) {
  var el = doc.createElement(tag);
  if (cls) el.className = cls;
  if (txt) el.textContent = txt;
  return el;
 }
 function setOpen(flag) {
  if (!overlay) return false;
  opened = !!flag;
  overlay.hidden = !opened;
  overlay.setAttribute('aria-hidden', opened ? 'false' : 'true');
  return true;
 }
 function launch(id) {
  setOpen(false);
  try {
   var bridge = root.MolaNative;
   if (bridge && typeof bridge.openMolaCard === 'function') {
    bridge.openMolaCard(id);
    return true;
   }
   // Legacy preview may only have the original Mola dashboard.
   if (bridge && typeof bridge.openMedia === 'function' &&
       id !== 'netflix' && id !== 'disney' && id !== 'prime') {
    bridge.openMedia();
    return true;
   }
  } catch (e) {
   try { root.console.warn('ID4 media handoff', e); } catch (_) {}
  }
  if (typeof root.molaStatus === 'function')
   root.molaStatus({status:'media',
    detail:'Bu kısayol için yeni ID.4 Android uygulama sürümü gerekli.'});
  return false;
 }
 function install() {
  if (overlay || !doc.body) return !!overlay;
  overlay = node('section', 'id4-mola-drawer');
  overlay.id = 'id4-mola-drawer';
  overlay.hidden = true;
  overlay.setAttribute('role', 'dialog');
  overlay.setAttribute('aria-label', 'ID.4 Yol Arkadaşım medya uygulamaları');
  overlay.setAttribute('aria-modal', 'true');
  overlay.setAttribute('aria-hidden', 'true');
  var panel = node('div', 'id4-mola-panel');
  var head = node('div', 'id4-mola-head');
  var title = node('div', 'id4-mola-title');
  title.appendChild(node('strong', '', 'ID.4 Yol Arkadaşım · Medya'));
  title.appendChild(node('small', '', 'ID.4 Yol Arkadaşım · mevcut oynatıcılar korunur'));
  var close = node('button', 'id4-mola-close', '✕ Kapat');
  close.id = 'id4-mola-close';
  close.type = 'button';close.onclick = function(){setOpen(false)};
  head.appendChild(title);head.appendChild(close);
  var grid = node('div', 'id4-mola-grid');
  cards.forEach(function (c) {
   var button = node('button', 'id4-mola-card id4-mola-' + c[3]);
   button.type = 'button'; button.id = 'id4-mola-' + c[0];
   button.appendChild(node('strong', '', c[1]));
   button.appendChild(node('small', '', c[2]));
   button.onclick = function () { launch(c[0]) };
   grid.appendChild(button);
  });
  panel.appendChild(head); panel.appendChild(grid);
  panel.appendChild(node('p', 'id4-mola-disclaimer',
   'Netflix, Disney+ ve Prime Video resmî uygulamada veya tarayıcıda açılır. ' +
   'TV, YouTube, radyo, Ekolayzır ve diğer özellikler ID.4 medya bölümüne geçer. ' +
   'DRM ve araç hareketi kısıtları değiştirilmez.'));
  overlay.appendChild(panel);
  overlay.addEventListener('click',function(e) {
   if (e && e.target === overlay) setOpen(false);
  });
  doc.body.appendChild(overlay);
  ['dock-apps','dock-music'].forEach(function(id) {
   var b = doc.getElementById(id);
   if (b) b.onclick = function(){setOpen(true)};
  });
  doc.addEventListener('keydown',function(e){
   if(opened && e && (e.key==='Escape'||e.key==='Esc'))setOpen(false);
  });
  return true;
 }
 root.ID4MolaMediaHub = {start:install,open:function(){return install()&&setOpen(true)},
  close:function(){return setOpen(false)},
  isOpen:function(){return opened},
  items:function(){return cards.map(function(c){return c[0]})}};
 if (doc.readyState === 'loading') doc.addEventListener('DOMContentLoaded',install);
 else install();
})(window, document);
