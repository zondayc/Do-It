const {performance}=require('node:perf_hooks');
const C=require('../web/core.js');
function fixture(count=200,days=730){const s=C.empty(),from='2024-10-04';for(let n=0;n<count;n++){const id='task'+n;s.tasks.push({id,createdOn:from,archivedOn:null,revisions:[{from,title:'测试任务 '+n,notes:'',icon:'check',enabled:true,time:'08:00',alarm:true,repeat:{type:'daily',weekdays:[],interval:2,anchor:from}}]});s.completions[id]={};for(let i=0;i<days;i++){const date=C.addDays(from,i);if(i%17!==0)s.completions[id][date]={at:date+'T10:00:00Z',manual:false};}}return s;}
const state=fixture();const samples=[];for(let i=0;i<5;i++){const start=performance.now();C.allStreak(state,'2026-10-03');for(const t of state.tasks)C.taskStreak(state,t,'2026-10-03');samples.push(performance.now()-start);}
console.log(JSON.stringify({tasks:200,historyDays:730,records:Object.values(state.completions).reduce((n,r)=>n+Object.keys(r).length,0),fullStatisticsMilliseconds:samples,median:samples.sort((a,b)=>a-b)[2]},null,2));
module.exports={fixture};
