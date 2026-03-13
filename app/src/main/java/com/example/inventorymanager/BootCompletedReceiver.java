package com.example.inventorymanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        SettingsStore settingsStore = new SettingsStore(context);
        if (!settingsStore.isAdminModeEnabled() || !settingsStore.isAdminReminderEnabled()) {
            return;
        }

        AdminReminderScheduler.scheduleReminder(
                context,
                settingsStore.getAdminReminderHour(),
                settingsStore.getAdminReminderMinute()
        );
    }
}