package com.example.inventorymanager;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InventoryActivity extends ThemedActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SettingsStore settingsStore;
    private SheetsRepository sheetsRepository;
    private GoogleProfileRepository googleProfileRepository;
    private GoogleOAuthRepository googleOAuthRepository;
    private SearchHistoryStore searchHistoryStore;
    private SearchResultAdapter adapter;

    private View mainRoot;
    private EditText searchInput;
    private TextView statusText;
    private TextView emptyView;
    private ProgressBar progressBar;
    private Button searchButton;
    private Button saveSearchButton;
    private Button loadHistoryButton;

    private List<InventoryItem> currentResults = Collections.emptyList();
    private String lastSearchedQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);

        settingsStore = new SettingsStore(this);
        sheetsRepository = new SheetsRepository();
        googleProfileRepository = new GoogleProfileRepository();
        googleOAuthRepository = new GoogleOAuthRepository(this);
        searchHistoryStore = new SearchHistoryStore(this);

        mainRoot = findViewById(R.id.main_root);
        searchInput = findViewById(R.id.search_input);
        statusText = findViewById(R.id.status_text);
        emptyView = findViewById(R.id.empty_view);
        progressBar = findViewById(R.id.progress_bar);
        searchButton = findViewById(R.id.search_button);
        saveSearchButton = findViewById(R.id.save_search_button);
        loadHistoryButton = findViewById(R.id.load_history_button);
        ListView resultsList = findViewById(R.id.results_list);

        adapter = new SearchResultAdapter(this);
        resultsList.setAdapter(adapter);
        resultsList.setEmptyView(emptyView);

        searchButton.setOnClickListener(view -> runSearch());
        saveSearchButton.setOnClickListener(view -> saveCurrentSearch());
        loadHistoryButton.setOnClickListener(view -> openSavedSearches());
        searchInput.setOnEditorActionListener(this::handleEditorAction);

        showIdleStatus();
        mainRoot.post(this::resetSearchFocus);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showIdleStatus();
        mainRoot.post(this::resetSearchFocus);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void resetSearchFocus() {
        if (mainRoot == null || searchInput == null) {
            return;
        }
        searchInput.clearFocus();
        mainRoot.requestFocus();
    }

    private boolean handleEditorAction(TextView view, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
            runSearch();
            return true;
        }
        return false;
    }

    private void showIdleStatus() {
        if (!settingsStore.load().isReady()) {
            showStatus(getString(R.string.status_sheet_not_ready));
            return;
        }

        String lastEmail = settingsStore.getLastAuthorizedEmail();
        if (!TextUtils.isEmpty(lastEmail)) {
            showStatus(getString(R.string.status_connected_account, lastEmail));
            return;
        }

        showStatus(getString(R.string.status_inventory_idle));
    }

    private void runSearch() {
        String query = searchInput.getText().toString().trim();
        if (TextUtils.isEmpty(query)) {
            clearCurrentResults();
            showStatus(getString(R.string.status_enter_query));
            return;
        }

        if (!settingsStore.load().isReady()) {
            clearCurrentResults();
            showStatus(getString(R.string.status_sheet_not_ready));
            return;
        }

        setLoading(true);
        showStatus(getString(R.string.status_loading));
        executor.execute(() -> {
            try {
                GoogleOAuthRepository.AuthSession session = googleOAuthRepository.ensureAccessToken();
                rememberAccountIfNeeded(session.getAccessToken());

                List<InventoryItem> results;
                try {
                    results = sheetsRepository.search(settingsStore.load(), query, session.getAccessToken());
                } catch (IOException firstException) {
                    if (!isAuthExpiredMessage(firstException.getMessage())) {
                        throw firstException;
                    }

                    GoogleOAuthRepository.AuthSession refreshedSession = googleOAuthRepository.refreshAccessToken();
                    rememberAccountIfNeeded(refreshedSession.getAccessToken());
                    results = sheetsRepository.search(settingsStore.load(), query, refreshedSession.getAccessToken());
                }

                List<InventoryItem> finalResults = results;
                mainHandler.post(() -> {
                    lastSearchedQuery = query;
                    currentResults = finalResults;
                    adapter.setItems(finalResults);
                    if (finalResults.isEmpty()) {
                        showStatus(getString(R.string.status_no_results));
                    } else {
                        showStatus(getString(R.string.status_result_count, finalResults.size()));
                    }
                    setLoading(false);
                });
            } catch (IOException exception) {
                googleOAuthRepository.clearAccessToken();
                mainHandler.post(() -> {
                    clearCurrentResults();
                    showStatus(safeMessage(exception));
                    setLoading(false);
                });
            }
        });
    }

    private void saveCurrentSearch() {
        if (TextUtils.isEmpty(lastSearchedQuery) || currentResults.isEmpty()) {
            showStatus(getString(R.string.status_history_need_result));
            return;
        }

        searchHistoryStore.save(lastSearchedQuery, buildHistoryPreview(currentResults), currentResults.size());
        showStatus(getString(R.string.status_history_saved));
    }

    private void openSavedSearches() {
        List<SearchHistoryStore.Entry> entries = searchHistoryStore.loadEntries();
        if (entries.isEmpty()) {
            showStatus(getString(R.string.status_history_empty));
            return;
        }

        CharSequence[] labels = new CharSequence[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            labels[i] = buildHistoryLabel(entries.get(i));
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.history_dialog_title)
                .setItems(labels, (dialog, which) -> loadSavedSearch(entries.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private CharSequence buildHistoryLabel(SearchHistoryStore.Entry entry) {
        StringBuilder builder = new StringBuilder(entry.getQuery());
        if (!TextUtils.isEmpty(entry.getPreview()) && !entry.getQuery().equals(entry.getPreview())) {
            builder.append("\n").append(entry.getPreview());
        }
        builder.append("\n").append(entry.getSavedAtLabel()).append(" 저장");
        if (entry.getResultCount() > 0) {
            builder.append(" · ").append(entry.getResultCount()).append("건");
        }
        return builder.toString();
    }

    private void loadSavedSearch(SearchHistoryStore.Entry entry) {
        if (entry == null || TextUtils.isEmpty(entry.getQuery())) {
            return;
        }

        searchInput.setText(entry.getQuery());
        searchInput.setSelection(entry.getQuery().length());
        runSearch();
    }

    private String buildHistoryPreview(List<InventoryItem> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }

        InventoryItem first = results.get(0);
        String preview = first.getProductName();
        if (TextUtils.isEmpty(preview)) {
            preview = first.getOrderCode();
        }
        if (TextUtils.isEmpty(preview)) {
            preview = first.getProductCode();
        }
        return preview == null ? "" : preview.trim();
    }

    private void clearCurrentResults() {
        currentResults = Collections.emptyList();
        lastSearchedQuery = "";
        adapter.setItems(null);
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
        searchButton.setEnabled(!isLoading);
        saveSearchButton.setEnabled(!isLoading);
        loadHistoryButton.setEnabled(!isLoading);
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
