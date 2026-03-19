package com.example.inventorymanager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
public class HomeActivity extends ThemedActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        findViewById(R.id.module_inventory).setOnClickListener(v -> openInventory());
        findViewById(R.id.module_shipping).setOnClickListener(v -> openSalesRanking());
        findViewById(R.id.module_orders).setOnClickListener(v -> openUnshipped());
        findViewById(R.id.module_clients).setOnClickListener(this::showFutureMessage);
        findViewById(R.id.module_reports).setOnClickListener(this::showFutureMessage);
        findViewById(R.id.module_options).setOnClickListener(v -> openOptions());
        findViewById(R.id.admin_entry).setOnClickListener(v -> openAdminMode());
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
