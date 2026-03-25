package com.example.inventorymanager;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UnshippedActivity extends ThemedActivity implements UnshippedAdapter.FeedbackListener {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Calendar selectedDate = Calendar.getInstance(Locale.KOREA);
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);

    private SettingsStore settingsStore;
    private GoogleOAuthRepository googleOAuthRepository;
    private UnshippedRepository unshippedRepository;
    private UnshippedAdapter adapter;
    private ArrayAdapter<String> vendorAdapter;

    private Button selectedDateButton;
    private Spinner vendorSpinner;
    private TextView querySummary;
    private TextView statusText;
    private TextView emptyView;
    private ProgressBar progressBar;
    private Button loadDateButton;
    private Button queryButton;

    private List<UnshippedItem> loadedItems = Collections.emptyList();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_unshipped);

        clearTime(selectedDate);
        settingsStore = new SettingsStore(this);
        googleOAuthRepository = new GoogleOAuthRepository(this);
        unshippedRepository = new UnshippedRepository();

        selectedDateButton = findViewById(R.id.selected_date_button);
        vendorSpinner = findViewById(R.id.vendor_spinner);
        querySummary = findViewById(R.id.query_summary);
        statusText = findViewById(R.id.status_text);
        emptyView = findViewById(R.id.empty_view);
        progressBar = findViewById(R.id.progress_bar);
        loadDateButton = findViewById(R.id.load_today_button);
        queryButton = findViewById(R.id.query_button);
        ListView resultsList = findViewById(R.id.results_list);

        adapter = new UnshippedAdapter(this, this);
        resultsList.setAdapter(adapter);
        resultsList.setEmptyView(emptyView);

        vendorAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        vendorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        vendorSpinner.setAdapter(vendorAdapter);

        selectedDateButton.setOnClickListener(v -> showDatePicker());
        loadDateButton.setOnClickListener(v -> loadSelectedDateRows());
        queryButton.setOnClickListener(v -> querySelectedVendor());

        updateSelectedDateButton();
        updateVendorOptions(Collections.emptyList());
        showIdleState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    @Override
    public void onApplyDefaultFeedback(UnshippedItem item) {
        saveFeedback(item, getString(R.string.unshipped_default_feedback_value));
    }

    @Override
    public void onSaveCustomFeedback(UnshippedItem item, String feedback) {
        saveFeedback(item, feedback);
    }

    private void showIdleState() {
        String dateText = getSelectedDateText();
        querySummary.setText(getString(R.string.unshipped_summary_idle, dateText));
        emptyView.setText(getString(R.string.unshipped_empty_results, dateText));
        showStatus(getString(R.string.unshipped_status_idle, dateText));
    }

    private void showDatePicker() {
        Calendar current = (Calendar) selectedDate.clone();
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedDate.set(year, month, dayOfMonth);
                    clearTime(selectedDate);
                    updateSelectedDateButton();
                    resetLoadedStateForSelectedDate();
                },
                current.get(Calendar.YEAR),
                current.get(Calendar.MONTH),
                current.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private void resetLoadedStateForSelectedDate() {
        loadedItems = Collections.emptyList();
        updateVendorOptions(Collections.emptyList());
        adapter.setItems(null);
        showIdleState();
    }

    private void loadSelectedDateRows() {
        SettingsStore.SheetSettings settings = settingsStore.load();
        if (TextUtils.isEmpty(settings.salesSpreadsheetId)) {
            showStatus(getString(R.string.unshipped_status_sheet_not_ready));
            return;
        }

        Calendar targetDate = (Calendar) selectedDate.clone();
        String dateText = formatDate(targetDate);

        setLoading(true);
        showStatus(getString(R.string.unshipped_status_loading, dateText));
        emptyView.setText(getString(R.string.unshipped_empty_results, dateText));
        adapter.setItems(null);

        executor.execute(() -> {
            try {
                UnshippedRepository.DateSnapshot snapshot = loadDateSnapshot(settings.salesSpreadsheetId, targetDate);
                mainHandler.post(() -> {
                    loadedItems = snapshot.getItems();
                    updateVendorOptions(snapshot.getVendors());
                    adapter.setItems(null);
                    if (snapshot.getItems().isEmpty()) {
                        querySummary.setText(getString(R.string.unshipped_summary_no_items, dateText));
                        showStatus(getString(R.string.unshipped_status_no_today, dateText));
                    } else {
                        querySummary.setText(getString(R.string.unshipped_summary_loaded, dateText, snapshot.getVendors().size()));
                        showStatus(getString(R.string.unshipped_status_loaded, dateText, snapshot.getVendors().size()));
                    }
                    setLoading(false);
                });
            } catch (IOException exception) {
                googleOAuthRepository.clearAccessToken();
                mainHandler.post(() -> {
                    loadedItems = Collections.emptyList();
                    updateVendorOptions(Collections.emptyList());
                    adapter.setItems(null);
                    emptyView.setText(getString(R.string.unshipped_empty_results, dateText));
                    showStatus(safeMessage(exception));
                    setLoading(false);
                });
            }
        });
    }

    private UnshippedRepository.DateSnapshot loadDateSnapshot(String spreadsheetId, Calendar targetDate) throws IOException {
        GoogleOAuthRepository.AuthSession session = googleOAuthRepository.ensureAccessToken();
        try {
            return unshippedRepository.loadDateSnapshot(spreadsheetId, targetDate, session.getAccessToken());
        } catch (IOException firstException) {
            if (!isAuthExpiredMessage(firstException.getMessage())) {
                throw firstException;
            }
            GoogleOAuthRepository.AuthSession refreshedSession = googleOAuthRepository.refreshAccessToken();
            return unshippedRepository.loadDateSnapshot(spreadsheetId, targetDate, refreshedSession.getAccessToken());
        }
    }

    private void querySelectedVendor() {
        String dateText = getSelectedDateText();
        if (loadedItems.isEmpty()) {
            adapter.setItems(null);
            querySummary.setText(getString(R.string.unshipped_summary_no_items, dateText));
            emptyView.setText(getString(R.string.unshipped_empty_results, dateText));
            showStatus(getString(R.string.unshipped_status_no_today, dateText));
            return;
        }

        String vendor = getSelectedVendor();
        if (TextUtils.isEmpty(vendor)) {
            showStatus(getString(R.string.unshipped_status_need_vendor));
            return;
        }

        List<UnshippedItem> filtered = new ArrayList<>();
        for (UnshippedItem item : loadedItems) {
            if (vendor.equals(item.getVendor())) {
                filtered.add(item);
            }
        }

        adapter.setItems(filtered);
        emptyView.setText(R.string.unshipped_none);
        querySummary.setText(getString(R.string.unshipped_summary_vendor, dateText, vendor, filtered.size()));
        if (filtered.isEmpty()) {
            showStatus(getString(R.string.unshipped_status_no_vendor_result));
            return;
        }
        showStatus(getString(R.string.unshipped_status_result_count, dateText, vendor, filtered.size()));
    }

    private void saveFeedback(UnshippedItem item, String feedback) {
        if (item == null) {
            return;
        }

        String normalized = feedback == null ? "" : feedback.trim();
        if (TextUtils.isEmpty(normalized)) {
            showStatus(getString(R.string.unshipped_status_need_feedback));
            return;
        }

        SettingsStore.SheetSettings settings = settingsStore.load();
        if (TextUtils.isEmpty(settings.salesSpreadsheetId)) {
            showStatus(getString(R.string.unshipped_status_sheet_not_ready));
            return;
        }

        setLoading(true);
        showStatus(getString(R.string.unshipped_status_saving));

        executor.execute(() -> {
            try {
                saveFeedbackValue(settings.salesSpreadsheetId, item.getSheetRowNumber(), normalized);
                mainHandler.post(() -> {
                    item.applySavedFeedback(normalized);
                    adapter.notifyDataSetChanged();
                    showStatus(getString(R.string.unshipped_status_saved));
                    setLoading(false);
                });
            } catch (IOException exception) {
                googleOAuthRepository.clearAccessToken();
                mainHandler.post(() -> {
                    showStatus(safeMessage(exception));
                    setLoading(false);
                });
            }
        });
    }

    private void saveFeedbackValue(String spreadsheetId, int rowNumber, String feedback) throws IOException {
        GoogleOAuthRepository.AuthSession session = googleOAuthRepository.ensureAccessToken();
        try {
            unshippedRepository.saveSellerFeedback(spreadsheetId, rowNumber, feedback, session.getAccessToken());
        } catch (IOException firstException) {
            if (!isAuthExpiredMessage(firstException.getMessage())) {
                throw firstException;
            }
            GoogleOAuthRepository.AuthSession refreshedSession = googleOAuthRepository.refreshAccessToken();
            unshippedRepository.saveSellerFeedback(spreadsheetId, rowNumber, feedback, refreshedSession.getAccessToken());
        }
    }

    private void updateSelectedDateButton() {
        selectedDateButton.setText(getSelectedDateText());
    }

    private void updateVendorOptions(List<String> vendors) {
        vendorAdapter.clear();
        if (vendors == null || vendors.isEmpty()) {
            vendorAdapter.add(getString(R.string.unshipped_none));
        } else {
            vendorAdapter.addAll(vendors);
        }
        vendorAdapter.notifyDataSetChanged();
        vendorSpinner.setSelection(0);
    }

    private String getSelectedVendor() {
        Object selectedItem = vendorSpinner.getSelectedItem();
        return selectedItem == null ? "" : selectedItem.toString().trim();
    }

    private String getSelectedDateText() {
        return formatDate(selectedDate);
    }

    private String formatDate(Calendar date) {
        return displayDateFormat.format(date.getTime());
    }

    private void clearTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
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

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        selectedDateButton.setEnabled(!isLoading);
        loadDateButton.setEnabled(!isLoading);
        queryButton.setEnabled(!isLoading);
        vendorSpinner.setEnabled(!isLoading);
        adapter.setControlsEnabled(!isLoading);
    }

    private void showStatus(String message) {
        statusText.setText(message);
    }

    private String safeMessage(Exception exception) {
        if (exception == null || TextUtils.isEmpty(exception.getMessage())) {
            return getString(R.string.status_unknown_error);
        }
        return exception.getMessage();
    }
}
