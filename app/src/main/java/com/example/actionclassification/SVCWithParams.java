package com.example.actionclassification;

import android.content.res.AssetManager;

import com.google.gson.Gson;

import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class SVCWithParams {

    private final SVC clf;
    private final SVMParams svmParam;

    static class SVMParams {
        double[][] vectors;
        double[][] coefficients;
        double[] intercepts;
        double[] data_max;
        double[] data_min;
        int[] weights;
        double gamma;
        String kernel;
        int nClasses;
        int nRows;
        double coef0;
        int degree;
    }

    public SVCWithParams(AssetManager assets) throws JSONException {
        String svmModelJsonString = Objects.requireNonNull(loadSvmJsonModelFromAsset(assets));
        Gson gson = new Gson();
        this.svmParam = gson.fromJson(svmModelJsonString, SVMParams.class);
        clf = new SVC(this.svmParam.nClasses,
                this.svmParam.nRows,
                this.svmParam.vectors,
                this.svmParam.coefficients,
                this.svmParam.intercepts,
                this.svmParam.weights,
                this.svmParam.kernel,
                this.svmParam.gamma,
                this.svmParam.coef0,
                this.svmParam.degree);
    }

    public int predict(double[] feature) {
        double[] featureScaled = minMaxScaleFeature(feature);
        return clf.predict(featureScaled);
    }

    private double[] minMaxScaleFeature(double[] feature) {
        double[] featureScaled = new double[feature.length];
        int i;
        for (i = 0; i < feature.length; i++) {
            featureScaled[i] = (feature[i] - this.svmParam.data_min[i]) /
                    (this.svmParam.data_max[i] - this.svmParam.data_min[i]);
        }
        return featureScaled;
    }

    private String loadSvmJsonModelFromAsset(AssetManager assets) {
        String json;
        try {
            InputStream is = assets.open("svm_model.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();

            json = new String(buffer, "UTF-8");


        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
    }
}
