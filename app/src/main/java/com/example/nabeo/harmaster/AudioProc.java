package com.example.nabeo.harmaster;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.FileObserver;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.nabeo.harmaster.MFCC.FFT;
import com.example.nabeo.harmaster.MFCC.MFCC;
import com.example.nabeo.harmaster.MFCC.Window;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;

import umich.cse.yctung.androidlibsvm.LibSVM;

public class AudioProc {
    private static int RECORDER_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION;
    private static int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static int RECORDER_SAMPLERATE = 8000;

    private static int FFT_SIZE = 32768;//8192;
    private static int MFCCS_VALUE = 12;
    private static int MEL_BANDS = 20;
    private static double[] FREQ_BANDEDGES = {50,250,500,1000,2000};

    private Thread recordingThread = null;
    private int bufferSize = 0;
    private int bufferSamples = 0;
    private static int[] freqBandIdx = null;

    private FFT featureFFT = null;
    private MFCC featureMFCC = null;
    private Window featureWin = null;

    private AudioRecord audioRecorder = null;

    public double prevSecs = 0;
    public double[] featureBuffer = null;

    public final static String ROOT_DIR = Environment.getExternalStorageDirectory().toString();
    private final static String MANNERMODE_PATH = ROOT_DIR +"/MannerMode";
    private final static String OUTPUT_MFCC = MANNERMODE_PATH + "/output_mfcc.txt";
    private final static String MODEL_AUDIO = MANNERMODE_PATH + "/model_audio2.model";

    private File file;
    private FileObserver fileObserver, dataObserver;

    private LibSVM libsvm;
    private Scaller scaller;

    private final static String SCALE_PATH = MANNERMODE_PATH + "/scale_sound.txt";

    private String mState = "";
    private boolean recordFlag;

    AudioProc(){

    }

    public void startRecording(){
        bufferSize = AudioRecord.getMinBufferSize(
                RECORDER_SAMPLERATE,
                RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING);
        //bufferSize = Math.max(bufferSize, RECORDER_SAMPLERATE*2);
        bufferSize = 65536;
        bufferSamples = bufferSize/2;

        featureFFT = new FFT(FFT_SIZE);
        featureWin = new Window(bufferSamples);
        featureMFCC = new MFCC(FFT_SIZE, MFCCS_VALUE, MEL_BANDS, RECORDER_SAMPLERATE);

        recordFlag = true;

        file = new File(MANNERMODE_PATH + "/" + "mfccvalue.txt");
        libsvm = new LibSVM();

        try {
            scaller = new Scaller().loadRange(new File(SCALE_PATH));
        } catch (IOException e) {
            e.printStackTrace();
        }

        dataObserver = new FileObserver(MANNERMODE_PATH + "/" + "mfccvalue.txt") {
            @Override
            public void onEvent(int event, @Nullable String path) {
                if (event == FileObserver.CLOSE_WRITE) {
                    libsvm.predict(MANNERMODE_PATH + "/" + "mfccvalue.txt" + " " + MODEL_AUDIO + " " + OUTPUT_MFCC);
                }
            }
        };

        fileObserver = new FileObserver(OUTPUT_MFCC) {
            @Override
            public void onEvent(int event, @Nullable String path) {
                if(event == FileObserver.CLOSE_WRITE){
                    try {
                        BufferedReader bufferedReader = new BufferedReader(new FileReader(OUTPUT_MFCC));
                        String str = bufferedReader.readLine();
                        Log.d("str", str);
                        if(str.equals("1")){
                            mState = "Train";
                        } else if(str.equals("2")){
                            mState = "unknown";
                        } else if(str.equals("3")){
                            mState = "Bus";
                        }
                        bufferedReader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        freqBandIdx = new int[FREQ_BANDEDGES.length];
        for (int i = 0; i < FREQ_BANDEDGES.length; i ++)
        {
            freqBandIdx[i] = Math.round((float)FREQ_BANDEDGES[i]*((float)FFT_SIZE/(float)RECORDER_SAMPLERATE));
            //writeLogTextLine("Frequency band edge " + i + ": " + Integer.toString(freqBandIdx[i]));
        }

        audioRecorder = new AudioRecord(
                RECORDER_SOURCE,
                RECORDER_SAMPLERATE,
                RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING,
                bufferSize);
        prevSecs = (double)System.currentTimeMillis()/1000.0d;
        recordingThread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                handleAudioStream();
            }
        }, "AudioRecorder Thread");

        audioRecorder.startRecording();
        recordingThread.start();
        dataObserver.startWatching();
        fileObserver.startWatching();
    }

    private void handleAudioStream()
    {
        short data16bit[] = new short[bufferSamples];
        byte data8bit[] = new byte[bufferSize];
        double fftBufferR[] = new double[FFT_SIZE];
        double fftBufferI[] = new double[FFT_SIZE];
        double featureCepstrum[] = new double[MFCCS_VALUE];
        StringBuilder sb;

        int readAudioSamples = 0;


        while(recordFlag) {
            double currentSecs = (double)(System.currentTimeMillis())/1000.0d;
            double diffSecs = currentSecs - prevSecs;
            prevSecs = currentSecs;
            readAudioSamples = audioRecorder.read(data16bit, 0, bufferSamples);
            sb = new StringBuilder();
            Log.d("status","Read " + readAudioSamples + " samples " + diffSecs);

            if (readAudioSamples > 0) {
                double fN = (double) readAudioSamples;

                // Convert shorts to 8-bit bytes for raw audio output
                for (int i = 0; i < bufferSamples; i++) {
                    data8bit[i * 2] = (byte) data16bit[i];
                    data8bit[i * 2 + 1] = (byte) (data16bit[i] >> 8);
                }

                // L1-norm
                double accum = 0;
                for (int i = 0; i < readAudioSamples; i++) {
                    accum += Math.abs((double) data16bit[i]);
                }

                // L2-norm
                accum = 0;
                for (int i = 0; i < readAudioSamples; i++) {
                    accum += (double) data16bit[i] * (double) data16bit[i];
                }

                // Linf-norm
                accum = 0;
                for (int i = 0; i < readAudioSamples; i++) {
                    accum = Math.max(Math.abs((double) data16bit[i]), accum);
                }

                // Frequency analysis
                Arrays.fill(fftBufferR, 0);
                Arrays.fill(fftBufferI, 0);

                // Convert audio buffer to doubles
                for (int i = 0; i < readAudioSamples; i++) {
                    fftBufferR[i] = data16bit[i];
                }

                // In-place windowing
                featureWin.applyWindow(fftBufferR);

                // In-place FFT
                featureFFT.fft(fftBufferR, fftBufferI);

                // Get PSD across frequency band ranges
                double[] psdAcrossFrequencyBands = new double[FREQ_BANDEDGES.length - 1];
                for (int b = 0; b < (FREQ_BANDEDGES.length - 1); b++) {
                    int j = freqBandIdx[b];
                    int k = freqBandIdx[b + 1];
                    accum = 0;
                    for (int h = j; h < k; h++) {
                        accum += fftBufferR[h] * fftBufferR[h] + fftBufferI[h] * fftBufferI[h];
                    }
                    psdAcrossFrequencyBands[b] = accum / ((double) (k - j));
                }

                // Get MFCCs
                featureCepstrum = featureMFCC.cepstrum(fftBufferR, fftBufferI);

                sb.append("0 ");
                // Write out features
                for (int i = 0; i < MFCCS_VALUE; i++) {
                    sb.append(i+1 + ":" + featureCepstrum[i] + " ");
                }

                String str_scaled = scaller.calcScaleFromLine(sb.toString());//scaled data

                try {
                    BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, false), "UTF-8"));
                    bufferedWriter.write(str_scaled);
                    //bufferedWriter.write("\n");
                    bufferedWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                Log.v("f", sb.toString());
                Log.v("sample", String.valueOf(readAudioSamples));
            }
        }
    }

    public String getState(){
        return this.mState;
    }

    public void stop(){
        recordFlag = false;
        fileObserver.stopWatching();
        dataObserver.stopWatching();
        audioRecorder.stop();
        recordingThread.interrupt();
    }
}
