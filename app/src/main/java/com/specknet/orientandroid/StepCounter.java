package com.specknet.orientandroid;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Double.NaN;

public class StepCounter{

    private double alpha;
    private double beta;
    private int k;
    private int t;

    private ArrayList<Double> step_mid_points = new ArrayList<>();
    private Double last_peak = 0.0;
    private Double last_valley = 0.0;
    private ArrayList<Double> time_between_peaks = new ArrayList<>();
    private Double peak_threshold = 0.0;
    private ArrayList<Double> time_between_valleys = new ArrayList<>();
    private Double valley_threshold = 0.0;
    private ArrayList<Double> step_data = new ArrayList<>();
    private ArrayList<Integer> step_classifications = new ArrayList<>();
    private int count = 0;
    private long init_time = System.currentTimeMillis();
    private ArrayList<ArrayList<Double>> peaks = new ArrayList<>();
    private Integer val_count = 0;
    private Integer peak_idx = 0;
    private ArrayList<Double> step_avg_arr = new ArrayList<>();
    private ArrayList<ArrayList<Double>> valleys = new ArrayList<>();







    public StepCounter(double alpha, double beta,int k, int t) {
        this.alpha = alpha;
        this.beta = beta;
        this.k = k;
        this.t = t;
    }

    public Double stepAverage(Double mean_a, Double std_a, int sign) {
        Log.d("STEPS", "MEANS: " + mean_a);
        Log.d("STEPS", "STD: " + std_a);


        double out = mean_a + sign*std_a;
        if (out == NaN) return 0.0;
        return out;

    }

    public double calculateAverage(List<Double> marks) {
        Log.d("STEPS", "Marks: " + marks);
        double sum = 0;
        if(!marks.isEmpty()) {
            for (Double mark : marks) {
                sum += mark;
            }
            return sum / marks.size();
        }
        return sum;
    }

    public static double sd (List<Double> table, Double mean_a)
    {
        // Step 1:
        double temp = 0;

        for (int i = 0; i < table.size(); i++)
        {
            Double val = table.get(i);

            // Step 2:
            double squrDiffToMean = Math.pow(val - mean_a, 2);

            // Step 3:
            temp += squrDiffToMean;
        }

        // Step 4:
        double meanOfDiffs = (double) temp / (double) (table.size());

        // Step 5:
        return Math.sqrt(meanOfDiffs);
    }

    public int detectCandidate() {
        int step_c = 0;

        Double prev = step_data.get(step_data.size()-3);
        Double current = step_data.get(step_data.size()-2);
        Double future = step_data.get(step_data.size()-1);
        Log.d("STEP", ""+step_mid_points);
        double mean_a = calculateAverage(step_mid_points);
        double std_a = sd( step_mid_points, mean_a);
        Log.d("STEP", "Step Avg: "+stepAverage(mean_a, std_a, 1));
        Log.d("STEP" , "Max: " + Math.max(Math.max(prev,future),stepAverage(mean_a, std_a, 1)));
        Log.d("STEP" , "Min: " + Math.min(Math.min(prev,future),stepAverage(mean_a, std_a, -1)));

        if (current>Math.max(Math.max(prev,future),stepAverage(mean_a, std_a, 1))) {
            step_c = 1;
        } else if (current<Math.min(Math.min(prev,future),stepAverage(mean_a, std_a, -1))) {
            step_c = -1;
        }
        return step_c;
    }

    public double step_midPoint_calc() {
        return (last_peak + last_valley)/2;
    }

    public void updatePeakThresh() {
        ArrayList<Double> time_diff = new ArrayList<>();
        for (int i=1;i<time_between_peaks.size();i++) {
            time_diff.add(time_between_peaks.get(i)-time_between_peaks.get(i-1));
        }
        double mean_t = calculateAverage(time_diff);
        peak_threshold = mean_t - (sd(time_diff, mean_t)/this.beta);
    }

    public void updateValleyThresh() {
        ArrayList<Double> time_diff = new ArrayList<>();
        for (int i=1;i<time_between_valleys.size();i++) {
            time_diff.add(time_between_valleys.get(i)-time_between_valleys.get(i-1));
        }
        double mean_t = calculateAverage(time_diff);
        valley_threshold = mean_t - (sd(time_diff, mean_t)/this.beta);
    }

    public void updatePeak(Double step, Double time) {
        time_between_peaks.add(time);
        last_peak = step;
        peak_idx = val_count;
    }

    public void updateValley(Double step, Double time) {
        time_between_valleys.add(time);
        last_valley = step;
    }

    public int getCount() {
        return this.count;
    }
    public int stepDetection(Double new_step, Double timestamp) {
        step_data.add(new_step);
        if (step_data.size() < 3) {
            val_count += 1;
            return count;
        }
        Double step_being_analysed = step_data.get(step_data.size() - 2);
        int step_candidate = detectCandidate();
        Log.d("STEP", "Candidate " + step_candidate);
        if (step_candidate == 1) {
            if (last_peak == null) {
                step_classifications.add(1);
                time_between_peaks.add(timestamp);
                updatePeak(step_being_analysed, timestamp);
            } else if (step_classifications.get(step_classifications.size() - 1) == -1 && (timestamp - time_between_peaks.get(time_between_peaks.size() - 1)) > peak_threshold) {
                step_classifications.add(1);
                time_between_peaks.add(timestamp);
                step_mid_points.add(step_midPoint_calc());
            } else if ((step_classifications.get(step_classifications.size() - 1) == 1 && (timestamp - time_between_peaks.get(time_between_peaks.size() - 1)) < peak_threshold && step_being_analysed > last_peak)) {
                updatePeak(step_being_analysed, timestamp);

            }
        } else if (step_candidate == -1) {
            if (last_valley == null) {
                time_between_valleys.add(timestamp);
                updateValley(step_being_analysed, timestamp);
            } else if (step_classifications.get(step_classifications.size() - 1) == 1 && (timestamp - time_between_valleys.get(time_between_valleys.size() - 1)) >= valley_threshold) {
                step_classifications.add(-1);
                updateValley(step_being_analysed, timestamp);
                updatePeakThresh();
                updateValleyThresh();
                count += 1;
                step_mid_points.add(step_midPoint_calc());
                int k_adjust = step_mid_points.size() - this.k;
                double mean_a = calculateAverage(step_mid_points.subList(k_adjust, step_mid_points.size()));
                double std_a = sd(step_mid_points.subList(k_adjust, step_mid_points.size()), mean_a);

                step_avg_arr.add(stepAverage(mean_a, std_a, 1));

                ArrayList<Double> temp  = new ArrayList<>();
                temp.add(Double.valueOf(peak_idx));
                temp.add(last_peak);
                peaks.add(temp);

                ArrayList<Double> val  = new ArrayList<>();
                val.add(Double.valueOf(val_count));
                val.add(step_being_analysed);
                valleys.add(val);

            } else if ((step_classifications.get(step_classifications.size() - 1) == -1 && (timestamp - time_between_peaks.get(time_between_peaks.size() - 1)) <= valley_threshold && step_being_analysed < last_valley)) {
                updateValley(step_being_analysed, timestamp);
                val_count += 1;
            }
        }
        return count;
    }



}
