package com.doit.app;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import org.json.*;
import java.time.*;
import java.util.*;

final class Scheduler {
    static final String CHANNEL="doit-alarms", QUIET="doit-quiet";
    static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("doit",Context.MODE_PRIVATE);}
    static synchronized String load(Context c){return prefs(c).getString("state","{\"version\":1,\"settings\":{\"dayStart\":0,\"sound\":true},\"tasks\":[],\"completions\":{}}");}
    static synchronized void save(Context c,String json)throws Exception{JSONObject value=new JSONObject(json);if(value.optInt("version")!=1||value.optJSONArray("tasks")==null||value.optJSONObject("completions")==null)throw new Exception("无效的任务数据");if(!prefs(c).edit().putString("state",json).commit())throw new Exception("本机存储写入失败");}
    static JSONObject state(Context c){try{return new JSONObject(load(c));}catch(Exception e){throw new IllegalStateException("本机数据损坏",e);}}
    static String day(JSONObject state){LocalDate now=LocalDate.now();if(LocalTime.now().getHour()<state.optJSONObject("settings").optInt("dayStart"))now=now.minusDays(1);return now.toString();}
    static JSONObject revision(JSONObject task,String date){JSONArray rs=task.optJSONArray("revisions");for(int i=rs.length()-1;i>=0;i--){JSONObject r=rs.optJSONObject(i);if(r.optString("from").compareTo(date)<=0)return r;}return null;}
    static boolean due(JSONObject task,LocalDate date){String d=date.toString(),archived=task.optString("archivedOn","");if(d.compareTo(task.optString("createdOn"))<0||(!archived.isEmpty()&&!archived.equals("null")&&d.compareTo(archived)>=0))return false;JSONObject r=revision(task,d);if(r==null||!r.optBoolean("enabled")||r.optBoolean("undated"))return false;JSONObject s=r.optJSONObject("repeat");String type=s.optString("type");if(type.equals("daily"))return true;if(type.equals("once"))return d.equals(s.optString("anchor"));if(type.equals("weekdays")){JSONArray ws=s.optJSONArray("weekdays");for(int i=0;i<ws.length();i++)if(ws.optInt(i)==date.getDayOfWeek().getValue())return true;return false;}long days=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(s.optString("anchor")),date);return days>=0&&days%s.optInt("interval",1)==0;}
    static boolean done(JSONObject state,String id,String date){JSONObject records=state.optJSONObject("completions").optJSONObject(id);if(records==null)return false;JSONArray tasks=state.optJSONArray("tasks");for(int i=0;i<tasks.length();i++){JSONObject t=tasks.optJSONObject(i);if(t.optString("id").equals(id)&&t.optString("kind").equals("note"))return records.length()>0;}return records.has(date);}
    static JSONObject status(Context c){JSONObject out=new JSONObject();try{out.put("platform","android").put("notifications",c.getSystemService(NotificationManager.class).areNotificationsEnabled()).put("exact",exact(c));}catch(Exception ignored){}return out;}
    static boolean exact(Context c){return Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager.class).canScheduleExactAlarms();}
    static void channels(Context c){NotificationManager nm=c.getSystemService(NotificationManager.class);for(String id:new String[]{CHANNEL,QUIET}){NotificationChannel channel=new NotificationChannel(id,id.equals(CHANNEL)?"任务闹钟":"安静提醒",NotificationManager.IMPORTANCE_HIGH);channel.setSound(null,null);channel.enableVibration(false);nm.createNotificationChannel(channel);}}
    static Intent eventIntent(Context c,String id,String date,String title,boolean sound){return new Intent(c,AlarmReceiver.class).setAction("alarm").setData(Uri.parse("doit://alarm/"+id)).putExtra("taskId",id).putExtra("date",date).putExtra("title",title).putExtra("sound",sound);}
    static PendingIntent event(Context c,Intent intent){return PendingIntent.getBroadcast(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static void schedule(Context c,long at,Intent intent){AlarmManager am=c.getSystemService(AlarmManager.class);PendingIntent p=event(c,intent);if(exact(c)){PendingIntent show=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);am.setAlarmClock(new AlarmManager.AlarmClockInfo(at,show),p);}else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,p);}
    static synchronized void sync(Context c){
        channels(c);JSONObject state=state(c);JSONArray tasks=state.optJSONArray("tasks");AlarmManager am=c.getSystemService(AlarmManager.class);
        Set<String> previous=new HashSet<>(prefs(c).getStringSet("scheduled",new HashSet<>())),next=new HashSet<>();
        for(int n=0;n<tasks.length();n++){
            JSONObject task=tasks.optJSONObject(n);String id=task.optString("id");
            // Cancel first so time/repeat edits and check-ins cannot leave old alarms behind.
            am.cancel(event(c,eventIntent(c,id,"","",false)));
            LocalDate start=LocalDate.parse(day(state));JSONObject r=revision(task,start.toString());
            if(r==null||!r.optBoolean("enabled")||!r.optBoolean("alarm")||r.isNull("time")||task.opt("archivedOn")!=JSONObject.NULL){c.getSystemService(NotificationManager.class).cancel(2000+(id.hashCode()&0x3fffffff));continue;}
            if(task.optString("kind").equals("note")&&done(state,id,start.toString())){c.getSystemService(NotificationManager.class).cancel(2000+(id.hashCode()&0x3fffffff));continue;}
            if(r.optJSONObject("repeat").optString("type").equals("once")){LocalDate anchor=LocalDate.parse(r.optJSONObject("repeat").optString("anchor"));if(anchor.isAfter(start))start=anchor;}
            for(int i=0;i<=370;i++){
                LocalDate logical=start.plusDays(i);String date=logical.toString();if(!due(task,logical)||done(state,id,date))continue;
                LocalTime time=LocalTime.parse(r.optString("time"));LocalDate civil=time.getHour()<state.optJSONObject("settings").optInt("dayStart")?logical.plusDays(1):logical;
                long at=civil.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();if(at<=System.currentTimeMillis())continue;
                schedule(c,at,eventIntent(c,id,date,r.optString("title"),state.optJSONObject("settings").optBoolean("sound",true)));next.add(id);break;
            }
        }
        previous.removeAll(next);for(String id:previous)am.cancel(event(c,eventIntent(c,id,"","",false)));
        prefs(c).edit().putStringSet("scheduled",next).apply();
        String ringing=prefs(c).getString("ringTask","");if(!ringing.isEmpty()){String ringDate=prefs(c).getString("ringDate","");boolean keep=false;for(int i=0;i<tasks.length();i++){JSONObject t=tasks.optJSONObject(i);if(t.optString("id").equals(ringing)){JSONObject r=revision(t,ringDate);keep=r!=null&&r.optBoolean("alarm")&&due(t,LocalDate.parse(ringDate))&&!done(state,ringing,ringDate);break;}}if(!keep&&!ringing.equals("test")){c.stopService(new Intent(c,AlarmService.class));c.getSystemService(NotificationManager.class).cancel(1001);c.getSystemService(NotificationManager.class).cancel(2000+(ringing.hashCode()&0x3fffffff));}}
        Intent snooze=eventIntent(c,"snooze","","",false);String snoozeTask=prefs(c).getString("snoozeTask","");String snoozeDate=prefs(c).getString("snoozeDate","");if(!snoozeTask.isEmpty()){boolean valid=false;for(int i=0;i<tasks.length();i++){JSONObject t=tasks.optJSONObject(i);if(t.optString("id").equals(snoozeTask)){JSONObject r=revision(t,snoozeDate);valid=r!=null&&r.optBoolean("alarm")&&due(t,LocalDate.parse(snoozeDate))&&!done(state,snoozeTask,snoozeDate);break;}}if(!valid){am.cancel(event(c,snooze));prefs(c).edit().remove("snoozeTask").remove("snoozeDate").apply();}}
    }
    static void test(Context c,boolean sound){schedule(c,System.currentTimeMillis()+10000,eventIntent(c,"test",day(state(c)),"提醒测试",sound));}
    static synchronized void complete(Context c,String id,String date)throws Exception{JSONObject state=state(c);boolean valid=false;JSONArray tasks=state.optJSONArray("tasks");for(int i=0;i<tasks.length();i++){JSONObject t=tasks.optJSONObject(i);if(t.optString("id").equals(id)&&due(t,LocalDate.parse(date)))valid=true;}if(!valid||done(state,id,date))return;JSONObject all=state.getJSONObject("completions"),records=all.optJSONObject(id);if(records==null)records=new JSONObject();records.put(date,new JSONObject().put("at",Instant.now().toString()).put("manual",!date.equals(day(state))));all.put(id,records);save(c,state.toString());sync(c);}
}
