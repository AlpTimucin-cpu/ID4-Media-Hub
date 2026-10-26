// In-place Mola 3.2 media shortcuts must not replace route/OBD or media engines.
'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,
 '../overlay/fermata/src/main/assets/id4/id4-mola-media.js'),'utf8');
const css=fs.readFileSync(path.join(__dirname,
 '../overlay/fermata/src/main/assets/id4/id4-mola-media.css'),'utf8');
const html=fs.readFileSync(path.join(__dirname,
 '../overlay/fermata/src/main/assets/id4/navigator.html'),'utf8');
assert.match(html,/src="id4-mola-media.js"/);
assert.match(html,/href="id4-mola-media.css"/);
assert.match(css,/id4-mola-drawer\[hidden\]/);
const entries={};
function el(tag='div'){
 return {tagName:tag,id:'',className:'',children:[],hidden:false,onclick:null,
  attrs:{},addEventListener(type,fn){this['on'+type]=fn},
  setAttribute(k,v){this.attrs[k]=v},
  appendChild(c){this.children.push(c);if(c.id)entries[c.id]=c},
  textContent:'',type:''};
}
const body=el('body');
const doc={readyState:'complete',body,createElement:el,getElementById(id){
 return entries[id] || null;
},addEventListener(){}};
entries['dock-apps']=el('button');entries['dock-music']=el('button');
const calls=[];
const root={MolaNative:{openMolaCard(id){calls.push(id)},openOriginalMedia(){calls.push('external-original')}},console:{warn(){}}};
vm.runInNewContext(source,{window:root,document:doc}, {filename:'id4-mola-media.js'});
assert(root.ID4MolaMediaHub,'media hub API missing');
assert.equal(root.ID4MolaMediaHub.isOpen(),false);
assert.equal(root.ID4MolaMediaHub.items().length,15);
assert(entries['id4-mola-external-original'],
 'external original 3.2 optional launcher must be visible');
assert.equal(root.ID4MolaMediaHub.openOriginalMedia instanceof Function,true);

for(const id of ['netflix','disney','prime','equalizer','radio','tv','mirror',
 'web','windasist','shortcuts','settings']){
 assert(root.ID4MolaMediaHub.items().includes(id),'missing module '+id);
}
entries['dock-apps'].onclick();
assert.equal(root.ID4MolaMediaHub.isOpen(),true,'dock must open in-place panel');
entries['id4-mola-external-original'].onclick();
assert.equal(calls.at(-1),'external-original');
assert.equal(root.ID4MolaMediaHub.isOpen(),false);
entries['dock-apps'].onclick();
assert.equal(root.ID4MolaMediaHub.isOpen(),true,'dock reopens after external launch');

const layer=entries['id4-mola-drawer'];
assert.equal(layer.hidden,false);
assert.equal(layer.attrs['aria-hidden'],'false');
assert.equal(body.children.length,1,'navigation map must not be recreated');
entries['id4-mola-netflix'].onclick();
assert.equal(calls.at(-1),'netflix','official app must have native handoff');
assert.equal(root.ID4MolaMediaHub.isOpen(),false);
entries['dock-music'].onclick();
entries['id4-mola-equalizer'].onclick();
assert.equal(calls.at(-1),'equalizer','Mola original equalizer must be used');
assert.equal(root.ID4MolaMediaHub.isOpen(),false);
root.ID4MolaMediaHub.open();
entries['id4-mola-close'].onclick();
assert.equal(layer.hidden,true);
delete root.MolaNative;
assert.equal(root.ID4MolaMediaHub.open(),true);
assert.doesNotThrow(()=>entries['id4-mola-prime'].onclick());
assert.equal(root.ID4MolaMediaHub.isOpen(),false);
assert(!source.includes('MediaSession.setPlaybackSpeed') &&
 !source.includes('webview.loadUrl("https://www.netflix'),
 'DRM playback and original audio engines must not be spoofed');
console.log('PASS: Mola 3.2 in-place catalogue, 15 cards, official DRM handoff and equalizer');
