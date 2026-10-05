const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname,'../../kernelsu-module/webroot/app.js'),'utf8');
// DOM and bridge fixtures validate the real WebUI contract; they do not assert a device backend works.
function sandbox(ksu) {
  const elements = new Map();const timers = new Map();let counter=0;
  const context = { window: { ksu }, document: { getElementById(id) {
    if (!elements.has(id)) elements.set(id,{textContent:'',className:'',disabled:false,dataset:{},value:'',children:[],addEventListener(){},replaceChildren(){this.children=[];this.value=''},appendChild(child){this.children.push(child);if(!this.value)this.value=child.value}});
    return elements.get(id);
  },createElement(){return {value:'',textContent:''}} }, setTimeout(fn){const id=++counter;timers.set(id,fn);return id},clearTimeout(id){timers.delete(id)} };
  vm.createContext(context);vm.runInContext(source,context);
  return {context,elements,timers};
}
test('missing actual KernelSU bridge rejects and clears stale cards', async () => {
  const {context,elements}=sandbox();
  await assert.rejects(context.execRoot('id'),/unavailable/);
  await context.refresh();
  assert.match(elements.get('uinput').textContent,/Unavailable/);
  assert.match(elements.get('runtimeStatus').textContent,/Unavailable/);
});
test('exec uses the three-argument KernelSU contract and the actual exit code', async () => {
  let context;const calls=[];
  const fixture=sandbox({exec(command,options,callback){calls.push({command,options,callback});}});context=fixture.context;
  const request=context.execRoot('id');const call=calls.at(-1);
  assert.equal(call.command,'id');assert.equal(call.options,'{}');assert.equal(typeof call.callback,'string');
  context.window[call.callback]('7','output','permission denied');
  const result=await request;assert.equal(result.errno,7);assert.equal(result.stderr,'permission denied');
  assert.equal(context.window[call.callback],undefined);
});
test('malformed bridge status cannot become success', async () => {
  const calls=[];const {context}=sandbox({exec(...args){calls.push(args)}});
  const request=context.execRoot('id');context.window[calls.at(-1)[2]]('invalid','','');
  await assert.rejects(request,/invalid exit status/);
});
test('lost bridge callback fails explicitly on timeout', async () => {
  const {context,timers}=sandbox({exec(){}});
  const request=context.execRoot('id');const timer=[...timers.values()].at(-1);timer();
  await assert.rejects(request,/timed out/);
});
test('AVAILABLE text with failure status never enables updater installation', async () => {
  const calls=[];const {context,elements}=sandbox({exec(...args){calls.push(args)}});
  const check=context.checkGithubUpdate();context.window[calls.at(-1)[2]](1,'STATE=AVAILABLE\nREMOTE_VERSION=2.0.0','failed');
  await check;assert.equal(elements.get('installUpdate').disabled,true);
});

test('incomplete capability output cannot enable root tuning controls', async () => {
  const calls=[];const {context,elements}=sandbox({exec(...args){calls.push(args)}});
  const request=context.refreshCapabilities();context.window[calls.at(-1)[2]](0,'CPU_BEGIN\nPOLICY=0\nCPU_END','');
  await request;assert.equal(elements.get('applyCpuGovernor').disabled,true);
  assert.match(elements.get('controlStatus').textContent,/Missing capability section/);
});
test('root controls are populated only from observed policy governors and frequencies', async () => {
  const calls=[];const {context,elements}=sandbox({exec(...args){calls.push(args)}});
  const request=context.refreshCapabilities();
  context.window[calls.at(-1)[2]](0,'CPU_BEGIN\nPOLICY=0\nscaling_available_governors=schedutil powersave\nscaling_governor=schedutil\nscaling_available_frequencies=300000 1000000\nscaling_min_freq=300000\nscaling_max_freq=1000000\nCPU_END\nDEVFREQ_BEGIN\nDEVFREQ_END\nTHERMAL_BEGIN\nread-only\nTHERMAL_END\nMEMORY_BEGIN\nswappiness=60\nMEMORY_END\nBATTERY_BEGIN\nlevel: 80\nBATTERY_END\nPRESETS_BEGIN\nNo verified presets\nPRESETS_END','');
  await request;assert.equal(elements.get('cpuPolicy').value,'0');
  assert.deepEqual(elements.get('cpuGovernor').children.map(value=>value.value),['schedutil','powersave']);
  assert.equal(elements.get('applyCpuGovernor').disabled,false);assert.equal(elements.get('applyDevfreqGovernor').disabled,true);
  assert.equal(elements.get('swappiness').value,'60');
});

test('empty boolean and null bridge exit values cannot become success',async()=>{
  for(const status of ['',null,false]) {
    const calls=[];const {context}=sandbox({exec(...args){calls.push(args)}});
    const request=context.execRoot('id');context.window[calls.at(-1)[2]](status,'','');
    await assert.rejects(request,/invalid exit status/);
  }
});
test('failed capability fields never manufacture selectable governor names',()=>{
  const {context}=sandbox({exec(){}});
  assert.deepEqual([...context.exposedWords('UNAVAILABLE: file is not readable',/^[a-zA-Z0-9_-]+$/)],[]);
  assert.deepEqual([...context.exposedWords('schedutil powersave',/^[a-zA-Z0-9_-]+$/)],['schedutil','powersave']);
});
