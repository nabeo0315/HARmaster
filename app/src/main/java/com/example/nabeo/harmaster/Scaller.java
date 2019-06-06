package com.example.nabeo.harmaster;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

public class Scaller {
    // y' = lower + (upper - lower) * ((y - min) / (max - min))
    private double upper, lower;
    private Map<Integer, Map<String, Double>> features;

    Scaller() {
        upper = 0.0; lower = 0.0;
        features = new HashMap<>();
    }

    Scaller loadRange(File rangFile) throws IOException {
        Map<Integer, Map<String, Double>> map = new HashMap<>();
        BufferedReader br = new BufferedReader(new FileReader(rangFile));
        String line;
        while ((line = br.readLine()) != null) {
            String[] strings;
            if ((strings = line.split(" ")).length == 2) { //lower and upper
                this.lower = Double.valueOf(strings[0]);
                this.upper = Double.valueOf(strings[1]);
            }
            if ((strings = line.split(" ")).length == 3) { //class min max
                Map<String, Double> min_max = new HashMap<>();
                min_max.put("min", Double.valueOf(strings[1]));
                min_max.put("max", Double.valueOf(strings[2]));
                map.put(Integer.valueOf(strings[0]), min_max);
            }
        }
        br.close();
        this.features = map;
        return this;
    }

    //一件分
    String calcScaleFromLine(String line) {
        StringBuilder sb = new StringBuilder();
        String[] strings = line.split(" ");
        sb.append(strings[0]); // write class
        for (int i = 1; i < strings.length; i++) {
            String[] ss = strings[i].split(":");
            if (calcScale(ss[0], Double.valueOf(ss[1])) == -1.0) sb.append(" " + ss[0] + ":" + (int)calcScale(ss[0], Double.valueOf(ss[1])));
            else if (calcScale(ss[0], Double.valueOf(ss[1])) != 0.0) sb.append(" " + ss[0] + ":" + calcScale(ss[0], Double.valueOf(ss[1])));
        }
        return sb.toString();
    }

    //全体
    void calcScaleFromFile(File inputDataFile, File outputScaledFile) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(inputDataFile));
        BufferedWriter bw = new BufferedWriter(new FileWriter(outputScaledFile));
        String line;
        while ((line = br.readLine()) != null) {
            String[] strings = line.split(" ");
            bw.write(strings[0]); // write class
            for (int i = 1; i < strings.length; i++) {
                String[] ss = strings[i].split(":");
                if (calcScale(ss[0], Double.valueOf(ss[1])) == -1.0) bw.write(" " + ss[0] + ":" + (int)calcScale(ss[0], Double.valueOf(ss[1])));
                else if (calcScale(ss[0], Double.valueOf(ss[1])) != 0.0) bw.write(" " + ss[0] + ":" + calcScale(ss[0], Double.valueOf(ss[1])));
            }
            bw.write(System.getProperty("line.separator"));
        }
        bw.flush();
        bw.close();
        br.close();
    }

    double calcScale(String classNum, int num) {
        return calcScale(classNum, (double)num);
    }

    double calcScale(String classNum, double num) {
        double d = lower + (upper - lower) * ((num - features.get(Integer.parseInt(classNum)).get("min")) / (features.get(Integer.parseInt(classNum)).get("max")- features.get(Integer.parseInt(classNum)).get("min")));
        BigDecimal value = new BigDecimal(d);
        MathContext mc = new MathContext(6, RoundingMode.HALF_UP);
        return value.round(mc).doubleValue();
    }
}

