(function(root){
  const items={
    check:['通用','<path d="m5 12 4 4L19 6"/>'],
    sun:['早起','<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/>'],
    moon:['睡眠','<path d="M20 14A9 9 0 0 1 10 4a9 9 0 1 0 10 10Z"/>'],
    book:['阅读','<path d="M12 5v15M3 4c4-1 6 0 9 1 3-1 5-2 9-1v15c-4-1-6 0-9 1-3-1-5-2-9-1Z"/>'],
    study:['学习','<path d="m2 9 10-5 10 5-10 5ZM6 11v6c4 3 8 3 12 0v-6M22 9v8"/>'],
    pen:['写作','<path d="m4 16 12-12 4 4L8 20H4Zm9-9 4 4M4 16l4 4"/>'],
    code:['编程','<path d="m8 6-6 6 6 6m8-12 6 6-6 6M14 4l-4 16"/>'],
    work:['工作','<rect x="3" y="7" width="18" height="14" rx="2"/><path d="M8 7V3h8v4M3 12c6 3 12 3 18 0M10 13v3h4v-3"/>'],
    move:['运动','<circle cx="14" cy="4" r="2"/><path d="m5 19 4-6 4 3-1 5M8 10l4-2 3 4 5 1M12 8l-2 5"/>'],
    dumbbell:['健身','<path d="m3 8 5 5m8-2 5 5M2 7l5-5m10 20 5-5M6 11l5-5m2 12 5-5M8 8l8 8"/>'],
    walk:['散步','<circle cx="13" cy="4" r="2"/><path d="m9 21 2-7-3-4 4-2 2 4 4 1M15 21l-2-6M6 13l2-3"/>'],
    bike:['骑行','<circle cx="5" cy="17" r="4"/><circle cx="19" cy="17" r="4"/><path d="m5 17 5-9 5 9H5m5-9h6l3 9M8 5h4m4 0h3v3"/>'],
    water:['喝水','<path d="M12 3s-7 8-7 12a7 7 0 0 0 14 0c0-4-7-12-7-12Z"/>'],
    coffee:['咖啡','<path d="M4 7h12v10a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4ZM16 8h2a3 3 0 0 1 0 6h-2M7 2v2m5-2v2"/>'],
    food:['饮食','<path d="M5 2v7m-3-7v5a3 3 0 0 0 6 0V2M5 10v12M18 2c-3 4-3 9 0 10h3V2ZM21 12v10"/>'],
    fruit:['水果','<path d="M12 8C3 1 0 17 7 21c3 2 4-1 5-1s2 3 5 1c7-4 4-20-5-13ZM12 8V5m0 0c1-4 4-3 5-3-1 3-3 4-5 3"/>'],
    meditate:['冥想','<circle cx="12" cy="4" r="2"/><path d="M8 10c2-2 6-2 8 0l2 5 4 1M2 16l4-1 2-5M7 15l-4 6h18l-4-6M12 10v7"/>'],
    heart:['健康','<path d="M20 4c-4-3-8 1-8 1s-4-4-8-1c-6 5 0 11 8 17 8-6 14-12 8-17Z"/>'],
    medicine:['用药','<path d="m5 13 8-8a4 4 0 0 1 6 6l-8 8a4 4 0 0 1-6-6Zm3-3 6 6"/>'],
    brush:['洗漱','<path d="M8 3h8v6H8Zm4 6v12M10 3v6m4-6v6M8 5h8"/>'],
    clean:['清洁','<path d="m14 3-5 10m-5 8 3-9 8 4-5 6ZM17 10l1 2 3 1-3 1-1 2-1-2-2-1 2-1Z"/>'],
    home:['家务','<path d="m2 11 10-8 10 8M5 10v11h14V10M9 21v-7h6v7"/>'],
    plant:['植物','<path d="M12 21V10M12 14C4 14 3 8 3 5c6 0 9 3 9 9Zm0-4c0-6 4-8 9-8 0 5-3 8-9 8M7 21h10"/>'],
    music:['音乐','<path d="M9 18V5l12-2v13M9 9l12-2"/><ellipse cx="6" cy="18" rx="3" ry="3"/><ellipse cx="18" cy="16" rx="3" ry="3"/>'],
    camera:['拍照','<path d="M3 7h4l2-3h6l2 3h4v14H3Z"/><circle cx="12" cy="14" r="4"/>'],
    money:['记账','<rect x="3" y="5" width="18" height="14" rx="2"/><circle cx="12" cy="12" r="3"/><path d="M6 9h.01M18 15h.01"/>'],
    phone:['联络','<path d="m5 3 4 1 1 5-3 2c2 3 3 4 6 6l2-3 5 1 1 4c-4 6-20-9-16-16Z"/>'],
    travel:['出行','<path d="m2 13 8-2V4l2-2 2 2v7l8 2v3l-8-1v4l3 2H7l3-2v-4l-8 1Z"/>'],
    shopping:['购物','<path d="M5 7h14l2 14H3ZM8 8V5a4 4 0 0 1 8 0v3"/>'],
    star:['目标','<path d="m12 2 3 6 7 1-5 5 1 7-6-3-6 3 1-7-5-5 7-1Z"/>']
  };
  // Every icon is a local SVG path using the same stroke system.
  const api={items:Object.fromEntries(Object.entries(items).map(([id,[label,path]])=>[id,{label,path}]))};
  root.DoItIcons=api;if(typeof module!=='undefined')module.exports=api;
})(globalThis);
