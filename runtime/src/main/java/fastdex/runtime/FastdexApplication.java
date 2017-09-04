package fastdex.runtime;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import fastdex.runtime.fastdex.Fastdex;
import fastdex.runtime.fd.Logging;
import fastdex.runtime.fd.MonkeyPatcher;
import fastdex.runtime.fd.Restarter;
import fastdex.runtime.multidex.MultiDex;
import fastdex.runtime.utils.ProcessUtils;

/**
 * Created by tong on 17/3/11.
 */
public class FastdexApplication extends Application {
    public static final String LOG_TAG = "Fastdex";
    private Application realApplication;

    private BroadcastReceiver broadcastReceiver;

    private String getOriginApplicationName(Context context) {
        ApplicationInfo appInfo = null;
        try {
            appInfo = context.getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        String msg = appInfo.metaData.getString("FASTDEX_ORIGIN_APPLICATION_CLASSNAME");
        return msg;
    }

    private void createRealApplication(Context context) {
       String applicationClass = getOriginApplicationName(context);
        if (applicationClass != null) {
            Log.d(LOG_TAG, new StringBuilder().append("About to create real application of class name = ").append(applicationClass).toString());

            try {
                Class realClass = Class.forName(applicationClass);
                Constructor constructor = realClass.getConstructor(new Class[0]);
                this.realApplication = ((Application) constructor.newInstance(new Object[0]));
                Log.v(LOG_TAG, new StringBuilder().append("Created real app instance successfully :").append(this.realApplication).toString());

            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        } else {
            this.realApplication = new Application();
        }
    }

    protected void attachBaseContext(Context context) {
        super.attachBaseContext(context);
        MultiDex.install(context);
        fixGoogleMultiDex(context);
        Fastdex.get(context).onAttachBaseContext(this);
        createRealApplication(context);

        if (this.realApplication != null) {
            try {
                Method attachBaseContext = ContextWrapper.class
                        .getDeclaredMethod("attachBaseContext", new Class[]{Context.class});

                attachBaseContext.setAccessible(true);
                attachBaseContext.invoke(this.realApplication, new Object[]{context});
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private void fixGoogleMultiDex(Context context) {
        try {
            Class clazz = getClassLoader().loadClass("android.support.multidex.MultiDex");
            Field field = clazz.getDeclaredField("installedApk");
            field.setAccessible(true);
            Set<String> installedApk = (Set<String>) field.get(null);

            installedApk.addAll(MultiDex.installedApk);
        } catch (Throwable e) {

        }
    }

    public Context createPackageContext(String packageName, int flags)
            throws PackageManager.NameNotFoundException {
        Context c = this.realApplication.createPackageContext(packageName, flags);
        return c == null ? this.realApplication : c;
    }

    public void registerComponentCallbacks(ComponentCallbacks callback) {
        this.realApplication.registerComponentCallbacks(callback);
    }

    public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        this.realApplication.registerActivityLifecycleCallbacks(callback);
    }

    public void registerOnProvideAssistDataListener(OnProvideAssistDataListener callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            this.realApplication.registerOnProvideAssistDataListener(callback);
        }
    }

    public void unregisterComponentCallbacks(ComponentCallbacks callback) {
        this.realApplication.unregisterComponentCallbacks(callback);
    }

    public void unregisterActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        this.realApplication.unregisterActivityLifecycleCallbacks(callback);
    }

    public void unregisterOnProvideAssistDataListener(OnProvideAssistDataListener callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            this.realApplication.unregisterOnProvideAssistDataListener(callback);
        }
    }

    public void onCreate() {
        super.onCreate();

        if (Fastdex.get(this).isFastdexEnabled()) {
            //startServer();
            if (ProcessUtils.isMainProcess(this)) {
                startService(new Intent(this,InstantRunService.class));
                broadcastReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String resources = intent.getStringExtra("resources");
                        Log.d(Logging.LOG_TAG,"收到请求: " + resources);
                        final List<Activity> activities = Restarter.getActivities(context, false);
                        MonkeyPatcher.monkeyPatchExistingResources(context, resources, activities);

                        Activity activity = Restarter.getForegroundActivity(context);
                        if (activity != null) {
                            Restarter.restartActivityOnUiThread(activity);
                        }
                    }
                };

                IntentFilter filter = new IntentFilter();
                filter.addCategory(getPackageName());
                filter.addAction("fastdex_monkey_patch_existing_resources");
                registerReceiver(broadcastReceiver,filter);
            }
        }
        if (this.realApplication != null) {
            MonkeyPatcher.monkeyPatchApplication(this,this,realApplication);
            this.realApplication.onCreate();
        }
    }

    @Override
    public void onTerminate() {
        if (this.broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver);
        }
        super.onTerminate();
    }
}

