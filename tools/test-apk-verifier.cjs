// Tests the real offline HTML script using Node WebCrypto, not an Android browser.
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const { webcrypto } = require('node:crypto');
const html = fs.readFileSync('docs/mission-003c/VERIFIER-APK.html', 'utf8');
const source = html.match(/<script>([\s\S]*?)<\/script>/)[1];
const digest = 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad';
async function run(expected, { noCrypto=false, size=3, fail=false }={}) {
  let click;
  const bytes = new TextEncoder().encode('abc');
  const elements = {file:{files:[{size,arrayBuffer:async()=>{if(fail)throw Error('read');return bytes.buffer;}}]},
    expected:{value:expected},result:{textContent:''},verify:{addEventListener:(_, fn)=>{click=fn;}}};
  vm.runInNewContext(source, {document:{getElementById:id=>elements[id]},crypto:noCrypto?{}:webcrypto,Uint8Array});
  await click(); return elements.result.textContent;
}
(async()=>{
  assert.match(await run(digest.toUpperCase()), /^CONFORME/);
  assert.match(await run('0'.repeat(64)), /^DIFFÉRENT/);
  assert.match(await run('invalid'), /référence valide/);
  assert.match(await run(digest,{noCrypto:true}), /indisponible/);
  assert.match(await run(digest,{size:64*1024*1024+1}), /Taille refusée/);
  assert.match(await run(digest,{fail:true}), /Lecture ou calcul impossible/);
  assert.match(html,/connect-src 'none'/);
  console.log('PASS: offline APK verifier: match, mismatch, invalid reference, unavailable crypto, size bound, read failure. Not an Android browser test.');
})().catch(e=>{console.error(e);process.exitCode=1;});
