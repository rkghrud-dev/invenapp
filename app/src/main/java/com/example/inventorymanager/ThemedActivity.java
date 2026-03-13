package com.example.inventorymanager;

import android.os.Bundle;
import android.util.TypedValue;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

public abstract class ThemedActivity extends AppCompatActivity {
    private boolean appliedDarkModeEnabled;
    private int appliedThemeResId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appliedDarkModeEnabled = SettingsStore.isDarkModeEnabled(this);
        appliedThemeResId = SettingsStore.resolveThemeResId(this);
        AppCompatDelegate.setDefaultNightMode(
                appliedDarkModeEnabled
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO
        );
        setTheme(appliedThemeResId);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        boolean currentDarkModeEnabled = SettingsStore.isDarkModeEnabled(this);
        int currentThemeResId = SettingsStore.resolveThemeResId(this);
        if (appliedDarkModeEnabled != currentDarkModeEnabled || appliedThemeResId != currentThemeResId) {
            appliedDarkModeEnabled = currentDarkModeEnabled;
            appliedThemeResId = currentThemeResId;
            recreate();
        }
    }

    protected int resolveThemeColor(int attrResId) {
        TypedValue typedValue = new TypedValue();
        if (!getTheme().resolveAttribute(attrResId, typedValue, true)) {
            return 0;
        }
        if (typedValue.resourceId != 0) {
            return ContextCompat.getColor(this, typedValue.resourceId);
        }
        return typedValue.data;
    }
}
