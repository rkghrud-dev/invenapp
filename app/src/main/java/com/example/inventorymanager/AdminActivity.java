package com.example.inventorymanager;

import android.app.TimePickerDialog;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.widget.SwitchCompat;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AdminActivity extends ThemedActivity {
    private static final int REQUEST_NOTIFICATION_PERMISSION = 4103;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SettingsStore settingsStore;
    private GoogleOAuthRepository googleOAuthRepository;
    private GoogleProfileRepository googleProfileRepository;
    private SalesSyncRepository salesSyncRepository;

    private SwitchCompat adminReminderSwitch;
    private TextView googleStatusValue;
    private Button googleConnectButton;
    private EditText spreadsheetIdInput;
    private EditText rangeInput;
    private EditText productCodeColumnInput;
    private EditText orderCodeColumnInput;
    private EditText productNameColumnInput;
    private EditText stockColumnInput;
    private EditText salesSpreadsheetIdInput;
    private EditText salesRangeInput;
    private EditText salesProductCodeColumnInput;
    private EditText salesQuantityColumnInput;
    private EditText rankingSpreadsheetIdInput;
    private EditText reminderTimeInput;
    private TextView saveStatus;
    private TextView dataSyncStatus;
    private Button dataSyncButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        settingsStore = new SettingsStore(this);
        googleOAuthRepository = new GoogleOAuthRepository(this);
        googleProfileRepository = new GoogleProfileRepository();
        salesSyncRepository = new SalesSyncRepository();

        adminReminderSwitch = findViewById(R.id.admin_reminder_switch);
        googleStatusValue = findViewById(R.id.google_status_value);
        googleConnectButton = findViewById(R.id.google_connect_button);
        spreadsheetIdInput = findViewById(R.id.spreadsheet_id_input);
        rangeInput = findViewById(R.id.range_input);
        productCodeColumnInput = findViewById(R.id.product_code_column_input);
        orderCodeColumnInput = findViewById(R.id.order_code_column_input);
        productNameColumnInput = findViewById(R.id.product_name_column_input);
        stockColumnInput = findViewById(R.id.stock_column_input);
        salesSpreadsheetIdInput = findViewById(R.id.sales_spreadsheet_id_input);
        salesRangeInput = findViewById(R.id.sales_range_input);
        salesProductCodeColumnInput = findViewById(R.id.sales_product_code_column_input);
        salesQuantityColumnInput = findViewById(R.id.sales_quantity_column_input);
        rankingSpreadsheetIdInput = findViewById(R.id.ranking_spreadsheet_id_input);
        reminderTimeInput = findViewById(R.id.reminder_time_input);
        saveStatus = findViewById(R.id.save_status);
        dataSyncStatus = findViewById(R.id.data_sync_status);
        dataSyncButton = findViewById(R.id.data_sync_button);
        Button saveButton = findViewById(R.id.save_button);
        Button exampleButton = findViewById(R.id.example_button);

        configureReminderTimePicker();
        bindSettings(settingsStore.load());
        bindAdminState();
        refreshGoogleStatus();
        dataSyncStatus.setText(R.string.admin_sync_idle);

        adminReminderSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> updateReminderInputState());
        googleConnectButton.setOnClickListener(view -> checkGoogleConnection());
        saveButton.setOnClickListener(view -> saveSettings());
        exampleButton.setOnClickListener(view -> fillExample());
        dataSyncButton.setOnClickListener(view -> collectDataNow());
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindAdminState();
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
        salesSpreadsheetIdInput.setText(settings.salesSpreadsheetId);
        salesRangeInput.setText(settings.salesRange);
        salesProductCodeColumnInput.setText(settings.salesProductCodeColumn);
        salesQuantityColumnInput.setText(settings.salesQuantityColumn);
        rankingSpreadsheetIdInput.setText(settings.rankingSpreadsheetId);
    }

    private void bindAdminState() {
        adminReminderSwitch.setOnCheckedChangeListener(null);
        adminReminderSwitch.setChecked(settingsStore.isAdminReminderEnabled());
        reminderTimeInput.setText(formatReminderTime(
                settingsStore.getAdminReminderHour(),
                settingsStore.getAdminReminderMinute()
        ));
        updateReminderInputState();
        adminReminderSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> updateReminderInputState());
    }

    private void configureReminderTimePicker() {
        reminderTimeInput.setFocusable(false);
        reminderTimeInput.setFocusableInTouchMode(false);
        reminderTimeInput.setCursorVisible(false);
        reminderTimeInput.setClickable(true);
        reminderTimeInput.setOnClickListener(view -> showReminderTimePicker());
    }

    private void updateReminderInputState() {
        boolean enabled = adminReminderSwitch.isChecked();
        reminderTimeInput.setEnabled(enabled);
        reminderTimeInput.setAlpha(enabled ? 1f : 0.6f);
    }

    private void showReminderTimePicker() {
        if (!adminReminderSwitch.isChecked()) {
            return;
        }

        int[] selectedTime = parseReminderTime(reminderTimeInput.getText().toString());
        int hour = selectedTime != null ? selectedTime[0] : settingsStore.getAdminReminderHour();
        int minute = selectedTime != null ? selectedTime[1] : settingsStore.getAdminReminderMinute();

        TimePickerDialog dialog = new TimePickerDialog(
                this,
                (timePicker, selectedHour, selectedMinute) ->
                        reminderTimeInput.setText(formatReminderTime(selectedHour, selectedMinute)),
                hour,
                minute,
                true
        );
        dialog.show();
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
        googleConnectButton.setTextColor(resolveThemeColor(R.attr.paletteTextPrimary));
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
        if (TextUtils.isEmpty(productNameColumnInput.getText())) {
            productNameColumnInput.setText("B");
        }
        if (TextUtils.isEmpty(stockColumnInput.getText())) {
            stockColumnInput.setText("M");
        }
        if (TextUtils.isEmpty(salesSpreadsheetIdInput.getText())) {
            salesSpreadsheetIdInput.setText("1WgLu0RciK6NKnPjKrcxmwIq0RouUsgv1Ib06eR4SI9A");
        }
        if (TextUtils.isEmpty(salesRangeInput.getText())) {
            salesRangeInput.setText("cj발주서!B:E");
        }
        if (TextUtils.isEmpty(salesProductCodeColumnInput.getText())) {
            salesProductCodeColumnInput.setText("B");
        }
        if (TextUtils.isEmpty(salesQuantityColumnInput.getText())) {
            salesQuantityColumnInput.setText("E");
        }
        if (TextUtils.isEmpty(rankingSpreadsheetIdInput.getText())) {
            rankingSpreadsheetIdInput.setText("19Ahq0nB5zclSFVUEx_FfRuJ3DcftKZNPh-JD2Sn1X_Q");
        }
        if (TextUtils.isEmpty(reminderTimeInput.getText())) {
            reminderTimeInput.setText("15:30");
        }
        saveStatus.setText(R.string.settings_example_filled);
    }

    private void saveSettings() {
        SettingsStore.SheetSettings settings = buildSettingsFromInputs();
        if (!validateSheetSettings(settings)) {
            return;
        }

        int[] reminderTime = parseReminderTime(reminderTimeInput.getText().toString());
        if (adminReminderSwitch.isChecked() && reminderTime == null) {
            saveStatus.setText(R.string.admin_reminder_time_invalid);
            return;
        }
        if (reminderTime == null) {
            reminderTime = new int[]{settingsStore.getAdminReminderHour(), settingsStore.getAdminReminderMinute()};
        }

        persistAdminSettings(settings, reminderTime[0], reminderTime[1]);
        saveStatus.setText(R.string.settings_saved_message);
        setResult(RESULT_OK);
    }

    private void collectDataNow() {
        SettingsStore.SheetSettings settings = buildSettingsFromInputs();
        if (!validateSheetSettings(settings)) {
            dataSyncStatus.setText(R.string.admin_sync_fix_settings);
            return;
        }

        settingsStore.save(settings);
        setResult(RESULT_OK);
        setDataSyncLoading(true);
        dataSyncStatus.setText(R.string.admin_sync_loading);

        executor.execute(() -> {
            try {
                GoogleOAuthRepository.AuthSession session = googleOAuthRepository.ensureAccessToken();
                rememberAccountIfNeeded(session.getAccessToken());

                SalesSyncRepository.SyncResult result;
                try {
                    result = salesSyncRepository.syncToday(settings, session.getAccessToken());
                } catch (IOException firstException) {
                    if (!isAuthExpiredMessage(firstException.getMessage())) {
                        throw firstException;
                    }
                    GoogleOAuthRepository.AuthSession refreshedSession = googleOAuthRepository.refreshAccessToken();
                    rememberAccountIfNeeded(refreshedSession.getAccessToken());
                    result = salesSyncRepository.syncToday(settings, refreshedSession.getAccessToken());
                }

                SalesSyncRepository.SyncResult finalResult = result;
                mainHandler.post(() -> {
                    refreshGoogleStatus();
                    dataSyncStatus.setText(getString(
                            R.string.admin_sync_done,
                            finalResult.getSnapshotDate(),
                            finalResult.getGroupedProductCount(),
                            finalResult.getSourceRowCount()
                    ));
                    setDataSyncLoading(false);
                });
            } catch (IOException exception) {
                googleOAuthRepository.clearAccessToken();
                mainHandler.post(() -> {
                    dataSyncStatus.setText(getString(R.string.admin_sync_failed, safeMessage(exception)));
                    setDataSyncLoading(false);
                });
            }
        });
    }

    private void persistAdminSettings(SettingsStore.SheetSettings settings, int reminderHour, int reminderMinute) {
        settingsStore.save(settings);
        settingsStore.setAdminModeEnabled(true);
        settingsStore.setAdminReminderEnabled(adminReminderSwitch.isChecked());
        settingsStore.setAdminReminderTime(reminderHour, reminderMinute);
        applyReminder(reminderHour, reminderMinute);
    }

    private void applyReminder(int reminderHour, int reminderMinute) {
        if (!settingsStore.isAdminReminderEnabled()) {
            AdminReminderScheduler.cancelReminder(this);
            return;
        }

        AdminReminderScheduler.scheduleReminder(this, reminderHour, reminderMinute);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATION_PERMISSION
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATION_PERMISSION) {
            return;
        }

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            saveStatus.setText(getString(
                    R.string.admin_reminder_saved,
                    formatReminderTime(settingsStore.getAdminReminderHour(), settingsStore.getAdminReminderMinute())
            ));
            return;
        }
        saveStatus.setText(R.string.admin_notification_permission_denied);
    }

    private void rememberAccountIfNeeded(String accessToken) {
        if (!TextUtils.isEmpty(settingsStore.getLastAuthorizedEmail())) {
            return;
        }

        String email = googleProfileRepository.fetchEmail(accessToken);
        if (!TextUtils.isEmpty(email)) {
            settingsStore.saveLastAuthorizedEmail(email);
        }
    }

    private SettingsStore.SheetSettings buildSettingsFromInputs() {
        return new SettingsStore.SheetSettings(
                "",
                SettingsStore.sanitizeSpreadsheetId(spreadsheetIdInput.getText().toString()),
                rangeInput.getText().toString(),
                productCodeColumnInput.getText().toString(),
                orderCodeColumnInput.getText().toString(),
                productNameColumnInput.getText().toString(),
                stockColumnInput.getText().toString(),
                SettingsStore.sanitizeSpreadsheetId(salesSpreadsheetIdInput.getText().toString()),
                salesRangeInput.getText().toString(),
                salesProductCodeColumnInput.getText().toString(),
                salesQuantityColumnInput.getText().toString(),
                SettingsStore.sanitizeSpreadsheetId(rankingSpreadsheetIdInput.getText().toString())
        );
    }

    private boolean validateSheetSettings(SettingsStore.SheetSettings settings) {
        boolean hasSearchColumn = !TextUtils.isEmpty(settings.productCodeColumn)
                || !TextUtils.isEmpty(settings.orderCodeColumn)
                || !TextUtils.isEmpty(settings.productNameColumn);
        boolean hasAnySalesInput = !TextUtils.isEmpty(settings.salesSpreadsheetId)
                || !TextUtils.isEmpty(settings.salesRange)
                || !TextUtils.isEmpty(settings.salesProductCodeColumn)
                || !TextUtils.isEmpty(settings.salesQuantityColumn);

        if (TextUtils.isEmpty(settings.spreadsheetId)
                || TextUtils.isEmpty(settings.range)
                || TextUtils.isEmpty(settings.stockColumn)
                || !hasSearchColumn) {
            saveStatus.setText(R.string.settings_required_message);
            return false;
        }
        if (hasAnySalesInput && !settings.isSalesReady()) {
            saveStatus.setText(R.string.settings_sales_required_message);
            return false;
        }
        if (!settings.isRankingReady()) {
            saveStatus.setText(R.string.settings_ranking_required_message);
            return false;
        }
        return true;
    }

    private void setDataSyncLoading(boolean isLoading) {
        dataSyncButton.setEnabled(!isLoading);
    }

    private boolean isAuthExpiredMessage(String message) {
        if (TextUtils.isEmpty(message)) {
            return false;
        }
        return message.contains("만료")
                || message.contains("승인되지")
                || message.contains("401")
                || message.contains("access token")
                || message.contains("Invalid Credentials");
    }

    private int[] parseReminderTime(String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        Matcher matcher = Pattern.compile("^(?:[01]?\\d|2[0-3]):[0-5]\\d$").matcher(value.trim());
        if (!matcher.matches()) {
            return null;
        }

        String[] pieces = value.trim().split(":");
        return new int[]{Integer.parseInt(pieces[0]), Integer.parseInt(pieces[1])};
    }

    private String formatReminderTime(int hour, int minute) {
        return String.format(Locale.KOREA, "%02d:%02d", hour, minute);
    }

    private String safeMessage(Exception exception) {
        if (exception == null || TextUtils.isEmpty(exception.getMessage())) {
            return getString(R.string.status_unknown_error);
        }
        return exception.getMessage();
    }
}