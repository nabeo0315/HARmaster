package com.example.nabeo.harmaster;

import android.util.Log;

import java.math.*;
import java.util.ArrayList;

public class Viterbi {

    private static final String[] state = {"Stop", "Walking", "Running", "UpStairs", "DownStairs", "Up-Elevator", "Down-Elevator", "Up-Escalator", "Down-Escalator", "Bicycle", "Bus", "Train"};
    private String[] sigma = {"Stop", "Walking", "Running", "UpStairs", "DownStairs", "Up-Elevator", "Down-Elevator", "Up-Escalator", "Down-Escalator", "Bicycle", "Bus", "Train"};

    private static final double LOWEST_VALUE = -10000;

    private static double[][] a = {
            {0.9813, 0.15, 0.01, 0.01, 0.01, 0.001, 0.001, 0.001, 0.001, 0.001, 0.001, 0.001},//stop
            {0.13, 0.9833, 0.01, 0.01, 0.01, 0.001, 0.001, 0.001, 0.001, 0.001, 0.001, 0.001},//walkiing
            {0.1, 0.02, 0.87, 0.01, 0.01, 0, 0, 0, 0, 0, 0, 0},//running
            {0.01, 0.10, 0, 0.89, 0, 0, 0, 0, 0, 0, 0, 0},//stair
            {0.01, 0.10, 0, 0, 0.89, 0, 0, 0, 0, 0, 0, 0},//stairs
            {0.01, 0.10, 0, 0, 0, 0.89, 0, 0, 0, 0, 0, 0},//elevator
            {0.01, 0.10, 0, 0, 0, 0, 0.89, 0, 0, 0, 0, 0},//elevator
            {0.01, 0.10, 0, 0, 0, 0, 0, 0.89, 0, 0, 0, 0},//escalator
            {0.01, 0.10, 0, 0, 0, 0, 0, 0, 0.89, 0, 0, 0},//escalator
            {0.10, 0.01, 0, 0, 0, 0, 0, 0, 0, 0.89, 0, 0},//bicycle
            {0.10, 0.01, 0, 0, 0, 0, 0, 0, 0, 0, 0.89, 0},//bus
            {0.10, 0.01, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.89}//train
    };
    private static double[][] b = {
            {0.95, 0.05, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},//stop
            {0, 0.93, 0.05, 0.01, 0.01, 0, 0, 0, 0, 0, 0, 0},//walkiing
            {0, 0.01, 0.97, 0.01, 0.01, 0, 0, 0, 0, 0, 0, 0},//running
            {0, 0.01, 0.01, 0.98, 0, 0, 0, 0, 0, 0, 0, 0},//stair
            {0, 0.01, 0.01, 0, 0.98, 0, 0, 0, 0, 0, 0, 0},//stairs
            {0.01, 0, 0, 0, 0, 0.98, 0, 0.01, 0, 0, 0, 0},//elevator
            {0.01, 0, 0, 0, 0, 0, 0.98, 0, 0.01, 0, 0, 0},//elevator
            {0.01, 0, 0, 0.01, 0, 0, 0, 0.98, 0, 0, 0, 0},//escalator
            {0.01, 0, 0, 0, 0.01, 0, 0, 0, 0.98, 0, 0, 0},//escalator
            {0, 0.01, 0.01, 0.01, 0.01, 0, 0, 0, 0, 0.96, 0, 0},//bicycle
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.9, 0.1},//bus
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.1, 0.9}//train
    };
    private static double[] pi = {0.083, 0.083, 0.083, 0.083, 0.083, 0.083, 0.083, 0.083, 0.083, 0.083, 0.083, 0.083};

    public Viterbi(){
        convert2Log();
    }

    public String viterbiAlgorithm(String[] states, String[] corrected) {

        //convert string to integer
        ArrayList<Integer> list = new ArrayList<>();
        for(String s: states) list.add(getClassLabel(s));
        int[] o = new int[list.size()];
        int index = 0;
        for(Integer n:list) {
            o[index] = n;
            index++;
        }

        // 1.各状態i=1,...,Nに対して、変数の初期化を行う。
        // delta_{1}(i)=pi_{i} b_{i}(o_{1})
        // psi_{1}(i)=0
        double[][] delta = new double[ o.length ][ state.length ];
        int[][] psi = new int[ o.length ][ state.length ];
        for ( int i = 0; i < state.length; ++i )
        {
            delta[0][i] = Math.log(pi[i]) + b[i][ o[0] ];
            Log.d("viterbi.java:b[0][" + i + "]", String.valueOf(b[i][o[0]]));
            psi[0][i] = 0;
        }

        // 2.各時刻t=1,...,T-1、各状態j=1,...,Nについて、再起計算を実行。
        // delta_{t+1}=max_{i}[delta_{t}(i) a_{i}_{j}] b_{j}(o_{t+1})
        // psi_{t+1}=argmax_{i}[delta_{t}(i) a_{i}_{j}]
        for ( int t = 1; t < o.length; ++t )
        {
            for ( int j = 0; j < delta[0].length; ++j )
            {
                double maxDeltaA = LOWEST_VALUE;
                int argmaxDeltaA = 0;
                for ( int i = 0; i < delta[0].length; i++ )
                {
                    double deltaA = delta[ t - 1 ][i] + a[i][j];
                    if ( maxDeltaA < deltaA && deltaA != 0.0)
                    {
                        maxDeltaA = deltaA;
                        argmaxDeltaA = i;
                    }
                }
                delta[t][j] = maxDeltaA + b[j][ o[t] ];
                psi[t][j] = argmaxDeltaA;
            }
        }

        // 3.再起計算の終了。
        // P^{^}=max_{i} delta_{T}(i)
        // q_{T}^{^}=argmax_{i} delta_{T}(i)
        double maxDelta = -10000;
        int argmaxDelta = 0;
        for ( int i = 0; i < delta[0].length; i++ )
        {
            double tempDelta = delta[ delta.length - 1 ][i];
            if ( maxDelta < tempDelta )
            {
                maxDelta = tempDelta;
                argmaxDelta = i;
            }
        }

        // 4.バックトラックによる最適状態遷移系列の復元。
        // t=T-1,...,1に対して、次を実行する。
        // q_{t}^{^}=psi_{t+1}(q_{t}^{^}+1)
        int[] q = new int[ o.length ];

        q[ o.length - 1 ] = argmaxDelta;
        for ( int t = o.length - 1; t >= 1; --t )
        {
            q[ t - 1 ] = psi[ t ][ q[ t ] ];
        }

        StringBuilder sb = new StringBuilder();
        int n = 0;
        // 最適な系列を出力
        for ( int x : q )
        {
            sb.append(state[o[n]] + "," + state[x] + "," + corrected[n] + "\n");
            n++;
        }

        return sb.toString();
    }

    public static void convert2Log(){
        //対数変換
        for(int i = 0; i < a.length; i++) {
            for(int j = 0; j < 12; j++) {
                if(a[i][j] != 0) {
                    a[i][j] = Math.log(a[i][j]);
                }else {
                    a[i][j] = LOWEST_VALUE;
                }
            }
        }

        for(int i = 0; i < b.length; i++) {
            for(int j = 0; j < 12; j++) {
                if(b[i][j] != 0) {
                    b[i][j] = Math.log(b[i][j]);
                }else {
                    b[i][j] = LOWEST_VALUE;
                }
            }
        }
    }

    public static int getClassLabel(String state){

        if(state.equals("Stop")) return 0;
        else if(state.equals("Walking")) return 1;
        else if(state.equals("Running")) return 2;
        else if(state.equals("Upstairs")) return 3;
        else if(state.equals("Downstairs")) return 4;
        else if(state.equals("Up-Elevator")) return 5;
        else if(state.equals("Down-Elevator")) return 6;
        else if(state.equals("Up-Escalator")) return 7;
        else if(state.equals("Down-Escalator")) return 8;
        else if(state.equals("Bicycle")) return 9;
        else if(state.equals("Bus")) return 10;
        else if(state.equals("Train")) return 11;

        return 0;
    }
}
