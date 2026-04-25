package com.taskmateaditya.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.taskmateaditya.R;
import com.taskmateaditya.data.Task;
import com.taskmateaditya.data.TaskViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.firebase.auth.FirebaseAuth;

public class DashboardActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private TextView tvWeeklyDone, tvProductivityScore;
    private BarChart barChart;
    private PieChart pieChart;
    private TaskViewModel taskViewModel;
    private FirebaseAuth.AuthStateListener authStateListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        btnBack = findViewById(R.id.btnBack);
        tvWeeklyDone = findViewById(R.id.tvWeeklyDone);
        tvProductivityScore = findViewById(R.id.tvProductivityScore);
        barChart = findViewById(R.id.barChart);
        pieChart = findViewById(R.id.pieChart);

        btnBack.setOnClickListener(v -> finish());

        setupCharts();

        taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);
        taskViewModel.getSearchResults().observe(this, this::processTaskData);
    }

    private void setupCharts() {
        // BarChart Setup
        barChart.getDescription().setEnabled(false);
        barChart.getLegend().setEnabled(false);
        barChart.setDrawGridBackground(false);
        barChart.getAxisRight().setEnabled(false);
        barChart.getAxisLeft().setDrawGridLines(false);
        barChart.getXAxis().setDrawGridLines(false);
        barChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        barChart.getXAxis().setGranularity(1f);

        // PieChart Setup
        pieChart.getDescription().setEnabled(false);
        pieChart.getLegend().setEnabled(true);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.TRANSPARENT);
        pieChart.setEntryLabelColor(Color.BLACK);
    }

    private void processTaskData(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) return;

        int totalTasks = tasks.size();
        int totalDone = 0;
        
        Map<String, Integer> categoryCount = new HashMap<>();
        
        // Setup 7 days array
        String[] days = new String[7];
        int[] donePerDay = new int[7];
        
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM", Locale.getDefault());
        for (int i = 6; i >= 0; i--) {
            days[i] = sdf.format(cal.getTime());
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }

        long sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
        int weeklyDoneCount = 0;

        for (Task task : tasks) {
            boolean isDone = task.isCompleted();
            if (isDone) totalDone++;

            // Category Distribution
            String cat = task.getCategory() != null && !task.getCategory().isEmpty() ? task.getCategory() : "Lainnya";
            categoryCount.put(cat, categoryCount.getOrDefault(cat, 0) + 1);

            // Bar chart logic (menggunakan completedAt, fallback ke createdAt untuk data lama)
            long timeRef = task.getCompletedAt() > 0 ? task.getCompletedAt() : task.getCreatedAt();
            if (isDone && timeRef > sevenDaysAgo) {
                weeklyDoneCount++;
                Calendar taskCal = Calendar.getInstance();
                taskCal.setTimeInMillis(timeRef);
                String taskDay = sdf.format(taskCal.getTime());
                
                for (int i = 0; i < 7; i++) {
                    if (days[i].equals(taskDay)) {
                        donePerDay[i]++;
                        break;
                    }
                }
            }
        }

        // Update UI Text
        tvWeeklyDone.setText(String.valueOf(weeklyDoneCount));
        int score = totalTasks > 0 ? (totalDone * 100 / totalTasks) : 0;
        tvProductivityScore.setText(score + "%");

        // Populate BarChart
        List<BarEntry> barEntries = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            barEntries.add(new BarEntry(i, donePerDay[i]));
        }
        
        if (!barEntries.isEmpty()) {
            BarDataSet barDataSet = new BarDataSet(barEntries, "Tugas Selesai");
            barDataSet.setColor(Color.parseColor("#00C853"));
            barDataSet.setValueTextSize(10f);
            barDataSet.setValueTextColor(Color.GRAY);
            
            BarData barData = new BarData(barDataSet);
            barData.setBarWidth(0.5f);
            barChart.setData(barData);
            barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(days));
            barChart.invalidate();
            barChart.animateY(1000);
        }

        // Populate PieChart
        List<PieEntry> pieEntries = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : categoryCount.entrySet()) {
            pieEntries.add(new PieEntry(entry.getValue(), entry.getKey()));
        }

        if (!pieEntries.isEmpty()) {
            PieDataSet pieDataSet = new PieDataSet(pieEntries, "");
            pieDataSet.setColors(ColorTemplate.MATERIAL_COLORS);
            pieDataSet.setValueTextSize(12f);
            pieDataSet.setValueTextColor(Color.WHITE);
            
            PieData pieData = new PieData(pieDataSet);
            pieChart.setData(pieData);
            pieChart.invalidate();
            pieChart.animateY(1000);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        authStateListener = auth -> {
            if (auth.getCurrentUser() != null) {
                if (taskViewModel != null) {
                    taskViewModel.syncData();
                    taskViewModel.refreshTasksFromCloud();
                }
            }
        };
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (authStateListener != null) {
            FirebaseAuth.getInstance().removeAuthStateListener(authStateListener);
        }
        if (taskViewModel != null) {
            taskViewModel.stopSync();
        }
    }
}
