package com.example.inventorymanager;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UnshippedActivity extends ThemedActivity implements UnshippedAdapter.FeedbackListener {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SettingsStore settingsStore;
    private GoogleOAuthRepository googleOAuthRepository;
    private UnshippedRepository unshippedRepository;
    private UnshippedAdapter adapter;
    private ArrayAdapter<String> vendorAdapter;

    private Spinner vendorSpinner;
    private TextView querySummary;
    private TextView statusText;
    private TextView emptyView;
    private ProgressBar progressBar;
    private Button loadTodayButton;
    private Button queryButton;

    private List<UnshippedItem> loadedItems = Collections.emptyList();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_unshipped);

        settingsStore = new SettingsStore(this);
        googleOAuthRepository = new GoogleOAuthRepository(this);
        unshippedRepository = new UnshippedRepository();

        vendorSpinner = findViewById(R.id.vendor_spinner);
        querySummary = findViewById(R.id.query_summary);
        statusText = findViewById(R.id.status_text);
        emptyView = findViewById(R.id.empty_view);
        progressBar = findViewById(R.id.progress_bar);
        loadTodayButton = findViewById(R.id.load_today_button);
        queryButton = findViewById(R.id.query_button);
        ListView resultsList = findViewById(R.id.results_list);

        adapter = new UnshippedAdapter(this, this);
        resultsList.setAdapter(adapter);
        resultsList.setEmptyView(emptyView);

        vendorAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        vendorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        vendorSpinner.setAdapter(vendorAdapter);

        loadTodayButton.setOnClickListener(v -> loadTodayRows());
        queryButton.setOnClickListener(v -> querySelectedVendor());

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
        querySummary.setText(R.string.unshipped_summary_idle);
        emptyView.setText(R.string.unshipped_empty_results);
        showStatus(getString(R.string.unshipped_status_idle));
    }

    private void loadTodayRows() {
        SettingsStore.SheetSettings settings = settingsStore.load();
        if (TextUtils.isEmpty(settings.salesSpreadsheetId)) {
            showStatus(getString(R.string.unshipped_status_sheet_not_ready));
            return;
        }

        setLoading(true);
        showStatus(getString(R.string.unshipped_status_loading));
        emptyView.setText(R.string.unshipped_empty_results);
        adapter.setItems(null);

        executor.execute(() -> {
            try {
                UnshippedRepository.TodaySnapshot snapshot = loadTodaySnapshot(settings.salesSpreadsheetId);
                mainHandler.post(() -> {
                    loadedItems = snapshot.getItems();
                    updateVendorOptions(snapshot.getVendors());
                    adapter.setItems(null);
                    if (snapshot.getItems().isEmpty()) {
                        querySummary.setText(R.string.unshipped_summary_no_items);
                        showStatus(getString(R.string.unshipped_status_no_today));
                    } else {
                        querySummary.setText(getString(R.string.unshipped_summary_loaded, snapshot.getVendors().size()));
                        showStatus(getString(R.string.unshipped_status_loaded, snapshot.getVendors().size()));
                    }
                    setLoading(false);
                });
            } catch (IOException exception) {
                googleOAuthRepository.clearAccessToken();
                mainHandler.post(() -> {
                    loadedItems = Collections.emptyList();
                    updateVendorOptions(Collections.emptyList());
                    adapter.setItems(null);
                    showStatus(safeMessage(exception));
                    setLoading(false);
                });
            }
        });
    }

    private UnshippedRepository.TodaySnapshot loadTodaySnapshot(String spreadsheetId) throws IOException {
        GoogleOAuthRepository.AuthSession session = googleOAuthRepository.ensureAccessToken();
        try {
            return unshippedRepository.loadTodaySnapshot(spreadsheetId, session.getAccessToken());
        } catch (IOException firstException) {
            if (!isAuthExpiredMessage(firstException.getMessage())) {
                throw firstException;
            }
            GoogleOAuthRepository.AuthSession refreshedSession = googleOAuthRepository.refreshAccessToken();
            return unshippedRepository.loadTodaySnapshot(spreadsheetId, refreshedSession.getAccessToken());
        }
    }

    private void querySelectedVendor() {
        if (loadedItems.isEmpty()) {
            adapter.setItems(null);
            querySummary.setText(R.string.unshipped_summary_no_items);
            emptyView.setText(R.string.unshipped_empty_results);
            showStatus(getString(R.string.unshipped_status_no_today));
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
        querySummary.setText(getString(R.string.unshipped_summary_vendor, vendor, filtered.size()));
        if (filtered.isEmpty()) {
            showStatus(getString(R.string.unshipped_status_no_vendor_result));
            return;
        }
        showStatus(getString(R.string.unshipped_status_result_count, vendor, filtered.size()));
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
        loadTodayButton.setEnabled(!isLoading);
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

