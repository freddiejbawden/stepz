package com.specknet.orientandroid;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Double.NaN;


/**
 * Handles counting the steps the user has taken based on a series of input magnitudes
 * from the accelerometer
 *
 */
public class StepCounter{

    private double alpha;
    private double beta;
    private int k;
    private int m;
    private ArrayList<Double> step_mid_points = new ArrayList<>();
    private Double last_peak = null;
    private Double last_valley = null;
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

    /**
     *
     * @param alpha the constant for step average - modifies how far our threshold should be from
     *              the mean
     * @param beta  the constant for the time threshold, modifies how much variance we should
     *              include
     * @param k     the number of previous step averages to use when finding mean_a
     * @param m     the number of previous peaks/valleys to include  when finding the time threshold
     */
    public StepCounter(double alpha, double beta,int k, int m) {
        this.alpha = alpha;
        this.beta = beta;
        this.k = k;
        this.m = m;
    }

    /**
     *  This function finds the threshold for the step average
     *
     * @param mean_a    the step average mean
     * @param std_a     the step average standard deviation
     * @param sign      whether to add or subtract the standard deviation
     * @return          a double containing a threshold for the step average
     */
    public Double stepAverage(Double mean_a, Double std_a, int sign) {

        Log.d("STEPS", "MEANS: " + mean_a);
        Log.d("STEPS", "STD: " + std_a);


        double out = mean_a + sign*std_a/this.alpha;
        return out;

    }

    /**
     *  Returns the mean of a List<Double
     * @param marks A list of values
     * @return The mean
     */
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

    /**
     *  Generic function for finding the standard devation of a List<Double>
     *
     * @param table     A list of values
     * @param mean_a    The mean of the values
     * @return          The standard deviation
     */
    public static double sd (List<Double> table, Double mean_a)
    {
        if (table.size() == 0) {
            return 0;
        }
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

    /**
     * Determines whether step n-1 is a valley, peak or nothing
     *
     * @return Returns 1 if peak, -1 if valley, 0 o/w
     */
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
            return step_c;
        }
        if (stepAverage(mean_a, std_a, -1) == 0.0) {
            if (current<Math.min(prev,future)) {
                step_c = -1;
            }
        } else {
            if (current < Math.min(Math.min(prev, future), stepAverage(mean_a, std_a, -1))) {
                step_c = -1;
            }
        }
        return step_c;
    }

    /**
     * Find the mid point of the last_valley and last_peak
     *
     * @return  Mid point of last_valley and last_peak
     */
    public double step_midPoint_calc() {
        return (last_peak + last_valley)/2;
    }

    /**
     * Updates the time between peaks using the last M values
     *
     */
    public void updatePeakThresh() {
        ArrayList<Double> time_diff = new ArrayList<>();
        for (int i=time_between_peaks.size()-1;i>time_between_peaks.size()-this.m && i >= 1;i--) {
            time_diff.add(time_between_peaks.get(i)-time_between_peaks.get(i-1));
        }
        double mean_t = calculateAverage(time_diff);
        peak_threshold = mean_t - (sd(time_diff, mean_t)/this.beta);
    }

    /**
     * Updates the time between valleys using the last M values
     *
     */
    public void updateValleyThresh() {
        ArrayList<Double> time_diff = new ArrayList<>();
        for (int i=time_between_peaks.size()-1;i>=time_between_valleys.size()-this.m && i>=1;i--) {
            time_diff.add(time_between_valleys.get(i)-time_between_valleys.get(i-1));
        }
        double mean_t = calculateAverage(time_diff);
        valley_threshold = mean_t - (sd(time_diff, mean_t)/this.beta);
    }

    /**
     * Updates the last_peak and assosiated collections with the new values
     * @param step  the maginitude of the new peak
     * @param time  the time (ms) of the last peak (epoch time)
     */
    public void updatePeak(Double step, Double time) {
        time_between_peaks.add(time);
        last_peak = step;
        peak_idx = val_count;
    }

    /**
     * Updates the last_valley and assosiated collections with the new values
     *
     * @param step the maginitude of the new valley
     * @param time the time (ms) of the last valley (epoch time)
     */
    public void updateValley(Double step, Double time) {
        time_between_valleys.add(time);
        last_valley = step;
    }

    /**
     * Returns the step count
     * @return Step Count since creation
     */
    public int getCount() {
        return this.count;
    }

    /**
     * Feeds new data into the class and calculates if a step has occured, otherwise update
     * collections
     *
     * @param new_step the magnitude of the new step
     * @param timestamp the time (epoch ms) of the new step
     * @return the current step count
     */
    public int stepDetection(Double new_step, Double timestamp) {

        // If there has not been enough data yet, skip analysis
        step_data.add(new_step);
        if (step_data.size() < 3) {
            val_count += 1;
            return count;
        }

        // Get step N-1 and catagorise it
        Double step_being_analysed = step_data.get(step_data.size() - 2);
        int step_candidate = detectCandidate();
        Log.d("STEP", "Candidate " + step_candidate);

        //If it is a peak
        if (step_candidate == 1) {
            if (last_peak == null) {
                // If it is the first ever peak, add it to our collections and update
                step_classifications.add(1);
                time_between_peaks.add(timestamp);
                updatePeak(step_being_analysed, timestamp);
            } else if (step_classifications.get(step_classifications.size() - 1) == -1 && (timestamp - time_between_peaks.get(time_between_peaks.size() - 1)) > peak_threshold) {
                // If the last classification was a valley and there has been enough time between
                // the peaks, then update
                step_classifications.add(1);
                time_between_peaks.add(timestamp);
                step_mid_points.add(step_midPoint_calc());
            } else if ((step_classifications.get(step_classifications.size() - 1) == 1 && (timestamp - time_between_peaks.get(time_between_peaks.size() - 1)) < peak_threshold && step_being_analysed > last_peak)) {
                // If we have found a peak and the previous value was a smaller peak, and we haven't
                // passed enough time for a new one, update the peak
                updatePeak(step_being_analysed, timestamp);
            }
        } else if (step_candidate == -1) {
            if (last_valley == null) {
                // If it is the first ever valley, add it to our collections and update
                time_between_valleys.add(timestamp);
                updateValley(step_being_analysed, timestamp);
            } else if (step_classifications.get(step_classifications.size() - 1) == 1 && (timestamp - time_between_valleys.get(time_between_valleys.size() - 1)) >= valley_threshold) {
                // If the last value was a peak and there has been enough time sincet the last valley
                // update and increment the step count
                step_classifications.add(-1);
                updateValley(step_being_analysed, timestamp);
                updatePeakThresh();
                updateValleyThresh();
                count += 1;
                step_mid_points.add(step_midPoint_calc());

                double mean_a = calculateAverage(step_mid_points);
                double std_a = sd(step_mid_points, mean_a);

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
                // If the last value was a valley and it was lower than the last, and not eneough
                // time has passed since tthe last one, update the valley with the lower value
                updateValley(step_being_analysed, timestamp);
                val_count += 1;
            }
        }
        return count;
    }



}
