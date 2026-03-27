package com.example.inventorymanager;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class HomeActivity extends ThemedActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        TextView versionText = findViewById(R.id.home_version);
        versionText.setText(getString(R.string.home_version_label, getAppVersionName()));

        findViewById(R.id.module_inventory).setOnClickListener(v -> openInventory());
        findViewById(R.id.module_shipping).setOnClickListener(v -> openSalesRanking());
        findViewById(R.id.module_orders).setOnClickListener(v -> openUnshipped());
        findViewById(R.id.module_clients).setOnClickListener(this::showFutureMessage);
        findViewById(R.id.module_reports).setOnClickListener(this::showFutureMessage);
        findViewById(R.id.module_options).setOnClickListener(v -> openOptions());
        findViewById(R.id.admin_entry).setOnClickListener(v -> openAdminMode());
    }

    private String getAppVersionName() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (packageInfo != null && packageInfo.versionName != null && !packageInfo.versionName.trim().isEmpty()) {
                return packageInfo.versionName.trim();
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return "1.0";
    }

    private void openInventory() {
        startActivity(new Intent(this, InventoryActivity.class));
    }

    private void openSalesRanking() {
        startActivity(new Intent(this, SalesRankingActivity.class));
    }

    private void openUnshipped() {
        startActivity(new Intent(this, UnshippedActivity.class));
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
}
