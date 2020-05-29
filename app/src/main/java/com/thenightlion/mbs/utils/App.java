package com.thenightlion.mbs.utils;

import android.app.Application;

public class App extends Application {

    public static App instance;
    private PermissionsUtils permissionsUtils;
    private SharedPreferencesUtils sharedPreferencesUtils;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        sharedPreferencesUtils = new SharedPreferencesUtils(instance);
        permissionsUtils = new PermissionsUtils(instance);
    }

    public static App getInstance() {
        if (instance == null) {
            instance = new App();
        }
        return instance;
    }

    public SharedPreferencesUtils getSharedPreferencesUtils() {
        return sharedPreferencesUtils;
    }

    public PermissionsUtils getPermissionsUtils() {
        return permissionsUtils;
    }
}
