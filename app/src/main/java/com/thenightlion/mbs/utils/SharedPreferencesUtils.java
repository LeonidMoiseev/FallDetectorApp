package com.thenightlion.mbs.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPreferencesUtils {
    private static final String APP_PREFERENCES = "mySettings";
    private static final String DEFAULT_STRING_VALUE = "null";
    private SharedPreferences sharedPreferences;

    SharedPreferencesUtils(Context context) {
        this.sharedPreferences = context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
    }

    public String getString(String key) {
        return sharedPreferences.getString(key, DEFAULT_STRING_VALUE);
    }

    public void putString(String key, String value) {
        sharedPreferences.edit().putString(key, value).apply();
    }
}
