package com.doit.app;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){final PendingResult result=goAsync();new Thread(()->{try{Scheduler.sync(c);}finally{result.finish();}},"DoIt-boot").start();}
}
