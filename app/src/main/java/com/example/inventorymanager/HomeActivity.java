package com.example.inventorymanager;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class HomeActivity extends ThemedActivity {
    private SettingsStore settingsStore;
    private TextView statusChip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        settingsStore = new SettingsStore(this);
        statusChip = findViewById(R.id.home_status_chip);

        findViewById(R.id.module_inventory).setOnClickListener(v -> openInventory());
        findViewById(R.id.module_shipping).setOnClickListener(v -> openSalesRanking());
        findViewById(R.id.module_orders).setOnClickListener(this::showFutureMessage);
        findViewById(R.id.module_clients).setOnClickListener(this::showFutureMessage);
        findViewById(R.id.module_reports).setOnClickListener(this::showFutureMessage);
        findViewById(R.id.module_options).setOnClickListener(v -> openOptions());
        findViewById(R.id.admin_entry).setOnClickListener(v -> openAdminMode());

        updateHomeStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateHomeStatus();
    }

    private void openInventory() {
        startActivity(new Intent(this, InventoryActivity.class));
    }

    private void openSalesRanking() {
        startActivity(new Intent(this, SalesRankingActivity.class));
    }

    private void openOptions() {
        startActivity(new Intent(this, OptionsActivity.class));
    }

    private void openAdminMode() {
        startActivity(new Intent(this, SettingsLockActivity.class));
    }

    private void showFutureMessage(View view) {
        Toast.makeText(this, R.string.home_future_message, Toast.LENGTH_SHORT).show();
    }

    private void updateHomeStatus() {
        String account = settingsStore.getLastAuthorizedEmail();
        String modeLabel = settingsStore.isDarkModeEnabled()
                ? getString(R.string.home_dark_mode_on)
                : getString(R.string.home_dark_mode_off);

        if (TextUtils.isEmpty(account)) {
            statusChip.setText(getString(R.string.home_status_no_google, modeLabel));
            return;
        }

        statusChip.setText(getString(R.string.home_status_with_google, account, modeLabel));
    }
}