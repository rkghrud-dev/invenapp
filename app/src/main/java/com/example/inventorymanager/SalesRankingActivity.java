package com.example.inventorymanager;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SalesRankingActivity extends ThemedActivity {
    private static final String MODE_TODAY = "today";
    private static final String MODE_DATE = "date";
    private static final String MODE_MONTH = "month";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
    private final SimpleDateFormat monthFormat = new SimpleDateFormat("yyyy-MM", Locale.KOREA);

    private SettingsStore settingsStore;
    private GoogleOAuthRepository googleOAuthRepository;
    private SalesRankingRepository salesRankingRepository;
    private SalesRankingAdapter adapter;

    private EditText dateInput;
    private EditText monthInput;
    private TextView querySummary;
    private TextView statusText;
    private TextView emptyView;
    private ProgressBar progressBar;
    private Button top10Button;
    private Button top30Button;
    private Button top50Button;
    private Button todayButton;
    private Button dateSearchButton;
    private Button monthSearchButton;

    private String currentMode = MODE_TODAY;
    private String currentTarget = todayText();
    private int currentLimit = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sales_ranking);

        dayFormat.setLenient(false);
        monthFormat.setLenient(false);

        settingsStore = new SettingsStore(this);
        googleOAuthRepository = new GoogleOAuthRepository(this);
        salesRankingRepository = new SalesRankingRepository();

        dateInput = findViewById(R.id.date_input);
        monthInput = findViewById(R.id.month_input);
        querySummary = findViewById(R.id.query_summary);
        statusText = findViewById(R.id.status_text);
        emptyView = findViewById(R.id.empty_view);
        progressBar = findViewById(R.id.progress_bar);
        top10Button = findViewById(R.id.top10_button);
        top30Button = findViewById(R.id.top30_button);
        top50Button = findViewById(R.id.top50_button);
        todayButton = findViewById(R.id.today_button);
        dateSearchButton = findViewById(R.id.date_search_button);
        monthSearchButton = findViewById(R.id.month_search_button);
        ListView resultsList = findViewById(R.id.results_list);

        adapter = new SalesRankingAdapter(this);
        resultsList.setAdapter(adapter);
        resultsList.setEmptyView(emptyView);

        dateInput.setText(todayText());
        monthInput.setText(todayMonthText());

        top10Button.setOnClickListener(v -> selectLimit(10));
        top30Button.setOnClickListener(v -> selectLimit(30));
        top50Button.setOnClickListener(v -> selectLimit(50));
        todayButton.setOnClickListener(v -> loadTodayRanking());
        dateSearchButton.setOnClickListener(v -> loadDateRanking());
        monthSearchButton.setOnClickListener(v -> loadMonthRanking());

        updateLimitButtons();
        updateQuerySummary();
        runRankingQuery();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void selectLimit(int limit) {
        if (currentLimit == limit) {
            return;
        }
        currentLimit = limit;
        updateLimitButtons();
        updateQuerySummary();
        runRankingQuery();
    }

    private void loadTodayRanking() {
        currentMode = MODE_TODAY;
        currentTarget = todayText();
        dateInput.setText(currentTarget);
        monthInput.setText(todayMonthText());
        updateQuerySummary();
        runRankingQuery();
    }

    private void loadDateRanking() {
        String value = dateInput.getText().toString().trim();
        if (!isValidDate(value)) {
            showStatus(getString(R.string.status_ranking_need_date));
            return;
        }
        currentMode = MODE_DATE;
        currentTarget = value;
        updateQuerySummary();
        runRankingQuery();
    }

    private void loadMonthRanking() {
        String value = monthInput.getText().toString().trim();
        if (!isValidMonth(value)) {
            showStatus(getString(R.string.status_ranking_need_month));
            return;
        }
        currentMode = MODE_MONTH;
        currentTarget = value;
        updateQuerySummary();
        runRankingQuery();
    }

    private void runRankingQuery() {
        setLoading(true);
        showStatus(getString(R.string.status_ranking_loading));

        final String mode = currentMode;
        final String target = currentTarget;
        final int limit = currentLimit;
        final String rankingSpreadsheetId = settingsStore.load().rankingSpreadsheetId;

        executor.execute(() -> {
            try {
                GoogleOAuthRepository.AuthSession session = googleOAuthRepository.ensureAccessToken();

                List<SalesRankingItem> results;
                try {
                    results = loadResults(rankingSpreadsheetId, mode, target, limit, session.getAccessToken());
                } catch (IOException firstException) {
                    if (!isAuthExpiredMessage(firstException.getMessage())) {
                        throw firstException;
                    }
                    GoogleOAuthRepository.AuthSession refreshedSession = googleOAuthRepository.refreshAccessToken();
                    results = loadResults(rankingSpreadsheetId, mode, target, limit, refreshedSession.getAccessToken());
                }

                List<SalesRankingItem> finalResults = results;
                mainHandler.post(() -> {
                    adapter.setItems(finalResults);
                    updateQuerySummary();
                    if (finalResults.isEmpty()) {
                        showStatus(getString(R.string.status_ranking_no_results));
                    } else {
                        showStatus(getString(R.string.status_ranking_result_count, finalResults.size()));
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

    private List<SalesRankingItem> loadResults(String rankingSpreadsheetId, String mode, String target, int limit, String accessToken) throws IOException {
        if (MODE_MONTH.equals(mode)) {
            return salesRankingRepository.loadByMonth(rankingSpreadsheetId, target, limit, accessToken);
        }
        return salesRankingRepository.loadByDate(rankingSpreadsheetId, target, limit, accessToken);
    }

    private void updateLimitButtons() {
        styleLimitButton(top10Button, currentLimit == 10);
        styleLimitButton(top30Button, currentLimit == 30);
        styleLimitButton(top50Button, currentLimit == 50);
    }

    private void styleLimitButton(Button button, boolean selected) {
        if (selected) {
            button.setBackgroundResource(R.drawable.bg_button_primary);
            button.setTextColor(getColor(android.R.color.white));
            return;
        }
        button.setBackgroundResource(R.drawable.bg_button_secondary);
        button.setTextColor(resolveThemeColor(R.attr.paletteTextPrimary));
    }

    private void updateQuerySummary() {
        if (MODE_MONTH.equals(currentMode)) {
            querySummary.setText(getString(R.string.status_ranking_month_summary, currentTarget, currentLimit));
            return;
        }
        if (MODE_DATE.equals(currentMode)) {
            querySummary.setText(getString(R.string.status_ranking_date_summary, currentTarget, currentLimit));
            return;
        }
        querySummary.setText(getString(R.string.status_ranking_today_summary, currentLimit));
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
        progressBar.setVisibility(isLoading ? android.view.View.VISIBLE : android.view.View.GONE);
        todayButton.setEnabled(!isLoading);
        dateSearchButton.setEnabled(!isLoading);
        monthSearchButton.setEnabled(!isLoading);
        top10Button.setEnabled(!isLoading);
        top30Button.setEnabled(!isLoading);
        top50Button.setEnabled(!isLoading);
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

    private boolean isValidDate(String value) {
        return parse(dayFormat, value) != null;
    }

    private boolean isValidMonth(String value) {
        return parse(monthFormat, value) != null;
    }

    private Date parse(SimpleDateFormat format, String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        try {
            return format.parse(value);
        } catch (ParseException ignored) {
            return null;
        }
    }

    private String todayText() {
        return dayFormat.format(new Date());
    }

    private String todayMonthText() {
        return monthFormat.format(new Date());
    }
}