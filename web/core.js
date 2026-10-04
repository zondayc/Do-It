(function (root) {
  'use strict';
  const DAY = 86400000;
  const MAX_TASKS = 200;
  const Icons=root.DoItIcons || (typeof require==='function'?require('./icons.js'):null);
  const newId=()=>globalThis.crypto?.randomUUID?.()||'t'+Date.now().toString(36)+Math.random().toString(36).slice(2);
  const iso = d => `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
  const validDate = value => typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) && iso(new Date(`${value}T12:00:00`)) === value;
  const ordinal = value => Date.parse(value+'T00:00:00Z') / DAY;
  const addDays = (value,n) => new Date((ordinal(value)+n)*DAY).toISOString().slice(0,10);
  const weekday = value => new Date(value+'T12:00:00').getDay() || 7;
  const today = (state, now = new Date()) => { const d = new Date(now); if (d.getHours() < state.settings.dayStart) d.setDate(d.getDate()-1); return iso(d); };
  const revision = (task, date) => { for(let i=task.revisions.length-1;i>=0;i--) if(task.revisions[i].from<=date) return task.revisions[i]; return null; };
  function due(task,date) {
    if(date<task.createdOn || task.archivedOn && date>=task.archivedOn) return false;
    const r=revision(task,date); if(!r || !r.enabled || r.undated) return false;
    const s=r.repeat;
    if(s.type==='daily') return true;
    if(s.type==='weekdays') return s.weekdays.includes(weekday(date));
    if(s.type==='once') return date===s.anchor;
    return date>=s.anchor && (ordinal(date)-ordinal(s.anchor))%s.interval===0;
  }
  const done = (state,id,date) => !!state.completions[id]?.[date];
  const daySummary = (state,date) => {const tasks=state.tasks.filter(t=>t.kind!=='note'&&due(t,date)); const count=tasks.reduce((n,t)=>n+Number(done(state,t.id,date)),0);return {tasks,total:tasks.length,done:count,complete:tasks.length>0 && count===tasks.length};};
  let dateCache={start:'',end:'',dates:[]};
  function dateRange(start,end){if(end!==dateCache.end||!dateCache.start||start<dateCache.start){const dates=[];for(let d=start;d<=end;d=addDays(d,1))dates.push(d);dateCache={start,end,dates};}return dateCache.dates.slice(ordinal(start)-ordinal(dateCache.start));}
  function taskStreak(state,task,date) {
    let longest=0, run=0, total=0;
    for(const d of dateRange(task.createdOn,date)) { if(!due(task,d)) continue; if(done(state,task.id,d)){total++;run++;longest=Math.max(longest,run);} else if(d<date) run=0; }
    return {current:run,longest,total};
  }
  function allStreak(state,date) {
    let longest=0,run=0,total=0;
    const start=state.tasks.filter(t=>t.kind!=='note').reduce((v,t)=>t.createdOn<v?t.createdOn:v,date);
    for(const d of dateRange(start,date)){const s=daySummary(state,d);if(!s.total)continue;if(s.complete){total++;run++;longest=Math.max(longest,run);}else if(d<date)run=0;}
    return {current:run,longest,total};
  }
  const empty = () => ({version:1,settings:{dayStart:0,sound:true,iconOrder:Object.keys(Icons.items)},tasks:[],completions:{},templates:[]});
  function validValues(r){const s=r?.repeat;
    return r&&typeof r.title==='string'&&!!r.title.trim()&&r.title.length<=80&&typeof r.notes==='string'&&r.notes.length<=500&&Object.hasOwn(Icons.items,r.icon)&&typeof r.enabled==='boolean'&&typeof r.alarm==='boolean'&&(r.time===null||typeof r.time==='string'&&/^([01]\d|2[0-3]):[0-5]\d$/.test(r.time))&&(!r.alarm||!!r.time)&&(!r.undated||!r.time&&!r.alarm)&& (r.undated===undefined||typeof r.undated==='boolean')&&s&&['daily','weekdays','interval','once'].includes(s.type)&&validDate(s.anchor)&&Number.isInteger(s.interval)&&s.interval>=1&&s.interval<=365&&Array.isArray(s.weekdays)&&s.weekdays.every(x=>Number.isInteger(x)&&x>=1&&x<=7)&&(s.type!=='weekdays'||s.weekdays.length>0);
  }
  const validId=id=>typeof id==='string'&&/^[a-zA-Z0-9_-]{1,80}$/.test(id)&&!['__proto__','constructor','prototype'].includes(id);
  function validate(input) {
    if(!input || input.version!==1 || !input.settings || ![0,1,2,3,4,5,6].includes(input.settings.dayStart) || typeof input.settings.sound!=='boolean' || !Array.isArray(input.tasks) || input.tasks.length>MAX_TASKS || !input.completions || typeof input.completions!=='object' || Array.isArray(input.completions)) throw Error('备份格式不正确，或版本不受支持');
    const iconOrder=input.settings.iconOrder??Object.keys(Icons.items);
    if(!Array.isArray(iconOrder)||iconOrder.length!==Object.keys(Icons.items).length||new Set(iconOrder).size!==iconOrder.length||iconOrder.some(id=>!Object.hasOwn(Icons.items,id)))throw Error('图标顺序不正确');
    const ids=new Set();
    for(const t of input.tasks){
      if(!t || !validId(t.id) || ids.has(t.id) || !validDate(t.createdOn) || t.createdOn<'2000-01-01' || (t.archivedOn!==null && (!validDate(t.archivedOn)||t.archivedOn<t.createdOn)) || !Array.isArray(t.revisions) || !t.revisions.length || t.revisions.length>10000 || t.kind!==undefined&&!['task','note'].includes(t.kind)) throw Error('任务数据不正确');
      ids.add(t.id); let prev='';
      for(const r of t.revisions){
        if(!validDate(r.from)||r.from<t.createdOn||r.from<=prev||!validValues(r)||t.kind==='note'&&r.repeat.type!=='once'||t.kind!=='note'&&r.undated)throw Error('任务设置不正确');
        prev=r.from;
      }
      if(t.revisions[0].from!==t.createdOn)throw Error('缺少初始任务设置');
    }
    for(const [id,records] of Object.entries(input.completions)){if(!ids.has(id)||!records||typeof records!=='object'||Array.isArray(records))throw Error('打卡记录不正确');for(const [d,r] of Object.entries(records)){if(!validDate(d)||d<'2000-01-01'||!r||typeof r.at!=='string'||!Number.isFinite(Date.parse(r.at))||typeof r.manual!=='boolean')throw Error('打卡记录不正确');}}
    const templates=input.templates??[];
    if(!Array.isArray(templates)||templates.length>100)throw Error('模板数据不正确');
    const templateIds=new Set();for(const t of templates){if(!t||!validId(t.id)||templateIds.has(t.id)||typeof t.name!=='string'||!t.name.trim()||t.name.length>80||!validValues(t.values)||t.values.undated||!Number.isInteger(t.offsetDays)||t.offsetDays<0||t.offsetDays>36500)throw Error('模板数据不正确');templateIds.add(t.id);}
    return JSON.parse(JSON.stringify({...input,settings:{...input.settings,iconOrder},templates}));
  }
  function upsert(state,values,date,id=null){const next=structuredClone(state);let t=next.tasks.find(t=>t.id===id);const {kind,...fields}=values;const r={...fields,from:date};if(t){t.revisions=t.revisions.filter(r=>r.from<date);t.revisions.push(r);}else{if(next.tasks.length>=MAX_TASKS)throw Error('最多支持 200 个任务和便签（含已删除）');t={id:newId(),kind:kind||'task',createdOn:date,archivedOn:null,revisions:[r]};next.tasks.push(t);}return validate(next);}
  const noteDone=(state,id)=>Object.keys(state.completions[id]||{}).length>0;
  function toggleNote(state,id,now=new Date()){const task=state.tasks.find(t=>t.id===id&&t.kind==='note'&&!t.archivedOn);if(!task)throw Error('便签不存在');const records=noteDone(state,id)?{}:{[today(state,now)]:{at:now.toISOString(),manual:false}};return {...state,completions:{...state.completions,[id]:records}};}
  function noteStats(state,date=today(state)){const notes=state.tasks.filter(t=>t.kind==='note'&&!t.archivedOn);const completed=notes.filter(t=>noteDone(state,t.id));return {total:notes.length,done:completed.length,pending:notes.length-completed.length,overdue:notes.filter(t=>{const r=t.revisions.at(-1);return !noteDone(state,t.id)&&!r.undated&&r.repeat.anchor<date;}).length};}
  function saveTemplate(state,name,values,date,id=null){const next=structuredClone(state);const {from,kind,undated,...fields}=values;const item={id:id||newId(),name:name.trim(),values:structuredClone(fields),offsetDays:fields.repeat.type==='once'?Math.max(0,ordinal(fields.repeat.anchor)-ordinal(date)):0};const at=next.templates.findIndex(t=>t.id===id);if(at>=0)next.templates[at]=item;else next.templates.push(item);return validate(next);}
  function templateValues(template,date){const values=structuredClone(template.values);values.repeat.anchor=values.repeat.type==='once'?addDays(date,template.offsetDays):date;return values;}
  function toggle(state,id,date,now=new Date()){const task=state.tasks.find(t=>t.id===id);if(!task||!due(task,date))throw Error('这一天没有安排该任务');const records={...state.completions[id]};if(records[date])delete records[date];else records[date]={at:now.toISOString(),manual:date!==today(state,now)};return {...state,completions:{...state.completions,[id]:records}};}
  function alarmTime(date,time,cutoff){const d=new Date(`${date}T${time}:00`);if(d.getHours()<cutoff)d.setDate(d.getDate()+1);return d.getTime();}
  function upcoming(state,now=new Date(),limit=60){const from=today(state,now),out=[],recurring=[];for(const t of state.tasks){const r=revision(t,from);if(!r?.enabled||!r.alarm||!r.time||t.archivedOn||t.kind==='note'&&noteDone(state,t.id))continue;if(r.repeat.type==='once'){const date=r.repeat.anchor,at=alarmTime(date,r.time,state.settings.dayStart);if(at>now.getTime()&&due(t,date)&&!done(state,t.id,date))out.push({taskId:t.id,date,title:r.title,at});}else recurring.push(t);}for(let i=0;i<3700&&recurring.length;i++){const date=addDays(from,i);for(const t of recurring){const r=revision(t,date);if(!due(t,date)||done(state,t.id,date))continue;const at=alarmTime(date,r.time,state.settings.dayStart);if(at>now.getTime())out.push({taskId:t.id,date,title:r.title,at});}if(out.length>=limit&&out.sort((a,b)=>a.at-b.at)[limit-1].at<=alarmTime(date,'23:59',state.settings.dayStart))break;}return out.sort((a,b)=>a.at-b.at).slice(0,limit);}
  function repeatLabel(r){const s=r.repeat;if(s.type==='daily')return '每天';if(s.type==='interval')return `每 ${s.interval} 天`;if(s.type==='once')return s.anchor.replaceAll('-',' / ');if(s.weekdays.length===5&&[1,2,3,4,5].every(x=>s.weekdays.includes(x)))return '工作日';if(s.weekdays.length===2&&[6,7].every(x=>s.weekdays.includes(x)))return '周末';return s.weekdays.map(x=>'一二三四五六日'[x-1]).join('、')+' / 周';}
  const api={iso,validDate,ordinal,addDays,weekday,today,revision,due,done,daySummary,taskStreak,allStreak,empty,validate,upsert,toggle,alarmTime,upcoming,repeatLabel,noteDone,toggleNote,noteStats,saveTemplate,templateValues,MAX_TASKS};
  root.DoItCore=api;if(typeof module!=='undefined')module.exports=api;
})(globalThis);
