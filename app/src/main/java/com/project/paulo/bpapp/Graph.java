package com.project.paulo.bpapp;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.project.paulo.bpapp.bluetooth.BluetoothChatFragment;
import com.project.paulo.bpapp.bluetooth.ChartValue;
import com.project.paulo.bpapp.common.logger.Log;
import com.project.paulo.bpapp.common.logger.LogFragment;
import com.project.paulo.bpapp.common.logger.LogWrapper;
import com.project.paulo.bpapp.common.logger.MessageOnlyLogFilter;
import com.project.paulo.bpapp.database.ChartValueDB;
import com.project.paulo.bpapp.database.DatabaseHandler;
import com.project.paulo.bpapp.featureextraction.DiastolicPressure;
import com.project.paulo.bpapp.featureextraction.SystolicPressure;
import com.project.paulo.bpapp.featureextraction.WabpJAVA;
import com.project.paulo.bpapp.mathematics.ArrayDivision;
import com.project.paulo.bpapp.mathematics.ArrayIndex;
import com.project.paulo.bpapp.mathematics.ArrayMax;
import com.project.paulo.bpapp.mathematics.ArrayMean;
import com.project.paulo.bpapp.mathematics.ArrayMin;
import com.project.paulo.bpapp.mathematics.ArraySubstract;
import com.project.paulo.bpapp.mathematics.Diff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Graph extends Fragment implements OnChartValueSelectedListener {

    public static final String TAG = "GraphFragment";

    DatabaseHandler databaseHandler;

    // Whether the Log Fragment is currently shown
    private boolean mLogShown;

    ChartValue chartValue = new ChartValue();
    BluetoothChatFragment fragment = new BluetoothChatFragment();

    // Reader parameters
    int arsr = 25; // ms

    // Feature extraction parameters
    ArrayList<Double> pap = new ArrayList<>();
    int windowLength = 20; // seconds

    // CHART CODE
    private LineChart mChart;
    protected Typeface mTfRegular;
    protected Typeface mTfLight;

    // C++ LIBRARY DECLARATION
    // Used to load the C++ library on application startup.
    static {
        System.loadLibrary("mult");
        System.loadLibrary("wabp");
    }

    public native double mult(double a, double b);
    public native void wabp(double[] abp, double[] onsets_data, int[] onsets_size);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.graph, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        getActivity().setTitle("Graph");
    }

    @Override
    public void onStart() {
        super.onStart();
        initializeLogging();
    }

    /** Set up targets to receive log data */
    public void initializeLogging() {
        // Wraps Android's native log framework.
        LogWrapper logWrapper = new LogWrapper();
        // Using Log, front-end to the logging chain, emulates android.util.log method signatures.
        Log.setLogNode(logWrapper);

        // Filter strips out everything except the message text.
//        MessageOnlyLogFilter msgFilter = new MessageOnlyLogFilter();
//        logWrapper.setNext(msgFilter);
//
//        // On screen logging via a fragment with a TextView.
//        LogFragment logFragment = (LogFragment) getActivity().getSupportFragmentManager()
//                .findFragmentById(R.id.log_fragment);
//        msgFilter.setNext(logFragment.getLogView());

        Log.i(TAG, "Ready");
        Log.i(TAG, Double.toString(mult(2.0, 4.0)));
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setHasOptionsMenu(true);

        // Reader parameter logic

        FloatingActionButton fab = (FloatingActionButton) getActivity().findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EditText activeResonatorFrequency = getActivity().findViewById(R.id.active_resonator_frequency);
                EditText referenceResonatorFrequency = getActivity().findViewById(R.id.reference_resonator_frequency);
                EditText activeResonatorSamplingRate = getActivity().findViewById(R.id.active_resonator_sampling_rate);
                EditText referenceResonatorSamplingRate = getActivity().findViewById(R.id.reference_resonator_sampling_rate);
                EditText sweeps = getActivity().findViewById(R.id.sweeps);
                EditText samples = getActivity().findViewById(R.id.samples);
                EditText averages = getActivity().findViewById(R.id.averages);
                EditText txPower = getActivity().findViewById(R.id.tx_power);
                EditText txTime = getActivity().findViewById(R.id.tx_time);
                EditText rxDelay = getActivity().findViewById(R.id.rx_delay);

                String activeResonatorFrequencyText;
                String referenceResonatorFrequencyText;
                String activeResonatorSamplingRateText;
                String referenceResonatorSamplingRateText;
                String sweepsText;
                String samplesText;
                String averagesText;
                String txPowerText;
                String txTimeText;
                String rxDelayText;

                Boolean isThereAnythingToSend = false;

                if(!activeResonatorFrequency.getText().toString().isEmpty()){
                    activeResonatorFrequencyText = activeResonatorFrequency.getText().toString();
                    isThereAnythingToSend = true;
                } else {
                    activeResonatorFrequencyText = activeResonatorFrequency.getHint().toString();
                }

                if(!referenceResonatorFrequency.getText().toString().isEmpty()){
                    referenceResonatorFrequencyText = referenceResonatorFrequency.getText().toString();
                    isThereAnythingToSend = true;
                } else {
                    referenceResonatorFrequencyText = referenceResonatorFrequency.getHint().toString();
                }

                if(!activeResonatorSamplingRate.getText().toString().isEmpty()){
                    activeResonatorSamplingRateText = activeResonatorSamplingRate.getText().toString();
                    isThereAnythingToSend = true;
                } else {
                    activeResonatorSamplingRateText = activeResonatorSamplingRate.getHint().toString();
                }

                if(!referenceResonatorSamplingRate.getText().toString().isEmpty()){
                    referenceResonatorSamplingRateText = referenceResonatorSamplingRate.getText().toString();
                    isThereAnythingToSend = true;
                } else {
                    referenceResonatorSamplingRateText = referenceResonatorSamplingRate.getHint().toString();
                }

                if(!sweeps.getText().toString().isEmpty()){
                    sweepsText = sweeps.getText().toString();
                    isThereAnythingToSend = true;
                } else {
                    sweepsText = sweeps.getHint().toString();
                }

                if(!samples.getText().toString().isEmpty()){
                    samplesText = samples.getText().toString();
                    isThereAnythingToSend = true;
                } else {
                    samplesText = samples.getHint().toString();
                }

                if(!averages.getText().toString().isEmpty()){
                    averagesText = averages.getText().toString();
                    isThereAnythingToSend = true;
                } else {
                    averagesText = averages.getHint().toString();
                }

                if(!txPower.getText().toString().isEmpty()){
                    txPowerText = txPower.getText().toString();
                    isThereAnythingToSend = true;
                } else {
                    txPowerText = txPower.getHint().toString();
                }

                if(!txTime.getText().toString().isEmpty()){
                    txTimeText = txTime.getText().toString();
                    isThereAnythingToSend = true;
                } else {
                    txTimeText = txTime.getHint().toString();
                }

                if(!rxDelay.getText().toString().isEmpty()){
                    rxDelayText = rxDelay.getText().toString();
                    isThereAnythingToSend = true;
                } else {
                    rxDelayText = rxDelay.getHint().toString();
                }

                Boolean isThereAnErrorInParams = false;

                if(Double.parseDouble(activeResonatorFrequencyText) > 920.0 ||
                        Double.parseDouble(activeResonatorFrequencyText) < 915.0 ||
                        Double.parseDouble(activeResonatorFrequencyText) > Double.parseDouble(referenceResonatorFrequencyText)) {

                    isThereAnErrorInParams = true;

                    activeResonatorFrequency.setError("Must be in range 915-920Mhz and less than reference resonator frequency.");
                }

                if(Double.parseDouble(referenceResonatorFrequencyText) > 920.0 ||
                        Double.parseDouble(referenceResonatorFrequencyText) < 915.0) {

                    isThereAnErrorInParams = true;

                    referenceResonatorFrequency.setError("Must be in range 915-920Mhz.");
                }

                if(Double.parseDouble(activeResonatorSamplingRateText) > 100 ||
                        Double.parseDouble(activeResonatorSamplingRateText) < 5) {

                    isThereAnErrorInParams = true;

                    activeResonatorSamplingRate.setError("Must be in range 5-100ms.");
                }

                if(Double.parseDouble(referenceResonatorSamplingRateText) > 7 ||
                        Double.parseDouble(referenceResonatorSamplingRateText) < 1) {

                    isThereAnErrorInParams = true;

                    referenceResonatorSamplingRate.setError("Must be in range 1-7s.");
                }

                if(Double.parseDouble(sweepsText) > 2047 ||
                        Double.parseDouble(sweepsText) < 1) {

                    isThereAnErrorInParams = true;

                    sweeps.setError("Must be in range 1-2047.");
                }

                if(Double.parseDouble(samplesText) > 1000 ||
                        Double.parseDouble(samplesText) < 64) {

                    isThereAnErrorInParams = true;

                    samples.setError("Must be in range 64-1000.");
                }

                if(Double.parseDouble(averagesText) > 999 ||
                        Double.parseDouble(averagesText) < 63 ||
                        Double.parseDouble(averagesText) > Double.parseDouble(samplesText)) {

                    isThereAnErrorInParams = true;

                    averages.setError("Must be in range 63-999 and less than samples.");
                }

                if(Double.parseDouble(txPowerText) > 1023 ||
                        Double.parseDouble(txPowerText) < 0) {

                    isThereAnErrorInParams = true;

                    txPower.setError("Must be in range 0-1023.");
                }

                if(Double.parseDouble(txTimeText) > 1023 ||
                        Double.parseDouble(txTimeText) < 0) {

                    isThereAnErrorInParams = true;

                    txTime.setError("Must be in range 0-1023.");
                }

                if(Double.parseDouble(rxDelayText) > 1023 ||
                        Double.parseDouble(rxDelayText) < 0) {

                    isThereAnErrorInParams = true;

                    rxDelay.setError("Must be in range 0-1023.");
                }

                if(isThereAnErrorInParams) {
                    Toast.makeText(getActivity(), "Please recheck parameter values.",
                            Toast.LENGTH_LONG).show();

                    return;
                }

                if(!isThereAnErrorInParams && !isThereAnythingToSend) {
                    activeResonatorFrequency.setError(null);
                    referenceResonatorFrequency.setError(null);
                    activeResonatorSamplingRate.setError(null);
                    referenceResonatorSamplingRate.setError(null);
                    sweeps.setError(null);
                    samples.setError(null);
                    averages.setError(null);
                    txPower.setError(null);
                    txTime.setError(null);
                    rxDelay.setError(null);

                    Toast.makeText(getActivity(), "Nothing to send.",
                            Toast.LENGTH_LONG).show();

                    return;
                }

                if(!isThereAnErrorInParams && isThereAnythingToSend) {
                    activeResonatorFrequency.setError(null);
                    referenceResonatorFrequency.setError(null);
                    activeResonatorSamplingRate.setError(null);
                    referenceResonatorSamplingRate.setError(null);
                    sweeps.setError(null);
                    samples.setError(null);
                    averages.setError(null);
                    txPower.setError(null);
                    txTime.setError(null);
                    rxDelay.setError(null);

                    fragment.sendMessage("arf" + activeResonatorFrequencyText +
                            "rrf" + referenceResonatorFrequencyText +
                            "ars" + activeResonatorSamplingRateText +
                            "rrs" + referenceResonatorSamplingRateText +
                            "swe" + sweepsText +
                            "sam" + samplesText +
                            "txp" + txPowerText +
                            "txt" + txTimeText +
                            "rxd" + rxDelayText);

                    Toast.makeText(getActivity(), "Parameters sent to reader.",
                            Toast.LENGTH_LONG).show();
                }
            }
        });

        databaseHandler = new DatabaseHandler(getActivity());

        // CHART CODE
        getActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        chartValue.setListener(new ChartValue.ChangeListener() {
            @Override
            public void onChange() {
                if(chartValue.getChartValue().contains(".")) {
                    Log.e(TAG, chartValue.getChartTime() + " "+ chartValue.getChartValue());

                    String x = null;
                    String y = null;

                    Pattern regex = Pattern.compile("(\\d+(?:\\.\\d+)?)");
                    Matcher matcher = regex.matcher(chartValue.getChartTime());
                    while(matcher.find()){
                        x = matcher.group(1);
                    }
                    matcher = regex.matcher(chartValue.getChartValue());
                    while(matcher.find()){
                        y = matcher.group(1);
                    }

                    ChartValueDB newData = new ChartValueDB(x, y);

                    databaseHandler.addData(newData);

                    addEntry(Float.parseFloat(x)/1000, Float.parseFloat(y));

                    Log.e(TAG, chartValue.getChartTime2());
                    Log.e(TAG, chartValue.getChartValue2());

//                    matcher = regex.matcher(chartValue.getChartTime2());
//                    while(matcher.find()){
//                        x = matcher.group(1);
//                    }
//                    matcher = regex.matcher(chartValue.getChartValue2());
//                    while(matcher.find()){
//                        y = matcher.group(1);update
//                    }

//                    addEntry(Float.parseFloat(chartValue.getChartTime2())/1000, Float.parseFloat(chartValue.getChartValue2()));

//                    matcher = regex.matcher(chartValue.getChartTime3());
//                    while(matcher.find()){
//                        x = matcher.group(1);
//                    }
//                    matcher = regex.matcher(chartValue.getChartValue3());
//                    while(matcher.find()){
//                        y = matcher.group(1);
//                    }

//                    addEntry(Float.parseFloat(chartValue.getChartTime3())/1000, Float.parseFloat(chartValue.getChartValue3()));

//                    Log.w(TAG, Arrays.toString(databaseHandler.getAllData().toArray()));

                    // TODO finish feature extraction
                    // FEATURE EXTRACTION
                    if (pap.size() < windowLength*(1000/arsr)) {
                        pap.add(Double.parseDouble(y));
                    } else {
                        // FILTERING

                        // BEAT ONSET DETECTION
                        double[] abp = new double[pap.size()];
                        for(int i = 0; i < pap.size(); i++)
                        {
                            abp[i] = pap.get(i);
                        }

//                        double[] onsets1 = {};
//                        int[] onsets_size = {};
//
//                        wabp(abp, onsets1, onsets_size);
//
//                        Log.e(TAG, "Calculated onsets");
//                        Log.e(TAG, Double.toString(onsets1[0]));

                        double[] temp = new double[abp.length];
                        System.arraycopy(abp, 0, temp, 0, abp.length);

                        long startTime = System.nanoTime();

                        int[] onsets = WabpJAVA.wabpJAVA(temp);

                        long endTime = System.nanoTime();
                        long duration = (endTime - startTime)/1000000;

                        Log.e(TAG, "Onset indexes = " + Arrays.toString(onsets));
                        Log.e(TAG, "Took in seconds: " + Long.toString(duration));

                        double[][] beats = new double[onsets.length - 1][];

                        for (int i = 0; i < onsets.length - 1; i++) {
                            beats[i] = new double[onsets[i + 1] - onsets[i]];
                            System.arraycopy(abp, onsets[i], beats[i], 0, onsets[i + 1] - onsets[i]);
                        }

                        Log.e(TAG,"");

                        // SYSTOLIC

                        double[] systolicPressures = new double[beats.length];

                        for (int i = 0; i < systolicPressures.length; i++) {
                            systolicPressures[i] = SystolicPressure.getSystolicPressure(beats[i]);
                        }

                        double systolicPressure = ArrayMean.getArrayMean(systolicPressures);
                        Log.e(TAG, "Systolic Pressure = " + Double.toString(systolicPressure));

                        // DIASTOLIC

                        double[] diastolicPressures = new double[beats.length];

                        for (int i = 0; i < diastolicPressures.length; i++) {
                            int systolicIndex = ArrayIndex.getArrayIndex(beats[i], systolicPressures[i]);
                            double[] tempBeat = new double[beats[i].length-systolicIndex];
                            System.arraycopy(beats[i], systolicIndex, tempBeat, 0, beats[i].length-systolicIndex);

                            diastolicPressures[i] = DiastolicPressure.getDiastolicPressure(tempBeat);
                        }

                        double diastolicPressure = ArrayMean.getArrayMean(diastolicPressures);
                        Log.e(TAG, "Diastolic Pressure = " + Double.toString(diastolicPressure));

                        // MEAN

                        double[] meanPressures = new double[beats.length];

                        for (int i = 0; i < meanPressures.length; i++) {
                            meanPressures[i] = (systolicPressures[i] + 2*diastolicPressures[i])/3.0;
                        }

                        double meanPressure = ArrayMean.getArrayMean(meanPressures);
                        Log.e(TAG, "Mean Pressure = " + Double.toString(meanPressure));

                        // HEART BEAT

                        double[] heartBeats = new double[beats.length];

                        for (int i = 0; i < heartBeats.length; i++) {
                            heartBeats[i] = 60.0/(beats[i].length*arsr/1000.0);
                        }

                        double heartBeat = ArrayMean.getArrayMean(heartBeats);
                        Log.e(TAG, "Heart Beat = " + Double.toString(heartBeat));

                        // DICROTIC NOTCH

                        double[] dicroticNotches = new double[beats.length];

                        for (int i = 0; i < dicroticNotches.length; i++) {
                            int systolicIndex = ArrayIndex.getArrayIndex(beats[i], systolicPressures[i]);
                            double[] tempBeat = new double[beats[i].length-systolicIndex];
                            System.arraycopy(beats[i], systolicIndex, tempBeat, 0, beats[i].length-systolicIndex);
                            int diastolicIndex = ArrayIndex.getArrayIndex(tempBeat, diastolicPressures[i]);
                            diastolicIndex += systolicIndex;

                            double[] lineFromSystoleToDiastole = new double[beats[i].length];
                            double a = (diastolicPressures[i] - systolicPressures[i])/(diastolicIndex - systolicIndex);
                            double b = systolicPressures[i] - a*systolicIndex;
                            for (int j = 0; j < lineFromSystoleToDiastole.length; j++) {
                                lineFromSystoleToDiastole[j] = a*beats[i][j] + b;
                            }

                            double[] differenceStraightLineAndBeat = ArraySubstract.getArraySubstraction(lineFromSystoleToDiastole, beats[i]);
                            double minDifference = ArrayMin.getArrayMin(differenceStraightLineAndBeat);
                            int minDifferenceIndex = ArrayIndex.getArrayIndex(differenceStraightLineAndBeat, minDifference);
                            dicroticNotches[i] = beats[i][minDifferenceIndex];

                        }

                        double dicroticNotch = ArrayMean.getArrayMean(dicroticNotches);
                        Log.e(TAG, "Dicrotic Notch = " + Double.toString(dicroticNotch));

                        // DICROTIC PEAK

                        double[] dicroticPeaks = new double[beats.length];

                        for (int i = 0; i < dicroticPeaks.length; i++) {
                            int dicroticNotchIndex = ArrayIndex.getArrayIndex(beats[i], dicroticNotches[i]);
                            double[] tempBeat = new double[beats[i].length-dicroticNotchIndex];
                            System.arraycopy(beats[i], dicroticNotchIndex, tempBeat, 0, beats[i].length-dicroticNotchIndex);
                            dicroticPeaks[i] = ArrayMax.getArrayMax(tempBeat);
                        }

                        double dicroticPeak = ArrayMean.getArrayMean(dicroticPeaks);
                        Log.e(TAG, "Dicrotic Peak = " + Double.toString(dicroticPeak));

                        // MAX DP/DT

                        double[] maxPressureChangeRates = new double[beats.length];

                        for (int i = 0; i < maxPressureChangeRates.length; i++) {
                            double[] pressureChangeRate = Diff.diff(beats[i]);
                            pressureChangeRate = ArrayDivision.getRealArrayScalarDiv(pressureChangeRate, arsr/1000.0);
                            maxPressureChangeRates[i] = ArrayMax.getArrayMax(pressureChangeRate);
                        }

                        double maxPressureChangeRate = ArrayMean.getArrayMean(maxPressureChangeRates);
                        Log.e(TAG, "Max dp/dt = " + Double.toString(maxPressureChangeRate));

                        // UPDATE UI

                        TextView systolicTextView = (TextView) getActivity().findViewById(R.id.systolic);

                        int randomSystolic = (int) (Math.random() * (40 - 20)) + 20;

                        systolicTextView.setText(Integer.toString(randomSystolic));

                        pap.clear();
                    }
                }
            }
        });

        fragment.setChartValue(chartValue);

        if (savedInstanceState == null) {
            FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.sample_content_fragment, fragment);
            transaction.commit();
        }

        // CHART CODE
        mChart = (LineChart) getActivity().findViewById(R.id.chart1);
        mChart.setOnChartValueSelectedListener(this);

        // set description text
        mChart.getDescription().setEnabled(true);
        Description description = new Description();
        description.setText("Time (s)");
        mChart.setDescription(description);

        // enable touch gestures
        mChart.setTouchEnabled(true);

        // enable scaling and dragging
        mChart.setDragEnabled(true);
        mChart.setScaleEnabled(true);
        mChart.setDrawGridBackground(false);

        // if disabled, scaling can be done on x- and y-axis separately
        mChart.setPinchZoom(true);

        // set an alternative background color
        mChart.setBackgroundColor(Color.LTGRAY);

        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);

        // add empty data
        mChart.setData(data);

        // get the legend (only possible after setting data)
        Legend l = mChart.getLegend();

        // modify the legend ...
        l.setForm(Legend.LegendForm.LINE);
        l.setTypeface(mTfLight);
        l.setTextColor(Color.WHITE);

        XAxis xl = mChart.getXAxis();
        mChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        xl.setTypeface(mTfLight);
        xl.setTextColor(Color.WHITE);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setEnabled(true);

        YAxis leftAxis = mChart.getAxisLeft();
        leftAxis.setTypeface(mTfLight);
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setAxisMaximum(60f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawGridLines(true);

        YAxis rightAxis = mChart.getAxisRight();
        rightAxis.setEnabled(false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.actionClear: {
                mChart.clearValues();
                Toast.makeText(getActivity(), "Chart cleared", Toast.LENGTH_SHORT).show();
                return true;
//                break;
            }
            case R.id.dataSend: {
                fragment.sendMessage("parameter1:" + Math.random() * 10 + ",parameter2:" + Math.random() * 10 + ",parameter3:" + Math.random() * 10 + "／n");
                return true;
//                break;
            }
        }

        return false;
//        return super.onOptionsItemSelected(item);
    }

    // CHART CODE
    private void addEntry(float x, float y) {

        LineData data = mChart.getData();

        if (data != null) {

            ILineDataSet set = data.getDataSetByIndex(0);
            // set.addEntry(...); // can be called as well

            if (set == null) {
                set = createSet();
                data.addDataSet(set);
            }

            data.addEntry(new Entry(x, y), 0);

            data.notifyDataChanged();

            // let the chart know it's data has changed
            mChart.notifyDataSetChanged();

            // limit the number of visible entries
            mChart.setVisibleXRangeMaximum(5);
            // mChart.setVisibleYRange(30, AxisDependency.LEFT);

            // move to the latest entry
            mChart.moveViewToX(data.getEntryCount());

            // adjust y axis
            // mChart.getAxisLeft().resetAxisMinimum();
            mChart.getAxisLeft().resetAxisMaximum();

            // this automatically refreshes the chart (calls invalidate())
            // mChart.moveViewTo(data.getXValCount()-7, 55f,
            // AxisDependency.LEFT);
        }
    }

    private LineDataSet createSet() {

        LineDataSet set = new LineDataSet(null, "Pressure (mmHg)");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(ColorTemplate.getHoloBlue());
        set.setCircleColor(Color.WHITE);
        set.setLineWidth(2f);
        set.setCircleRadius(4f);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        set.setDrawCircles(false);
        return set;
    }

    @Override
    public void onValueSelected(Entry e, Highlight h) {
        android.util.Log.i("Entry selected", e.toString());
    }

    @Override
    public void onNothingSelected() {
        android.util.Log.i("Nothing selected", "Nothing selected.");
    }
}
