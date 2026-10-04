package com.doit.app;
import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;

public class AlarmService extends Service {
    MediaPlayer player;final Handler handler=new Handler(Looper.getMainLooper());Vibrator vibrator;
    @Override public IBinder onBind(Intent i){return null;}
    static PendingIntent action(Context c,String action,Intent source){Intent i=new Intent(c,AlarmReceiver.class).setAction(action).setData(Uri.parse("doit://action/"+action+"/"+source.getStringExtra("taskId")+"/"+source.getStringExtra("date"))).putExtras(source);return PendingIntent.getBroadcast(c,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static int notificationId(Intent source){return 2000+(source.getStringExtra("taskId").hashCode()&0x3fffffff);}
    static Notification notification(Context c,Intent source){boolean test="test".equals(source.getStringExtra("taskId"));Notification.Builder b=new Notification.Builder(c,source.getBooleanExtra("sound",true)?Scheduler.CHANNEL:Scheduler.QUIET).setSmallIcon(com.doit.app.R.drawable.ic_notification).setContentTitle("Do It · "+source.getStringExtra("title")).setContentText(test?"测试提醒已触发。":"到时间了，做完记得打卡。").setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC).setOnlyAlertOnce(true).setAutoCancel(true).setContentIntent(PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)).addAction(new Notification.Action.Builder(null,"停止",action(c,"stop",source)).build());if(!test)b.addAction(new Notification.Action.Builder(null,"完成打卡",action(c,"complete",source)).build()).addAction(new Notification.Action.Builder(null,"10 分钟后",action(c,"snooze",source)).build());return b.build();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf();return START_NOT_STICKY;}Scheduler.channels(this);startForeground(1001,new Notification.Builder(this,Scheduler.QUIET).setSmallIcon(R.drawable.ic_notification).setContentTitle("Do It · 闹钟正在响铃").setContentText("使用任务通知可停止、打卡或稍后提醒。").setOngoing(true).addAction(new Notification.Action.Builder(null,"停止响铃",action(this,"stop",intent)).build()).build());getSystemService(NotificationManager.class).notify(notificationId(intent),notification(this,intent));Scheduler.prefs(this).edit().putString("ringTask",intent.getStringExtra("taskId")).putString("ringDate",intent.getStringExtra("date")).apply();
        stopSound();handler.removeCallbacksAndMessages(null);
        if(intent.getBooleanExtra("sound",true))try{Uri tone=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);if(tone==null)tone=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);player=new MediaPlayer();player.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK);player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());player.setDataSource(this,tone);player.setLooping(true);player.prepare();player.start();vibrator=(Vibrator)getSystemService(VIBRATOR_SERVICE);if(vibrator!=null)vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,400,600,400,1200},0));}catch(Exception ignored){}
        handler.postDelayed(()->{stopSound();stopForeground(false);stopSelf();},60000);return START_NOT_STICKY;
    }
    void stopSound(){if(player!=null){try{player.stop();}catch(Exception ignored){}player.release();player=null;}if(vibrator!=null)vibrator.cancel();}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);stopSound();Scheduler.prefs(this).edit().remove("ringTask").remove("ringDate").apply();super.onDestroy();}
}
