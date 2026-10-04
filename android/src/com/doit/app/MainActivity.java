package com.doit.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    WebView web;
    private static final int IMPORT = 51, EXPORT = 52;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(Color.rgb(247,247,244));
        getWindow().setNavigationBarColor(Color.rgb(247,247,244));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(247,247,244));
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(false);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.getSettings().setSupportZoom(false);
        web.addJavascriptInterface(new Bridge(), "DoItAndroid");
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equals(uri.getScheme()) && "appassets.androidplatform.net".equals(uri.getHost()) && uri.getPath().startsWith("/assets/")) {
                    String path = uri.getPath().substring(8);
                    if (!path.contains("..") && !path.contains("\\")) try {
                        String mime = path.endsWith(".js") ? "application/javascript" : path.endsWith(".css") ? "text/css" : "text/html";
                        return new WebResourceResponse(mime,"UTF-8",getAssets().open(path));
                    } catch(IOException ignored) {}
                }
                return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !"appassets.androidplatform.net".equals(request.getUrl().getHost());
            }
            @Override public void onPageFinished(WebView view, String url) { syncInBackground(); }
        });
        setContentView(web);
        if (Build.VERSION.SDK_INT >= 30) web.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }
    void emit(Object value) { runOnUiThread(()->web.evaluateJavascript("window.DoItNativeEvent && window.DoItNativeEvent("+JSONObject.quote(value instanceof String ? (String)value : value.toString())+")",null)); }
    void event(JSONObject value) { runOnUiThread(()->web.evaluateJavascript("window.DoItNativeEvent && window.DoItNativeEvent("+value.toString()+")",null)); }
    private void syncInBackground(){new Thread(()->{try{Scheduler.sync(this);}catch(Exception e){try{event(new JSONObject().put("error",e.getMessage()));}catch(Exception ignored){}}},"DoIt-schedule").start();}
    @Override public void onResume() { super.onResume(); if(web!=null){syncInBackground();emit("focus");} }
    @Override public void onBackPressed() { web.evaluateJavascript("(function(){let d=Array.from(document.querySelectorAll('dialog[open]')).pop();if(d){d.close();return true;}return false;})()",result->{if(!"true".equals(result))moveTaskToBack(true);}); }
    private void permissions() { if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},61);else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName())); }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);syncInBackground();emit("focus");}
    class Bridge {
        @JavascriptInterface public String call(String raw) {
            JSONObject result = new JSONObject();
            try {
                JSONObject req = new JSONObject(raw),payload=req.optJSONObject("payload");String action=req.getString("action");Object data=JSONObject.NULL;
                switch(action) {
                    case "load": data=new JSONObject(Scheduler.load(MainActivity.this));break;
                    case "save": Scheduler.save(MainActivity.this,payload.getJSONObject("state").toString());data=true;break;
                    case "sync": Scheduler.sync(MainActivity.this);data=Scheduler.status(MainActivity.this);break;
                    case "status": data=Scheduler.status(MainActivity.this);break;
                    case "permission":runOnUiThread(()->permissions());data=true;break;
                    case "exact":runOnUiThread(()->{if(Build.VERSION.SDK_INT>=31)startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));});data=true;break;
                    case "appSettings":runOnUiThread(()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));data=true;break;
                    case "test":if(!getSystemService(NotificationManager.class).areNotificationsEnabled())throw new IOException("请先允许系统通知");if(!Scheduler.exact(MainActivity.this))throw new IOException("请先允许「闹钟和提醒」，再测试准时提醒");Scheduler.test(MainActivity.this,payload.optBoolean("sound",true));data=true;break;
                    case "export":runOnUiThread(()->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"Do-It-"+Scheduler.day(Scheduler.state(MainActivity.this))+".json"),EXPORT));break;
                    case "pickImport":runOnUiThread(()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),IMPORT));break;
                    case "beforeImport":if(!Scheduler.prefs(MainActivity.this).edit().putString("before-import",Scheduler.load(MainActivity.this)).commit())throw new IOException("未能保存恢复前的数据");data=true;break;
                    default:throw new IOException("不支持的操作");
                }
                result.put("data",data);
            } catch(Exception e){try{result.put("error",e.getMessage()==null?"操作未完成":e.getMessage());}catch(Exception ignored){}}
            return result.toString();
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent intent) {
        super.onActivityResult(request,result,intent);
        if(result!=RESULT_OK||intent==null||intent.getData()==null)return;
        new Thread(()->{try {
            if(request==EXPORT){try(OutputStream out=getContentResolver().openOutputStream(intent.getData())){out.write(Scheduler.load(this).getBytes(StandardCharsets.UTF_8));}}
            else if(request==IMPORT){ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream input=getContentResolver().openInputStream(intent.getData())){byte[] buf=new byte[8192];int n;while((n=input.read(buf))!=-1){out.write(buf,0,n);if(out.size()>20_000_000)throw new IOException("备份文件过大");}}event(new JSONObject().put("type","importCandidate").put("raw",out.toString("UTF-8")));}
        }catch(Exception e){try{event(new JSONObject().put("error",e.getMessage()));}catch(Exception ignored){}}},"DoIt-file").start();
    }
}
