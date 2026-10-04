package com.doit.app;
import android.app.*;
import android.content.*;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        String action=intent.getAction();
        if("stop".equals(action)||"complete".equals(action)||"snooze".equals(action)){
            if(intent.getStringExtra("taskId").equals(Scheduler.prefs(c).getString("ringTask",""))){c.stopService(new Intent(c,AlarmService.class));c.getSystemService(NotificationManager.class).cancel(1001);}c.getSystemService(NotificationManager.class).cancel(AlarmService.notificationId(intent));
            if("complete".equals(action)){final PendingResult result=goAsync();new Thread(()->{try{Scheduler.complete(c,intent.getStringExtra("taskId"),intent.getStringExtra("date"));}catch(Exception ignored){}finally{result.finish();}},"DoIt-complete").start();}
            else if("snooze".equals(action)){String id=intent.getStringExtra("taskId"),date=intent.getStringExtra("date");Intent later=Scheduler.eventIntent(c,"snooze",date,intent.getStringExtra("title"),intent.getBooleanExtra("sound",true)).putExtra("originalId",id);Scheduler.prefs(c).edit().putString("snoozeTask",id).putString("snoozeDate",date).apply();Scheduler.schedule(c,System.currentTimeMillis()+600000,later);}
            return;
        }
        boolean exact=Scheduler.exact(c);Intent alarm=new Intent(c,AlarmService.class).putExtras(intent);if("snooze".equals(intent.getStringExtra("taskId"))){alarm.putExtra("taskId",intent.getStringExtra("originalId"));Scheduler.prefs(c).edit().remove("snoozeTask").remove("snoozeDate").apply();}
        // Inexact delivery has no foreground-service exemption; use a normal notification.
        if(exact&&alarm.getBooleanExtra("sound",true)){if(Build.VERSION.SDK_INT>=26)c.startForegroundService(alarm);else c.startService(alarm);}else{Scheduler.channels(c);c.getSystemService(NotificationManager.class).notify(AlarmService.notificationId(alarm),AlarmService.notification(c,alarm));}
        final PendingResult result=goAsync();new Thread(()->{try{Scheduler.sync(c);}finally{result.finish();}},"DoIt-reschedule").start();
    }
}
