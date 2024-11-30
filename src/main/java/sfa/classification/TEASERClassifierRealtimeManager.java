package sfa.classification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

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
    this.sValues = new int[] {4, 6, 10, Math.min((int) (ts_len * 0.5), 50), Math.min(ts_len * 1, 100)}; // Snapshotanzahl der S_TEASER Instanzen
    this.kValues = new int[] {2, 3, 5, 10, 20}; // zusammenfassungsfensterlängen der K_TEASER Instanzen
  }

  public static class predictionResults {
    String predictionTyp;
    double datasetAccuracy;
    double avgEarliness;
    double avgPredictionTime;
    Double[][] seriesSnapshotTimes; // für jede TS ein Array der Duration an jedem Datenpunkt
    double min_prediction_frequency;

    public predictionResults(String predictionTyp, double datasetAccuracy, double avgEarliness, double avgPredictionTime, Double[][] seriesSnapshotTimes, double min_prediction_frequency) {
      this.predictionTyp = predictionTyp;
      this.datasetAccuracy = datasetAccuracy;
      this.avgEarliness = avgEarliness;
      this.avgPredictionTime = avgPredictionTime;
      this.seriesSnapshotTimes = seriesSnapshotTimes;
      this.min_prediction_frequency = min_prediction_frequency;
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
    public double getMinPredictionFrequency() {
      return min_prediction_frequency;
    }

    @Override
    public String toString() {
      return predictionTyp + "\n"
      + "avgMs: " + String.format("%.02f", this.avgPredictionTime) 
      + "  acc: " + String.format("%.02f", this.datasetAccuracy) 
      + "  earliness: " + String.format("%.02f", this.avgEarliness) 
      + "  minFreq: " + String.format("%.02f", this.min_prediction_frequency);
    }
  }

  public predictionResults[][] manageRealtime() { // ev. noch die anzätze kombinieren
    // Train the default Teaser
    defaultTeaser = new TEASERClassifierRealtime();
    Classifier.Score scoreDefaultTeaser = defaultTeaser.fit_and_measure(this.trainSamples);
    System.out.println(scoreDefaultTeaser); // debugging
    
    // nicht parallele ausführung
    predictionResults defaultTeaserResults = defaultTeaser_predict(samplingrate);
    predictionResults skippingTeaserResults = skippingTeaser_predict(samplingrate);
    predictionResults[] STeaserResults = STeaser_predict(samplingrate);
    predictionResults[] KTeaserResults = KTeaser_predict(samplingrate);
    /* // Diese Parallele Ausführung führt zu Sprüngen in der Ausführungszeit (ev. wegen der Threadverwaltung)
    // Erstelle einen ForkJoinPool
    ForkJoinPool forkJoinPool = new ForkJoinPool();

    // Variablen für die Ergebnisse
    predictionResults[][] temp_results = new predictionResults[4][]; // eigentlich 4 zum debuggen 1

    // Führe die Aufgaben parallel aus
    forkJoinPool.submit(() -> {
        // Train the default Teaser
        Classifier.Score scoreDefaultTeaser = defaultTeaser.fit_and_measure(this.trainSamples);
        System.out.println(scoreDefaultTeaser); // debugging
        temp_results[0] = new predictionResults[] {defaultTeaser_predict(samplingrate)};
        temp_results[1] = new predictionResults[] {skippingTeaser_predict(samplingrate)};
    });

    forkJoinPool.submit(() -> {
        temp_results[2] = STeaser_predict(samplingrate);
    });

    forkJoinPool.submit(() -> {
        temp_results[3] = KTeaser_predict(samplingrate);
    });

    // Schließe den Pool und warte auf Abschluss
    forkJoinPool.shutdown();

    // Optional: Warte darauf, dass alle Aufgaben abgeschlossen sind
    while (!forkJoinPool.isTerminated()) {
        // Warten bis alle Aufgaben abgeschlossen sind
    }
    */
    System.out.println("Frequency done: " + samplingrate);

    results = new predictionResults[][] {new predictionResults[] {defaultTeaserResults}, new predictionResults[] {skippingTeaserResults}, STeaserResults, KTeaserResults};
    //outputResults();
    return new predictionResults[][] {new predictionResults[] {defaultTeaserResults}, new predictionResults[] {skippingTeaserResults}, STeaserResults, KTeaserResults}; // eigentlich liefert jede Methode nur ein Result, zum messen brauche ich aber alle (deswegen arrays bei K und S)
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
    return new predictionResults("TEASER_Default", pred[0][0][0], pred[2][0][0], pred[5][0][0], pred[4], getMinFrequency(pred[1][0]));
  }

  public predictionResults skippingTeaser_predict(double samplingrate) { // benutzt auch den vortrainierten default teaser
    double min_fitfrequency = defaultTeaser.min_prediction_frequency; // das sollte auch eher hier stattfinden
    
    System.out.println("SKIPPING TEASER | Dataset samplingrate: " + samplingrate + "Hz | minimal fitting prediction frequency: " + min_fitfrequency + "Hz");
              
    // Prediction
    Double[][][] pred = defaultTeaser.predict_and_measure_dataset(testSamples, samplingrate, "realtime"); // pred[0] ist die acc, pred[1] sind die frequenzen aller Samples
    return new predictionResults("TEASER_Skip", pred[0][0][0], pred[2][0][0], pred[5][0][0], pred[4], getMinFrequency(pred[1][0]));
  }

  public predictionResults[] STeaser_predict(double samplingrate) { // gibt erstmal nur die results der besten instanz zurück
    // training von 5 instanzen
    TEASERClassifierRealtimeInst[] teaserInst = new TEASERClassifierRealtimeInst[5];
    double[][] scores = new double[5][3]; // acc, earl, freq

    ForkJoinPool customThreadPool = new ForkJoinPool(5);
    customThreadPool.submit(() -> IntStream.range(0, sValues.length).parallel().forEach(i -> {
      teaserInst[i] = new TEASERClassifierRealtimeInst();
      teaserInst[i].S = sValues[i]; // teaser erhöht S um 1
      Classifier.Score score = teaserInst[i].fit_and_measure(trainSamples);

      scores[i] = new double[] {score.getTrainingAccuracy(), score.getTestEarliness(), teaserInst[i].min_prediction_frequency}; // der score wird von teaser nur durch eval() gefüllt. ich schreibe die earliness nach dem predict der traindaten hinein
      // theorethisch könnte hier abgebrochen werden, wenn die min_prediction_frequency > samplingrate gilt. Weil dann eine RealtimeInstanz gefunden wurde. Ich brauche aber alle Messungen
    })).join(); // ab hier nicht mehr parallel

    // prediction auf traindaten -> beste instanz auswählen (brauche hier acc, earl, duration/min_prediction_freq)
    int instNr = getBestRealtimeInst(scores);
    //System.out.println(Arrays.deepToString(scores));
    
    System.out.println("S TEASER | Dataset samplingrate: " + samplingrate + "Hz | minimal fitting prediction frequency (of inst): " + teaserInst[instNr].min_prediction_frequency + "Hz");
    System.out.println("Instanz " + instNr + " wurde ausgewählt. " + scores[instNr][2]);
    // prediction auf testdaten
    //Double[][][] pred = teaserInst[instNr].predict_and_measure_dataset(testSamples, samplingrate, "default"); // prediction der besten standard teaser Instanz auf den trainingsdaten

    //return new predictionResults(pred[0][0][0], pred[2][0][0], pred[5][0][0], pred[4], pred[3][0][0] > samplingrate ? true : false);

    // Ab hier zum messen, sonst die zwei zeilen darüber nutzen
    predictionResults[] result = new predictionResults[sValues.length];
    for(int i = 0; i < sValues.length; i++) { // prediction mit allen Instanzen
      Double[][][] pred = teaserInst[i].predict_and_measure_dataset(testSamples, samplingrate, "default");
      result[i] = new predictionResults("TEASER_S_("+sValues[i]+")", pred[0][0][0], pred[2][0][0], pred[5][0][0], pred[4], getMinFrequency(pred[1][0]));
    }
    //return result; // alle instanzen darstellen
    Double[][][] pred = teaserInst[instNr].predict_and_measure_dataset(testSamples, samplingrate, "realtime"); // Skipping zusätzlich verwenden
    return new predictionResults[] {result[instNr], new predictionResults("TEASER_SSkip_("+sValues[instNr]+")", pred[0][0][0], pred[2][0][0], pred[5][0][0], pred[4], getMinFrequency(pred[1][0]))}; // nur die beste instanz
  }

  public predictionResults[] KTeaser_predict(double samplingrate) {
    // training von 5 instanzen
    TEASERClassifierRealtimeInst[] teaserInst = new TEASERClassifierRealtimeInst[5];
    double[][] scores = new double[5][3]; // acc, earl, freq
    
    ForkJoinPool customThreadPool = new ForkJoinPool(5);
    customThreadPool.submit(() -> IntStream.range(0, kValues.length).parallel().forEach(i -> {
      teaserInst[i] = new TEASERClassifierRealtimeInst();
      teaserInst[i].K = kValues[i];
      teaserInst[i].ts_len = trainSamples[0].getLength();
      Classifier.Score score = teaserInst[i].fit_and_measure(downscaleTs(trainSamples, kValues[i]));

      scores[i] = new double[] {score.getTrainingAccuracy(), score.getTestEarliness(), teaserInst[i].min_prediction_frequency * kValues[i]}; // der score wird von teaser nur durch eval() gefüllt. ich schreibe die earliness nach dem predict der traindaten hinein
      // theorethisch könnte hier abgebrochen werden, wenn die min_prediction_frequency > samplingrate gilt. Weil dann eine RealtimeInstanz gefunden wurde. Ich brauche aber alle Messungen
    })).join(); // ab hier nicht mehr parallel

    // prediction auf traindaten -> beste instanz auswählen (brauche hier acc, earl, duration/min_prediction_freq)
    int instNr = getBestRealtimeInst(scores);
    //System.out.println(Arrays.deepToString(scores));
    
    System.out.println("K TEASER | Dataset samplingrate: " + samplingrate + "Hz | minimal fitting prediction frequency (of inst): " + teaserInst[instNr].min_prediction_frequency * kValues[instNr] + "Hz");
    System.out.println("Instanz " + instNr + " wurde ausgewählt. " + scores[instNr][2]);
    // prediction auf testdaten
    //Double[][][] pred = teaserInst[instNr].predict_and_measure_dataset(downscaleTs(testSamples, kValues[instNr]), samplingrate, "default"); // prediction der besten standard teaser Instanz auf den gekürzten trainingsdaten
    
    //return new predictionResults(pred[0][0][0], pred[2][0][0] * kValues[instNr], pred[5][0][0], pred[4], (pred[3][0][0] * kValues[instNr]) > samplingrate ? true : false); // earliness muss auf die eigentliche länge der ungekürzten ts wieder hochgerechnet werden
  
    // Ab hier zum messen, sonst die zwei zeilen darüber nutzen
    predictionResults[] result = new predictionResults[kValues.length];
    for(int i = 0; i < kValues.length; i++) { // prediction mit allen Instanzen
      Double[][][] pred = teaserInst[i].predict_and_measure_dataset(downscaleTs(testSamples, kValues[i]), samplingrate, "default");
      result[i] = new predictionResults("TEASER_K_("+kValues[i]+")", pred[0][0][0], pred[2][0][0], pred[5][0][0], pred[4], (getMinFrequency(pred[1][0]) * kValues[i]));
    }
    //return result; // alle instanzen darstellen
    Double[][][] pred = teaserInst[instNr].predict_and_measure_dataset(downscaleTs(testSamples, kValues[instNr]), samplingrate, "realtime"); // Skipping zusätzlich verwenden
    return new predictionResults[] {result[instNr], new predictionResults("TEASER_KSkip_("+kValues[instNr]+")", pred[0][0][0], pred[2][0][0], pred[5][0][0], pred[4], (getMinFrequency(pred[1][0]) * kValues[instNr]))}; // nur die beste instanz
  }

  // helper methods
  private TimeSeries[] downscaleTs(TimeSeries[] ts, int k) {
    TimeSeries[] result = new TimeSeries[ts.length];
    for(int i = 0; i < ts.length; i++) { // für jede Zeitreihe im Dataset
      double[] editedData = new double[(int) ts[i].getLength() / k];
      for(int j = 0; j < editedData.length; j++) {
        double sum = 0;
        for(int x = k * j; x < k * (j+1); x++) {
          sum += ts[i].getData()[x];
        }
        editedData[j] = sum / k; // bildet von je k Werten den Durchschnitt
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

  private double getAvgFrequency(Double[] seriesFrequencys) {
    double avg = 0;
    for(int i = 0; i < seriesFrequencys.length; i++) { 
      avg += seriesFrequencys[i];
    }
    avg = avg / seriesFrequencys.length;
    return avg;
  }

  private int getBestRealtimeInst(double[][] scores) { // realtime ist ein hartes kriterium, unter allen realtime instanzen wird die mit dem besten harmonic mean von accuracy und earliness gewählt // wenn es keine realtimeinstanz gibt, wählen wir die instanz, mit der höchsten avg frequenz
    int res = -1;
    double max_harmonic_mean = 0;
    for(int i = 0; i < scores.length; i++) { // für jede instanz
      //System.out.println(scores[i][2]);
      if(scores[i][2] > samplingrate) { // instanz schafft realtime
        double harmonic_mean = (2 * (1 - scores[i][1]) * scores[i][0]) / ((1 - scores[i][1]) + scores[i][0]);
        //System.out.println(i + " " + scores[i][1] + " " + scores[i][0] + " " + harmonic_mean + " realtime");
        if(max_harmonic_mean < harmonic_mean) {
          max_harmonic_mean = harmonic_mean;
          res = i;
        }
      }
    }
    if(res == -1) { // es gibt keine realtime instanz
      double max_frequency = 0;
      for(int i = 0; i < scores.length; i++) { // für jede instanz
        //System.out.println(i + " " + scores[i][2] + " not realtime");
        if(max_frequency < scores[i][2]) {
          max_frequency = scores[i][2];
          res = i;
        }
      }
    }
    return res;
  }
}
