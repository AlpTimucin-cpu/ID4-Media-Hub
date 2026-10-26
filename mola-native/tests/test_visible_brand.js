// Visible-only brand guard. Historic GPL attribution and Android internal
// identifiers must remain intact for license / upgrade compatibility.
'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../../');
const ui=fs.readFileSync(path.join(root,'mola-native/overlay/fermata/src/main/assets/id4/navigator.html'),'utf8');
const legacy=fs.readFileSync(path.join(root,'mola-native/overlay/fermata/src/main/assets/id4/index.html'),'utf8');
const cards=fs.readFileSync(path.join(root,'mola-native/overlay/fermata/src/main/assets/id4/id4-mola-media.js'),'utf8');
const readme=fs.readFileSync(path.join(root,'README.md'),'utf8');
const tr=fs.readFileSync(path.join(root,'README_TR.md'),'utf8');
const changes=fs.readFileSync(path.join(root,'CHANGELOG.md'),'utf8');
for(const [file,content] of [['README',readme],['README_TR',tr],['CHANGELOG',changes],['kokpit',ui],['harita',legacy],['kartlar',cards]]){
 assert(!/ID\.4 Yol Arkadaşım|Volkswagen ID\.4|VOLKSWAGEN|Mola 3\.2/i.test(content),
   file+': legacy public-facing brand remained');
}
assert(ui.includes('<strong>Elektrikli Yol Arkadaşım</strong>'));
assert(/<title>Elektrikli Yol Arkadaşım/.test(ui));
assert(/<title>Elektrikli Yol Arkadaşım/.test(legacy));
assert(cards.includes("'Elektrikli Yol Arkadaşım'")||
       cards.includes('"Elektrikli Yol Arkadaşım"'));
assert(!/"NETFLIX"|"Disney\+"|"prime video"/.test(cards));
console.log('PASS: public app brand consistent; license and technical identifiers untouched');
