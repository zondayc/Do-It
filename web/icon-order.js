(function(root){
  'use strict';
  function move(order,id,target){const next=[...order],from=next.indexOf(id),to=next.indexOf(target);if(from<0||to<0||from===to)return next;next.splice(from,1);next.splice(to,0,id);return next;}
  root.DoItIconOrder={move};if(typeof module!=='undefined')module.exports=root.DoItIconOrder;
})(globalThis);
