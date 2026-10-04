(function(root){
  'use strict';
  // One timer per active touch; no polling, pointer capture, or blocked scrolling.
  function createLongPress({onLongPress,onFeedback=()=>{},delay=550,slop=10,
    schedule=setTimeout,unschedule=clearTimeout,now=Date.now}={}){
    let active=null,suppressed=null;
    function cancel(){if(!active)return;unschedule(active.timer);onFeedback(active.target,false);active=null;}
    function start({pointerId,x,y,target}){
      cancel();const press={pointerId,x,y,target,timer:null};active=press;
      onFeedback(target,true);
      press.timer=schedule(()=>{
        if(active!==press)return;
        active=null;onFeedback(target,false);
        suppressed={target,until:now()+800};
        onLongPress(target);
      },delay);
    }
    function move({pointerId,x,y}){if(active&&active.pointerId===pointerId&&Math.hypot(x-active.x,y-active.y)>slop)cancel();}
    function end(pointerId){if(active&&active.pointerId===pointerId)cancel();}
    function suppressClick(target){if(!suppressed)return false;if(now()>suppressed.until){suppressed=null;return false;}return suppressed.target===target;}
    return {start,move,end,cancel,suppressClick};
  }
  function bindTaskLongPress(document,{isEnabled,onLongPress}){
    const cardFor=event=>event.target?.closest?.('.task-card[data-task-id]');
    const gesture=createLongPress({
      onFeedback:(card,pressed)=>card.classList.toggle('long-pressing',pressed),
      onLongPress:card=>{if(card.isConnected&&isEnabled())onLongPress(card.dataset.taskId);}
    });
    const listeners=[];
    function listen(name,fn,options){document.addEventListener(name,fn,options);listeners.push([name,fn,options]);}
    listen('pointerdown',event=>{
      const card=cardFor(event);
      if(!event.isPrimary||!['touch','pen'].includes(event.pointerType)||!card||!isEnabled()){gesture.cancel();return;}
      gesture.start({pointerId:event.pointerId,x:event.clientX,y:event.clientY,target:card});
    },{passive:true});
    listen('pointermove',event=>gesture.move({pointerId:event.pointerId,x:event.clientX,y:event.clientY}),{passive:true});
    listen('pointerup',event=>gesture.end(event.pointerId),{passive:true});
    listen('pointercancel',()=>gesture.cancel(),{passive:true});
    listen('scroll',()=>gesture.cancel(),{capture:true,passive:true});
    listen('visibilitychange',()=>gesture.cancel());
    listen('click',event=>{if(gesture.suppressClick(cardFor(event))){event.preventDefault();event.stopImmediatePropagation();}},true);
    listen('contextmenu',event=>{if(cardFor(event)&&isEnabled())event.preventDefault();});
    return ()=>{gesture.cancel();for(const args of listeners)document.removeEventListener(...args);};
  }
  const api={createLongPress,bindTaskLongPress};root.DoItGestures=api;
  if(typeof module!=='undefined')module.exports=api;
})(globalThis);
