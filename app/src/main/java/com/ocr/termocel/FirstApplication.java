package com.ocr.termocel;

import android.app.Application;
import android.content.Context;

import com.activeandroid.ActiveAndroid;
import com.firebase.client.Firebase;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.log.CustomLogger;

/**
 * This class initializes all the goodies on the app
 */
public class FirstApplication extends Application {

    private static FirstApplication instance;
    private JobManager jobManager;

    public static Context getContext() {
        return context;
    }

    public static Context context;

    public FirstApplication() {
        instance = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize ORM
        ActiveAndroid.initialize(this);

        context = getApplicationContext();

        configureJobManager();

        Firebase.setAndroidContext(this);

    }

    private void configureJobManager() {
        Configuration configuration = new Configuration.Builder(this)
                .customLogger(new CustomLogger() {
                    private static final String TAG = "JOBS";

                    @Override
                    public boolean isDebugEnabled() {
                        return true;
                    }

                    @Override
                    public void d(String text, Object... args) {
//                        Log.d(TAG, String.format(text, args));
                    }


                    @Override
                    public void e(Throwable t, String text, Object... args) {
//                        Log.e(TAG, String.format(text, args), t);
                    }

                    @Override
                    public void e(String text, Object... args) {
//                        Log.e(TAG, String.format(text, args));
                    }
                })
                .minConsumerCount(1)//always keep at least one consumer alive
                .maxConsumerCount(3)//up to 3 consumers at a time
                .loadFactor(3)//3 jobs per consumer
                .consumerKeepAlive(120)//wait 2 minute
                .build();
        jobManager = new JobManager(this, configuration);
    }

    public JobManager getJobManager() {
        return jobManager;
    }

    public static FirstApplication getInstance() {
        return instance;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        ActiveAndroid.dispose();
    }
}
