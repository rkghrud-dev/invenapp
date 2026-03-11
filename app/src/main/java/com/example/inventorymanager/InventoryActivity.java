package com.example.inventorymanager;

import android.content.Intent;
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

import java.io.IOException;
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
    private SearchResultAdapter adapter;

    private View mainRoot;
    private EditText searchInput;
    private TextView configSummary;
    private TextView statusText;
    private TextView emptyView;
    private ProgressBar progressBar;
    private Button searchButton;
    private Button optionsButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);

        settingsStore = new SettingsStore(this);
        sheetsRepository = new SheetsRepository();
        googleProfileRepository = new GoogleProfileRepository();
        googleOAuthRepository = new GoogleOAuthRepository(this);

        mainRoot = findViewById(R.id.main_root);
        searchInput = findViewById(R.id.search_input);
        configSummary = findViewById(R.id.config_summary);
        statusText = findViewById(R.id.status_text);
        emptyView = findViewById(R.id.empty_view);
        progressBar = findViewById(R.id.progress_bar);
        searchButton = findViewById(R.id.search_button);
        optionsButton = findViewById(R.id.options_button);
        ListView resultsList = findViewById(R.id.results_list);

        adapter = new SearchResultAdapter(this);
        resultsList.setAdapter(adapter);
        resultsList.setEmptyView(emptyView);

        searchButton.setOnClickListener(view -> runSearch());
        optionsButton.setOnClickListener(view -> startActivity(new Intent(this, SettingsActivity.class)));
        searchInput.setOnEditorActionListener(this::handleEditorAction);

        updateConfigSummary();
        showIdleStatus();
        mainRoot.post(this::resetSearchFocus);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateConfigSummary();
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

    private void updateConfigSummary() {
        configSummary.setText(settingsStore.load().toSummary());
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
            showStatus(getString(R.string.status_enter_query));
            adapter.setItems(null);
            return;
        }

        if (!settingsStore.load().isReady()) {
            showStatus(getString(R.string.status_sheet_not_ready));
            adapter.setItems(null);
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
                    updateConfigSummary();
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
                    adapter.setItems(null);
                    showStatus(safeMessage(exception));
                    setLoading(false);
                });
            }
        });
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
        optionsButton.setEnabled(!isLoading);
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