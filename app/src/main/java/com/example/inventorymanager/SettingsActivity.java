package com.example.inventorymanager;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends ThemedActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SettingsStore settingsStore;
    private GoogleOAuthRepository googleOAuthRepository;
    private GoogleProfileRepository googleProfileRepository;

    private SwitchCompat darkModeSwitch;
    private TextView googleStatusValue;
    private Button googleConnectButton;
    private EditText spreadsheetIdInput;
    private EditText rangeInput;
    private EditText productCodeColumnInput;
    private EditText orderCodeColumnInput;
    private EditText productNameColumnInput;
    private EditText stockColumnInput;
    private TextView saveStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        settingsStore = new SettingsStore(this);
        googleOAuthRepository = new GoogleOAuthRepository(this);
        googleProfileRepository = new GoogleProfileRepository();

        darkModeSwitch = findViewById(R.id.dark_mode_switch);
        googleStatusValue = findViewById(R.id.google_status_value);
        googleConnectButton = findViewById(R.id.google_connect_button);
        spreadsheetIdInput = findViewById(R.id.spreadsheet_id_input);
        rangeInput = findViewById(R.id.range_input);
        productCodeColumnInput = findViewById(R.id.product_code_column_input);
        orderCodeColumnInput = findViewById(R.id.order_code_column_input);
        productNameColumnInput = findViewById(R.id.product_name_column_input);
        stockColumnInput = findViewById(R.id.stock_column_input);
        saveStatus = findViewById(R.id.save_status);
        Button saveButton = findViewById(R.id.save_button);
        Button exampleButton = findViewById(R.id.example_button);

        bindSettings(settingsStore.load());
        bindOptionState();
        refreshGoogleStatus();

        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> toggleDarkMode(isChecked));
        googleConnectButton.setOnClickListener(view -> checkGoogleConnection());
        saveButton.setOnClickListener(view -> saveSettings());
        exampleButton.setOnClickListener(view -> fillExample());
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindOptionState();
        refreshGoogleStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void bindSettings(SettingsStore.SheetSettings settings) {
        spreadsheetIdInput.setText(settings.spreadsheetId);
        rangeInput.setText(settings.range);
        productCodeColumnInput.setText(settings.productCodeColumn);
        orderCodeColumnInput.setText(settings.orderCodeColumn);
        productNameColumnInput.setText(settings.productNameColumn);
        stockColumnInput.setText(settings.stockColumn);
    }

    private void bindOptionState() {
        darkModeSwitch.setOnCheckedChangeListener(null);
        darkModeSwitch.setChecked(settingsStore.isDarkModeEnabled());
        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> toggleDarkMode(isChecked));
    }

    private void toggleDarkMode(boolean enabled) {
        if (settingsStore.isDarkModeEnabled() == enabled) {
            return;
        }

        settingsStore.setDarkModeEnabled(enabled);
        AppCompatDelegate.setDefaultNightMode(
                enabled ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
        recreate();
    }

    private void refreshGoogleStatus() {
        String email = settingsStore.getLastAuthorizedEmail();
        boolean hasToken = googleOAuthRepository.hasStoredAuthorization();

        if (!TextUtils.isEmpty(email)) {
            googleStatusValue.setText(getString(R.string.options_google_connected, email));
            googleConnectButton.setText(R.string.options_google_recheck_button);
            googleConnectButton.setBackgroundResource(R.drawable.bg_button_connected);
            googleConnectButton.setTextColor(getColor(android.R.color.white));
            return;
        }

        if (hasToken) {
            googleStatusValue.setText(R.string.options_google_token_ready);
        } else {
            googleStatusValue.setText(R.string.options_google_not_ready);
        }
        googleConnectButton.setText(R.string.options_google_button);
        googleConnectButton.setBackgroundResource(R.drawable.bg_button_secondary);
        googleConnectButton.setTextColor(getColor(R.color.ebony));
    }

    private void checkGoogleConnection() {
        setGoogleLoading(true);
        saveStatus.setText(R.string.options_google_checking);

        executor.execute(() -> {
            try {
                GoogleOAuthRepository.AuthSession session = googleOAuthRepository.refreshAccessToken();
                String email = googleProfileRepository.fetchEmail(session.getAccessToken());
                if (!TextUtils.isEmpty(email)) {
                    settingsStore.saveLastAuthorizedEmail(email);
                }

                mainHandler.post(() -> {
                    setGoogleLoading(false);
                    refreshGoogleStatus();
                    saveStatus.setText(R.string.options_google_check_done);
                });
            } catch (IOException exception) {
                googleOAuthRepository.clearAccessToken();
                mainHandler.post(() -> {
                    setGoogleLoading(false);
                    refreshGoogleStatus();
                    saveStatus.setText(getString(R.string.options_google_check_failed, safeMessage(exception)));
                });
            }
        });
    }

    private void setGoogleLoading(boolean isLoading) {
        googleConnectButton.setEnabled(!isLoading);
    }

    private void fillExample() {
        if (TextUtils.isEmpty(spreadsheetIdInput.getText())) {
            spreadsheetIdInput.setText("1HWR8zdvx0DYbl4ac9hmGuaaIA47nMO0v1CtO99PyC6w");
        }
        if (TextUtils.isEmpty(rangeInput.getText())) {
            rangeInput.setText("상품정보!A2:M");
        }
        if (TextUtils.isEmpty(productCodeColumnInput.getText())) {
            productCodeColumnInput.setText("A");
        }
        if (TextUtils.isEmpty(orderCodeColumnInput.getText())) {
            orderCodeColumnInput.setText("B");
        }
        if (TextUtils.isEmpty(stockColumnInput.getText())) {
            stockColumnInput.setText("M");
        }
        saveStatus.setText(R.string.settings_example_filled);
    }

    private void saveSettings() {
        SettingsStore.SheetSettings settings = new SettingsStore.SheetSettings(
                "",
                SettingsStore.sanitizeSpreadsheetId(spreadsheetIdInput.getText().toString()),
                rangeInput.getText().toString(),
                productCodeColumnInput.getText().toString(),
                orderCodeColumnInput.getText().toString(),
                productNameColumnInput.getText().toString(),
                stockColumnInput.getText().toString()
        );

        boolean hasSearchColumn = !TextUtils.isEmpty(settings.productCodeColumn)
                || !TextUtils.isEmpty(settings.orderCodeColumn)
                || !TextUtils.isEmpty(settings.productNameColumn);

        if (TextUtils.isEmpty(settings.spreadsheetId)
                || TextUtils.isEmpty(settings.range)
                || TextUtils.isEmpty(settings.stockColumn)
                || !hasSearchColumn) {
            saveStatus.setText(R.string.settings_required_message);
            return;
        }

        settingsStore.save(settings);
        saveStatus.setText(R.string.settings_saved_message);
        setResult(RESULT_OK);
    }

    private String safeMessage(Exception exception) {
        if (exception == null || TextUtils.isEmpty(exception.getMessage())) {
            return getString(R.string.status_unknown_error);
        }
        return exception.getMessage();
    }
}
