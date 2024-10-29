package sfa.classification;

import java.util.Arrays;

import sfa.timeseries.TimeSeries;

/**
 *  A class to manage the different real-time approaches of teaser.
 */
public class TEASERClassifierRealtimeManager {
  // soll nur ein dataset betrachten
  // alle verschiedenen real-time ansätze werden durchgeführt
  // messungen hier, output im testskript

  public TimeSeries[] testSamples;
  public TimeSeries[] trainSamples;
  public double samplingrate;
  public predictionResults[][] results;

  public TEASERClassifierRealtime defaultTeaser;

  public int[] sValues;
  public int[] kValues;

  public TEASERClassifierRealtimeManager(TimeSeries[] testSamples, TimeSeries[] trainSamples, double samplingrate) {
    this.testSamples = testSamples;
    this.trainSamples = trainSamples;
    this.samplingrate = samplingrate;

    int ts_len = this.testSamples[0].getLength();
    this.sValues = new int[] {2, 5, 10, (int) (ts_len * 0.5), ts_len * 1}; // Snapshotanzahl der S_TEASER Instanzen
    this.kValues = new int[] {2, 3, 5, 10, 20}; // zusammenfassungsfensterlängen der K_TEASER Instanzen
  }

  public static class predictionResults {
    String predictionTyp;
    double datasetAccuracy;
    double avgEarliness;
    double avgPredictionTime;
    Double[][] seriesSnapshotTimes; // für jede TS ein Array der Duration an jedem Datenpunkt
    boolean realtime;

    public predictionResults(String predictionTyp, double datasetAccuracy, double avgEarliness, double avgPredictionTime, Double[][] seriesSnapshotTimes, boolean realtime) {
      this.predictionTyp = predictionTyp;
      this.datasetAccuracy = datasetAccuracy;
      this.avgEarliness = avgEarliness;
      this.avgPredictionTime = avgPredictionTime;
      this.seriesSnapshotTimes = seriesSnapshotTimes;
      this.realtime = realtime;
    }

    // getter
    public String getTyp() {
      return this.predictionTyp;
    }
    public double getAccuracy() {
      return this.datasetAccuracy;
    }
    public double getEarlyness() {
      return this.avgEarliness;
    }
    public double getPredictionTime() {
      return this.avgPredictionTime;
    }
    public Double[][] getSnapshotTimes() {
      return this.seriesSnapshotTimes;
    }
    public String getSnapshotTimesToString() {
      String result = "";
      for(int i = 0; i < this.seriesSnapshotTimes.length; i++) {
        result = result + Arrays.toString(this.seriesSnapshotTimes[i]) + "\n";
      }
      return result;
    }
    public boolean isAvgRealtime() {
      return realtime;
    }

    @Override
    public String toString() {
      return predictionTyp + "\n"
      + "avgMs: " + String.format("%.02f", this.avgPredictionTime) 
      + "  acc: " + String.format("%.02f", this.datasetAccuracy) 
      + "  earliness: " + String.format("%.02f", this.avgEarliness) 
      + "  " + (this.realtime ? "avg_REALTIME" : "avg_NOT_REALTIME");
    }
  }

  public predictionResults[][] manageRealtime() { // ev. noch die anzätze kombinieren
    // Train the default Teaser
    defaultTeaser = new TEASERClassifierRealtime();
    Classifier.Score scoreDefaultTeaser = defaultTeaser.fit_and_measure(this.trainSamples);
    System.out.println(scoreDefaultTeaser); // debugging

    predictionResults defaultTeaserResults = defaultTeaser_predict(samplingrate);
    predictionResults skippingTeaserResults = skippingTeaser_predict(samplingrate);
    predictionResults[] STeaserResults = STeaser_predict(samplingrate);
    predictionResults[] KTeaserResults = KTeaser_predict(samplingrate);

    results = new predictionResults[][] {new predictionResults[] {defaultTeaserResults}, new predictionResults[] {skippingTeaserResults}, STeaserResults, KTeaserResults};
    outputResults();
    return new predictionResults[][] {new predictionResults[] {defaultTeaserResults}, new predictionResults[] {skippingTeaserResults}, STeaserResults, KTeaserResults}; // eigentlich liefert jede Methode nur ein Result, zum messen brauche ich aber alle
  }

  public void outputResults() {
    for(int i = 0; i < results.length; i++) {
      for(int j = 0; j < results[i].length; j++) {
        System.out.println(results[i][j]);
      }
    }
  }

  public predictionResults defaultTeaser_predict(double samplingrate) {
    double min_fitfrequency = defaultTeaser.min_prediction_frequency; // das sollte auch eher hier stattfinden
    
    System.out.println("DEFAULT TEASER | Dataset samplingrate: " + samplingrate + "Hz | minimal fitting prediction frequency: " + min_fitfrequency + "Hz");
              
    // Prediction
    Double[][][] pred = defaultTeaser.predict_and_measure_dataset(testSamples, samplingrate, "default"); // pred[0] ist die acc, pred[1] sind die frequenzen aller Samples
    return new predictionResults("TEASER_Default", pred[0][0][0], pred[2][0][0], pred[5][0][0], pred[4], getMinFrequency(pred[1][0]) > samplingrate ? true : false);
  }

  public predictionResults skippingTeaser_predict(double samplingrate) { // benutzt auch den vortrainierten default teaser
    double min_fitfrequency = defaultTeaser.min_prediction_frequency; // das sollte auch eher hier stattfinden
    
    System.out.println("SKIPPING TEASER | Dataset samplingrate: " + samplingrate + "Hz | minimal fitting prediction frequency: " + min_fitfrequency + "Hz");
              
    // Prediction
    Double[][][] pred = defaultTeaser.predict_and_measure_dataset(testSamples, samplingrate, "realtime"); // pred[0] ist die acc, pred[1] sind die frequenzen aller Samples
    return new predictionResults("TEASER_Skipping", pred[0][0][0], pred[2][0][0], pred[5][0][0], pred[4], getMinFrequency(pred[1][0]) > samplingrate ? true : false);
  }

  public predictionResults[] STeaser_predict(double samplingrate) { // gibt erstmal nur die results der besten instanz zurück
    // training von 5 instanzen
    TEASERClassifierRealtimeInst[] teaserInst = new TEASERClassifierRealtimeInst[5];
    double[][] scores = new double[5][3]; // acc, earl, freq

    for(int i = 0; i < sValues.length; i++) { // 3 SN, 10 SN, defaultTeaser(20 SN spart traintime), 50 SN, lenght SN
      teaserInst[i] = new TEASERClassifierRealtimeInst();
      teaserInst[i].S = sValues[i]; // teaser erhöht S um 1
      Classifier.Score score = teaserInst[i].fit_and_measure(trainSamples);

      scores[i] = new double[] {score.getTrainingAccuracy(), score.getTestEarliness(), teaserInst[i].min_prediction_frequency}; // der score wird von teaser nur durch eval() gefüllt. ich schreibe die earliness nach dem predict der traindaten hinein
      // theorethisch könnte hier abgebrochen werden, wenn die min_prediction_frequency > samplingrate gilt. Weil dann eine RealtimeInstanz gefunden wurde. Ich brauche aber alle Messungen
    }
    // prediction auf traindaten -> beste instanz auswählen (brauche hier acc, earl, duration/min_prediction_freq)
    int instNr = 0;
    System.out.println(Arrays.deepToString(scores));
    for(int i = scores.length-1; i >= 0; i--) { // Instanz mit max acc und min early auswählen, die schneller als die samplingrate ist
      if(scores[i][2] > samplingrate) { // acc nimmt ab, early nimmt ab, freq nimmt ab, ev wollen wir also nach kleineren S suchen
        instNr = i;
      }
    }
    System.out.println("Instanz " + instNr + " wurde ausgewählt. " + scores[instNr][2]);
    // prediction auf testdaten
    //Double[][][] pred = teaserInst[instNr].predict_and_measure_dataset(testSamples, samplingrate, "default"); // prediction der besten standard teaser Instanz auf den trainingsdaten

    //return new predictionResults(pred[0][0][0], pred[2][0][0], pred[5][0][0], pred[4], pred[3][0][0] > samplingrate ? true : false);

    // Ab hier zum messen, sonst die zwei zeilen darüber nutzen
    predictionResults[] result = new predictionResults[sValues.length];
    for(int i = 0; i < sValues.length; i++) { // prediction auf allen Instanzen
      Double[][][] pred = teaserInst[i].predict_and_measure_dataset(testSamples, samplingrate, "default");
      result[i] = new predictionResults("TEASER_S_("+sValues[i]+")", pred[0][0][0], pred[2][0][0], pred[5][0][0], pred[4], getMinFrequency(pred[1][0]) > samplingrate ? true : false);
    }
    return result;
  }

  public predictionResults[] KTeaser_predict(double samplingrate) {
    // vorverarbeitung der train und testdaten
    // training von 5 instanzen
    for(int i = 0; i < 5; i++) { // es werden je K werte aus den TS zu einem wert Zusammengefasst (durchschnitt; nur den letzten Wert; ...)
      // K2, K3, K5, K10, K20
    }
    // training von 5 instanzen
    TEASERClassifierRealtimeInst[] teaserInst = new TEASERClassifierRealtimeInst[5];
    double[][] scores = new double[5][3]; // acc, earl, freq
    
    for(int i = 0; i < kValues.length; i++) {
      teaserInst[i] = new TEASERClassifierRealtimeInst();
      Classifier.Score score = teaserInst[i].fit_and_measure(downscaleTs(trainSamples, kValues[i]));

      scores[i] = new double[] {score.getTrainingAccuracy(), score.getTestEarliness(), teaserInst[i].min_prediction_frequency * kValues[i]}; // der score wird von teaser nur durch eval() gefüllt. ich schreibe die earliness nach dem predict der traindaten hinein
      // theorethisch könnte hier abgebrochen werden, wenn die min_prediction_frequency > samplingrate gilt. Weil dann eine RealtimeInstanz gefunden wurde. Ich brauche aber alle Messungen
    }
    // prediction auf traindaten -> beste instanz auswählen (brauche hier acc, earl, duration/min_prediction_freq)
    int instNr = 0;
    System.out.println(Arrays.deepToString(scores));
    for(int i = scores.length-1; i >= 0; i--) { // Instanz mit max acc und min early auswählen, die schneller als die samplingrate ist
      if(scores[i][2] > samplingrate) { // acc nimmt leicht zu, earl nimmt zu, freq nimmt ab (mit größerem k)
        instNr = i;
      }
    }
    System.out.println("Instanz " + instNr + " wurde ausgewählt. " + scores[instNr][2]);
    // prediction auf testdaten
    //Double[][][] pred = teaserInst[instNr].predict_and_measure_dataset(downscaleTs(testSamples, kValues[instNr]), samplingrate, "default"); // prediction der besten standard teaser Instanz auf den gekürzten trainingsdaten
    
    //return new predictionResults(pred[0][0][0], pred[2][0][0] * kValues[instNr], pred[5][0][0], pred[4], (pred[3][0][0] * kValues[instNr]) > samplingrate ? true : false); // earliness muss auf die eigentliche länge der ungekürzten ts wieder hochgerechnet werden
  
    // Ab hier zum messen, sonst die zwei zeilen darüber nutzen
    predictionResults[] result = new predictionResults[kValues.length];
    for(int i = 0; i < kValues.length; i++) { // prediction auf allen Instanzen
      Double[][][] pred = teaserInst[i].predict_and_measure_dataset(downscaleTs(testSamples, kValues[i]), samplingrate, "default");
      result[i] = new predictionResults("TEASER_K_("+kValues[i]+")", pred[0][0][0], pred[2][0][0], pred[5][0][0], pred[4], (getMinFrequency(pred[1][0]) * kValues[i]) > samplingrate ? true : false);
    }
    return result;
  }

  // helper methods
  private TimeSeries[] downscaleTs(TimeSeries[] ts, int k) {
    TimeSeries[] result = new TimeSeries[ts.length];
    for(int i = 0; i < ts.length; i++) { // für jede Zeitreihe im Dataset
      double[] editedData = new double[(int) ts[i].getLength() / k];
      for(int j = 0; j < editedData.length; j++) {
        editedData[j] = ts[i].getData()[k * j]; // nimmt immer den ersten wert und überspringt dann k-1 werte
      }
      result[i] = new TimeSeries(editedData, ts[i].getLabel());
    }
    return result;
  }

  private double getMinFrequency(Double[] seriesFrequencys) {
    double min = seriesFrequencys[0];
    for(int i = 1; i < seriesFrequencys.length; i++) { 
      if(seriesFrequencys[i] < min) {
        min = seriesFrequencys[i];
      }
    }
    return min;
  }
  
}
