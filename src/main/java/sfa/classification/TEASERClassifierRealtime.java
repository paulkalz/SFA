package sfa.classification;

import com.carrotsearch.hppc.*;
import com.carrotsearch.hppc.cursors.DoubleDoubleCursor;
import de.bwaldvogel.liblinear.SolverType;
import libsvm.*;
import sfa.timeseries.TimeSeries;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.*;

/**
 * TEASER: A framework for early and accurate times series classification
 * <p>
 *   Univariate classifier with WEASEL as slave
 * </p>
 */
public class TEASERClassifierRealtime extends Classifier {

  public double max_prediction_time = 0; // langsamster der 4 Trainings Predictions (in milliseconds)
  public double min_prediction_frequency = Double.MAX_VALUE; // mit einem fit_and_measure() messen wir die minimale prediction frequenz auf den trainingsdaten
  private boolean skip = false; // damit kann die Prediction einzelner Snapshots übersprungen werden, um databacklogs zu verhindern
  private Double[] timesPerSnapshot; // darin werden die Predictiontimes der einzelnen Snapshots gespeichet
  private Double[] avgMsPerSnapshotFit; // avg Ms fuer die Prediction jedes Snapshots, gemessen beim training, zum einschaetzen der Predictiontime
  private boolean disable_earlyness = false; // wenn true, wird jeder Snapshot klassifiziert
  private double originalDatasetFrequency = 0;

  /**
   * The parameters for the one-class SVM
   */
  public static int SVM_KERNEL = svm_parameter.RBF; /*, svm_parameter.LINEAR */
  public static double[] SVM_GAMMAS = new double[]{100, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1.5, 1};
  public static double SVM_NU = 0.05;

  /**
   * The total number of time stamps S: a time stamp is a fraction of the full time series length n.
   * S is typically a constant set to 20, such that a prediction will be made after every 5% of the full
   * time series length.
   */
  public static double S = 20.0;

  public static boolean PRINT_EARLINESS = false;

  public static int MIN_WINDOW_LENGTH = 2;
  public static int MAX_WINDOW_LENGTH = 250;

  // the trained TEASER model
  EarlyClassificationModel model;

  WEASELClassifier slaveClassifier;

  public TEASERClassifierRealtime() {
    slaveClassifier = new WEASELClassifier();
    WEASELClassifier.lowerBounding = true;
    WEASELClassifier.solverType = SolverType.L2R_LR;
    WEASELClassifier.MAX_WINDOW_LENGTH = 250;

  }

  public static class EarlyClassificationModel extends Model {
    public EarlyClassificationModel() {
      super("TEASER", 0, 1, 0, 1, false, -1);

      this.offsets = new int[(int) S + 1];
      this.masterModels = new svm_model[(int) S + 1];
      this.slaveModels = new WEASELClassifier.WEASELModel[(int) S + 1];

      Arrays.fill(this.offsets, -1);
    }

    public svm_model[] masterModels;
    public WEASELClassifier.WEASELModel[] slaveModels;

    public int[] offsets;
    public int threshold;
  }


  static class OffsetPrediction {
    double offset;
    Double[] labels;
    int correct;
    int N;

    public OffsetPrediction(double offset, Double[] labels, int correct, int N) {
      this.offset = offset;
      this.correct = correct;
      this.labels = labels;
      this.N = N;
    }

    public int getCorrect() {
      return this.correct;
    }

    @Override
    public String toString() {
      return
          "Avg. Offset\t" + this.offset
              + "\tacc: " + String.format("%.02f", ((double) getCorrect()) / this.N)
              + "\tearliness: " + String.format("%.02f", this.offset / this.N);
    }

  }

  @Override
  public Score eval(
      final TimeSeries[] trainSamples, final TimeSeries[] testSamples) {
    long startTime = System.currentTimeMillis();

    Score score = fit(trainSamples);

    // training score
    if (DEBUG) {
      System.out.println("TEASER Training:\t");
      outputResult(score.training, startTime, trainSamples.length);
    }

    // determine score
    OffsetPrediction pred = predict(testSamples, true);
    int correctTesting = pred.getCorrect();

    if (DEBUG) {
      System.out.println("TEASER Testing:\t");
      outputResult(correctTesting, startTime, testSamples.length);
      System.out.println("");
    }

    score.avgOffset = pred.offset / testSamples.length;
    score.testing = correctTesting;
    score.testSize = testSamples.length;
    score.trainSize = trainSamples.length;

    return score;
  }

  @Override
  public Score fit(final TimeSeries[] trainSamples) {

    // train the shotgun models for different offsets
    this.model = fitTeaser(trainSamples);

    // return score
    return model.score;
  }

  public Score fit_and_measure(final TimeSeries[] trainingSamples) {
    // train the shotgun models for different offsets
    this.model = fitTeaser(trainingSamples);

    //messungen der predictiontime der Snapshots
    avgMsPerSnapshotFit = new Double[(int) S+1];
    Arrays.fill(avgMsPerSnapshotFit, 0.0);
    disable_earlyness = true;

    // die minimale prediction frequenz bestimmen
    double average_prediction_time_per_ts = (max_prediction_time / 1000000) / trainingSamples.length; // in ms
    double fit_predict_frequency = 1 / ((average_prediction_time_per_ts / trainingSamples[0].getLength()) / 1000);
    
    // test prediction auf den trainsamples (um die langsamste frequenz zu finden, und average snapshottimes zu messen)
    double max_train_pred_duration = 0;
    for(TimeSeries trainSeries : trainingSamples) {
      long startTimetrainpred = System.nanoTime();
      Double[] snapTimes = predict_and_measure(new TimeSeries[] {trainSeries}, 0.0, "")[5]; // brauche spaeter um zu schaetzen, wie lange laengere Snapshots brauchen
      long estimatedTimetrainpred = System.nanoTime() - startTimetrainpred;
      double estimatedMilliSecondstrainpred = (double) estimatedTimetrainpred / 1000000;
      max_train_pred_duration = max_train_pred_duration > estimatedMilliSecondstrainpred ? max_train_pred_duration : (double) estimatedMilliSecondstrainpred;

      for(int i = 0; i < S+1; i++) {
        avgMsPerSnapshotFit[i] += snapTimes[i];
      }
    }
    double min_train_predict_frequency = 1 / ((max_train_pred_duration / trainingSamples[0].getLength()) / 1000);
    min_prediction_frequency = Math.min(min_train_predict_frequency, fit_predict_frequency); // kleinere Frequenz finden

    for(int i = 0; i < S+1; i++) {
      avgMsPerSnapshotFit[i] = avgMsPerSnapshotFit[i] / trainingSamples.length;
    }
    //System.out.println(Arrays.toString(avgMsPerSnapshotFit));

    disable_earlyness = false;
    // return score
    return model.score;
  }

  public EarlyClassificationModel fitTeaser(final TimeSeries[] samples) {
    try {

      int min = Math.max(3, MIN_WINDOW_LENGTH); // ist 3
      int max = getMax(samples, MAX_WINDOW_LENGTH); // Integer.MAX_VALUE // ist min(maxwindowlen(250), länge der längsten TS)
      for (TimeSeries ts : samples) { // MaxWindowLen wird ignoriert, damit die gesamte TS betrachtet werden kann
        max = Math.max(ts.getLength(), max);
      }
      double step = max / S; // steps of 5% // maximal 12,5 bei S=20 und WindowLen=250

      this.model = new EarlyClassificationModel();

      for (int s = 2; s <= S; s++) {
        // train TEASER
        model.offsets[s] = (int) Math.round(step * s); // model.offsets enthält bis zu welchem Datenpunkt klassifiziert werden soll? z.b. 25, 37, 50, 62, ... // in position 0 und 1 ist der Wert -1
        TimeSeries[] data = extractUntilOffset(samples, model.offsets[s], true); // kürzt alle TS bis zum Offset

        if (model.offsets[s] >= min) {
          // train the time series classifier
          Score score = this.slaveClassifier.fit(data); // accuracy der (gekürzten)trainingsdaten
          Predictions result = this.slaveClassifier.predictProbabilities(data);

          // train one class svm on ts classifier
          model.slaveModels[s] = this.slaveClassifier.getModel();
          model.masterModels[s] = fitSVM(samples, result.labels, result.probabilities, result.realLabels);
        }
      }

      // train the best ratio between earliness and accuracy
      double bestF1 = -1;
      int bestCount = 1;
      for (int i = 2; i <= 5; i++) { // 4 predictions, um den besten harmonic mean zu finden
        model.threshold = i;

        long startTimeFit = System.nanoTime();
        OffsetPrediction off = predict(samples, false); //  prediction auf den trainingsdaten, die TS werden nicht gekürzt
        long estimatedTimeFit = System.nanoTime() - startTimeFit;
        System.out.print("Prediction Duration (for entire trainDataset): ");
        System.out.println((double) estimatedTimeFit / 1000000); //TimeUnit.NANOSECONDS.toMillis(estimatedTime); // convert to milliseconds
        max_prediction_time = max_prediction_time > estimatedTimeFit ? max_prediction_time : (double) estimatedTimeFit;


        double correct = ((double) off.getCorrect()) / off.N; // correct = #RichtigKlassifiziert / #Datenpunkte
        double earliness = 1.0 - off.offset / off.N; 

        double harmonic_mean = 2 * correct * earliness / (correct + earliness);
        System.out.println("Prediction:\t" + model.threshold + "\t" + off + "\t" + harmonic_mean);

        if (bestF1 < harmonic_mean) {
          bestF1 = harmonic_mean;
          bestCount = i;

          model.score.training = off.getCorrect();
          model.score.trainSize = samples.length;
          //model.score.testSize = samples.length;
          //model.score.testing = off.getCorrect();

        }
      }

      System.out.println("Best Repetition: " + bestCount);
      model.threshold = bestCount;

      return model;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public svm_model fitSVM(
      final TimeSeries[] samples,
      Double[] predictedLabels,
      double[][] probs,
      int[] probsLabels
  ) {

    ArrayList<double[]> probabilities = new ArrayList<>();
    ArrayList<int[]> labels = new ArrayList<>();
    DoubleArrayList correct = new DoubleArrayList();
    for (int ind = 0; ind < samples.length; ind++) {
      double is_corr = compareLabels(samples[ind].getLabel(), predictedLabels[ind]) ? 1 : 0;
      if (is_corr == 1) {
        labels.add(probsLabels);
        probabilities.add(probs[ind]);
        correct.add(1);
      }
    }

    svm_problem problem_one_class = initProblem(
        probabilities.toArray(new double[][]{}),
        labels.toArray(new int[][]{}),
        correct.toArray());

    svm_parameter best_parameter = null;
    double bestCorrect = -1;
    for (double gamma : SVM_GAMMAS) {
      svm_parameter parameter = initSVMParameters(gamma);
      if (svm.svm_check_parameter(problem_one_class, parameter) != null) {
        System.out.println(svm.svm_check_parameter(problem_one_class, parameter));
      }
      ;
      Double[] predictions = new Double[problem_one_class.l];
      trainSVMOneClass(problem_one_class, parameter, 10, predictions, new Random(1));
      double correct2 = evalLabels(problem_one_class.y, predictions).correct.get() / (double) problem_one_class.l;

      if (correct2 > bestCorrect) {
        best_parameter = parameter;
        bestCorrect = correct2;
      }
    }
    return svm.svm_train(problem_one_class, best_parameter);
  }

  public TimeSeries[] extractUntilOffset(TimeSeries[] samples, int offset, boolean testing) {
    List<TimeSeries> offsetSamples = new ArrayList<TimeSeries>();
    for (TimeSeries sample : samples) { 
      if (testing) { // bei testdaten
        offsetSamples.add(sample.getSubsequence(0, offset)); // nimmt die werte der TS bis zum Offset (gibt eine TS zurück)
      } else { // bei trainingsdaten
        offsetSamples.add(sample); //nimmt die kompletten TimeSeries
      }
    }
    return offsetSamples.toArray(new TimeSeries[]{});
  }

  public int getCount(DoubleIntHashMap counts, double prediction) {
    int count = counts.get(prediction);
    if (count == 0) {
      counts.clear();
    }
    return counts.addTo(prediction, 1);
  }

  @Override
  public Predictions score(final TimeSeries[] testSamples) {
    Double[] labels = predict(testSamples);
    return evalLabels(testSamples, labels);
  }

  
  @Override
  public Double[] predict(final TimeSeries[] testSamples) {
    return predict(testSamples, true).labels;
  }

  public Double[][][] predict_and_measure_dataset(final TimeSeries[] timeSeriesDataset, double datasetFrequency, String resamplingStrategy) {   
    int correct_prediction = 0;
    double average_earliness = 0;
    double average_prediction_time = 0;
    disable_earlyness = false; // soll mit earliness predicted werden
    Double[] seriesFrequencys = new Double[timeSeriesDataset.length];
    Double[][] seriesSnapshotTimes = new Double[timeSeriesDataset.length][(int)S+1];
    for(int i = 0; i < timeSeriesDataset.length; i++) {
      TimeSeries timeSeries = timeSeriesDataset[i];
      Double[][] result = predict_and_measure(new TimeSeries[]{timeSeries}, datasetFrequency, resamplingStrategy);

      seriesFrequencys[i] = 1 / ((result[4][0] / (timeSeries.getLength() * result[1][0])) / 1000);
      correct_prediction += result[2][0];
      average_earliness += result[1][0];
      seriesSnapshotTimes[i] = result[5];
      average_prediction_time += result[4][0];
    }
    double datasetAccuracy = ((double) correct_prediction / (double) timeSeriesDataset.length);
    average_earliness = average_earliness / (double) timeSeriesDataset.length;
    average_prediction_time = average_prediction_time / (double) timeSeriesDataset.length;
    System.out.println("Average Earliness: " + average_earliness);
    double average_frequency = 0;
    for(double freq : seriesFrequencys) {
      average_frequency += freq;
    }
    average_frequency = average_frequency / (double) seriesFrequencys.length;
    System.out.println("Average Frequency: " + average_frequency);

    return new Double[][][] {new Double[][] {new Double[] {datasetAccuracy}}, new Double[][] {seriesFrequencys}, new Double[][] {new Double[] {average_earliness}}, new Double[][] {new Double[] {average_frequency}}, seriesSnapshotTimes, new Double[][] {new Double[] {average_prediction_time}}};
    // accuracy of the dataset, array with the reached prediction frequency for each timeseries in the dataset, average earliness, average prediction frequency, elapsed prediction time at every Snapshotprediction for every TS, average prediction time of the dataset
  }

  // nur für Predictions einer einzelnen TS
  public Double[][] predict_and_measure(TimeSeries[] testSamples, double frequency, String resamplingStrategy) {
    // hier die Parameter anpassen
    originalDatasetFrequency = frequency;
    switch (resamplingStrategy) {
      case "realtime":
          skip = true;
        break;
    
      default:
          skip = false;
        break;
    }
    
    // Measure the prediction time
    long startTime = System.nanoTime();
    OffsetPrediction temp = predict(testSamples, true);
    long estimatedTime = System.nanoTime() - startTime;
    double estimatedMilliSeconds = (double) estimatedTime / 1000000; //TimeUnit.NANOSECONDS.toMillis(estimatedTime); // convert to milliseconds
    //System.out.println("Prediction Time in Ms: " + estimatedMilliSeconds + " | Earliness: " + temp.offset);

    return new Double[][] {new Double[] {temp.labels[0]}, new Double[] {temp.offset}, new Double[] {(double) temp.correct}, new Double[] {(double) temp.N}, new Double[] {estimatedMilliSeconds}, timesPerSnapshot};
    // Predicted Label, offset = used percentage of TS length (= earliness), number of correct prediction (0 or 1), number of TS in Dataset (1), duration of the prediction, elapsed prediction time at every Snapshotprediction
  }

  // debugging function
  public void printJVMMemory() {
    // Runtime-Instanz abrufen
    Runtime runtime = Runtime.getRuntime();

    // Maximaler Heap-Speicher (in Bytes)
    long maxHeapSize = runtime.maxMemory();
    // Aktuell verwendeter Heap-Speicher (in Bytes)
    long usedHeapSize = runtime.totalMemory() - runtime.freeMemory();

    //System.out.println("Maximaler Heap-Speicher: " + maxHeapSize / (1024 * 1024) + " MB");
    //System.out.println("Aktuell verwendeter Heap-Speicher: " + usedHeapSize / (1024 * 1024) + " MB");
    if(usedHeapSize > maxHeapSize * 0.9) {
      System.out.println("Heap Speicher wird knapp");
    }
  }

  private int nextSnapshot(double datasetFrequency, int lastSnapshot, double elapsedPredictionTimeMs) { // currentSnapshot ist der letzte bearbeitete Snapshot
    if(model.offsets[lastSnapshot] == -1) {return lastSnapshot + 1;} // es wurde noch keine Prediction gemacht
    double currentFrequency = 1 / ((elapsedPredictionTimeMs / model.offsets[lastSnapshot]) / 1000); // aktuelle prediction frequenz bestimmen (in Hz) (am letzten snapshot)
    if(currentFrequency > datasetFrequency) { // wenn schnell genug, dann weitermachen 
      return lastSnapshot + 1;
    }
    // nicht schnell genug // ein databacklog muss aufgeloest werden durch ueberspringen von datenpunkten
    for(int i = lastSnapshot+1; i < S+1; i++) { // fuer alle noch kommenden Snapshots
      double estimated_post_skip_Snapshot_time = avgMsPerSnapshotFit[i] - avgMsPerSnapshotFit[i-1]; // zeit, die beim fitten durchschnittlich fuer Snapshot i gebraucht wurde
      currentFrequency = 1 / (((elapsedPredictionTimeMs + estimated_post_skip_Snapshot_time) / model.offsets[i]) / 1000);
      
      if(currentFrequency > datasetFrequency) { // es ist nach dem skippen zu Snapshot i schnell genug
        double temp = 1 / ((elapsedPredictionTimeMs / model.offsets[lastSnapshot]) / 1000);
        //System.out.println(elapsedPredictionTimeMs + " " + model.offsets[lastSnapshot]);
        //System.out.println("ZU LANGSAM -> Skippen von " + lastSnapshot + " zu " + i + " ( von " + temp + " zu " + currentFrequency + " ) " + datasetFrequency); // current = estimation nach dem skip, temp = aktuell
        return i; // returns the next Snapshot that should be evaluated (return - currentSnapshot = number of Snapshots that should be skipped to predict with datasetFrequency)
      }
    }
    //System.out.println("ZU LANGSAM - REAL-TIME NICHT MOEGLICH " + lastSnapshot + " " + currentFrequency + " " + datasetFrequency);
    return (int) S; // es ist nicht mehr moeglich schnell genug zu sein // ich skippe zum letzten snapshot
  }

  // Pruefen, wie stark der einfluss der garbage collection auf die messungen ist. -> genug ram verwenden damit garbage collection nur selten auftritt
  private static long getTotalGCTime() { // Aktivitätsdauer des GarbageCollectors
        long totalGCTime = 0;
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            totalGCTime += gcBean.getCollectionTime();
        }
        return totalGCTime; // in ms
  }

  private OffsetPrediction predict(
      final TimeSeries[] testSamples,
      final boolean testing) {

    double avgOffset = 0;
    int correct = 0;
    int count = 0;

    Double[] predictedLabels = new Double[testSamples.length];
    int[] offsets = new int[testSamples.length];
    DoubleIntHashMap[] predictions = new DoubleIntHashMap[testSamples.length];
    for (int i = 0; i < testSamples.length; i++) {
      predictions[i] = new DoubleIntHashMap();
    }

    DoubleDoubleMap perClassEarliness = new DoubleDoubleHashMap();
    DoubleIntMap perClassCount = new DoubleIntHashMap();

    timesPerSnapshot = new Double[(int) S+1]; // um in jedem durchlauf die Ausfhrungzeit jedes Snapshots zu speichern
    Arrays.fill(timesPerSnapshot, 0.0);

    //System.out.print("OffsetArray: "); // DEBUG - um die Offsets anzuzeigen
    //for(int i = 0; i<model.offsets.length; i++) {
    //  System.out.print(model.offsets[i]);
    //  System.out.print(" / ");
    //}
    //System.out.println();
    //System.out.println(Arrays.toString(extractUntilOffset(testSamples, model.offsets[2], true)[0].getData()));
    //printJVMMemory();
    long gcStartTime = getTotalGCTime();
    long gcCurrentTime = gcStartTime;
    long gcDuration = 0;
    long startTimePred = System.nanoTime();
    timesPerSnapshot[0] = (double) (System.nanoTime() - startTimePred) / 1000000; // sollte 0 sein

    predict:
    for (int s = 0; s < model.slaveModels.length; s++) { // S + 1 Wealsel (slaves) (im normalfall 21) mit jedem durchlauf wurde auf 5% mehr daten trainiert
      if(model.masterModels[s] != null && testing && skip) {s = nextSnapshot(originalDatasetFrequency, 0 > s-1 ? 0 : s-1, ((double) (System.nanoTime() - startTimePred) / 1000000));}
      if (model.masterModels[s] != null) { // array mit trainierten oneClassSVMs (trainingsdaten in 5% schnitten ansteigend) // die ersten beiden werden nicht trainiert (0,1 sind null)
        
        // extract samples of length offset
        TimeSeries[] data = extractUntilOffset(testSamples, model.offsets[s], testing); // die ersten beiden werte sind -1 // ich habe immer nur 1 TS gleichzeitig
        
        // Slave Classification
        this.slaveClassifier.setModel(model.slaveModels[s]); // TODO ugly
        Predictions result = this.slaveClassifier.predictProbabilities(data); //  wahrscheinlichkeiten pro Klasse

        for (int ind = 0; ind < data.length; ind++) { // data sind die TS aus testSamples aber gekürzt nach dem Offset // loop einmal pro TS
          if (predictedLabels[ind] == null) {
            double predictedLabel = result.labels[ind]; 
            double[] probabilities = result.probabilities[ind]; 

            // Master Klassifikation
            double predictNow = svm.svm_predict(
                model.masterModels[s],
                generateFeatures(probabilities, result.realLabels)); // SVM berechnet, ob predicted wird // ist -1.0 oder 1.0 

            if (s >= S // es soll eine prediction geben
                || model.offsets[s] >= testSamples[ind].getLength()
                || predictNow == 1
                ) {
              int counts = getCount(predictions[ind], predictedLabel); 
              if (counts >= model.threshold
                  || s >= S
                  || model.offsets[s] >= testSamples[ind].getLength()) { 
                predictedLabels[ind] = predictedLabel; // Prediction festlegen

                double earliness = Math.min(1.0, ((double) model.offsets[s] / testSamples[ind].getLength()));
                avgOffset += earliness; 

                offsets[ind] = model.offsets[s];

                perClassEarliness.addTo(testSamples[ind].getLabel(), earliness); // earliness der einzelnen Klassen speichern
                perClassCount.addTo(testSamples[ind].getLabel(), 1); 

                if (compareLabels(testSamples[ind].getLabel(), predictedLabel)) { // richtige predictions zählen
                  correct++;
                }
                count++;
              }
            }
          }
          // no predictions to be made
          if (count == testSamples.length && !disable_earlyness) {
            //if(testing) {System.out.println((double) (System.nanoTime() - startTimePred) / 1000000);} // Dauer bis zur entgültigen Klassifikation
            gcCurrentTime = getTotalGCTime();
            gcDuration = gcCurrentTime - gcStartTime;
            //if(gcDuration > 0) {System.out.println(gcDuration);}
            timesPerSnapshot[s] = ((double) (System.nanoTime() - startTimePred) / 1000000);
            
            break predict;
          }
        }
      }
      gcCurrentTime = getTotalGCTime();
      gcDuration = gcCurrentTime - gcStartTime;
      //if(gcDuration > 0) {System.out.println(gcDuration);}
      timesPerSnapshot[s] = ((double) (System.nanoTime() - startTimePred) / 1000000);
    }

    // per Class counts
    if (false) { // während entwicklung deaktiviert
      for (DoubleDoubleCursor c : perClassEarliness) {
        System.out.println("Class\t" + c.key + "\t Earliness \t" + c.value / perClassCount.get(c.key));
      }
    }

    //System.out.println (Arrays.toString(predictedLabels));

    if (testing && PRINT_EARLINESS) {
      for (int ind = 0; ind < offsets.length; ind++) {
        int e = offsets[ind];
        System.out.print("[" + e + "," + (compareLabels(predictedLabels[ind], testSamples[ind].getLabel()) ? "True" : "False") + "],");
      }
      System.out.println("");
    }

    return new OffsetPrediction(
        avgOffset,
        predictedLabels,
        correct,
        testSamples.length);
  }

  public svm_parameter initSVMParameters(double gamma) {
    svm_parameter parameter2 = new svm_parameter();
    parameter2.eps = 1e-4;
    parameter2.nu = SVM_NU;
    parameter2.gamma = gamma;
    parameter2.kernel_type = SVM_KERNEL;
    parameter2.cache_size = 40;
    parameter2.svm_type = svm_parameter.ONE_CLASS;
    return parameter2;
  }

  public static svm_problem initProblem(
      final double[][] probabilities,
      final int[][] labels,
      double[] correctPrediction) {
    svm.svm_set_print_string_function(new svm_print_interface() {
      @Override
      public void print(String s) {
      } // Disables svm output
    });
    svm.rand.setSeed(1);

    svm_problem problem = new svm_problem();
    final svm_node[][] features = initLibSVM(probabilities, labels);
    problem.y = correctPrediction;
    problem.l = features.length;
    problem.x = features;
    return problem;
  }

  public static svm_node[][] initLibSVM(
      final double[][] probabilities,
      final int[][] labels) {
    svm_node[][] featuresTrain = new svm_node[probabilities.length][];
    for (int a = 0; a < probabilities.length; a++) {
      featuresTrain[a] = generateFeatures(probabilities[a], labels[a]);
    }
    return featuresTrain;
  }

  protected static double getMinDiff(double[] probabilities) {
    int maxId = 0;
    double max = 0.0;
    for (int i = 0; i < probabilities.length; i++) {
      if (probabilities[i] > max) {
        max = probabilities[i];
        maxId = i;
      }
    }

    double minDiff = 1.0;
    for (int i = 0; i < probabilities.length; i++) {
      if (maxId != i) {
        minDiff = Math.min(minDiff, max - probabilities[i]);
      }
    }

    return minDiff;
  }

  public static svm_node[] generateFeatures(final double[] probabilities, final int[] labels) {
    svm_node[] features = new svm_node[probabilities.length + 1];
    int maxLabel = 0;
    for (int i = 0; i < probabilities.length; i++) {
      features[i] = new svm_node();
      features[i].index = 2 + labels[i];
      features[i].value = probabilities[i];
      maxLabel = Math.max(features[i].index, maxLabel);
    }
    features[features.length - 1] = new svm_node();
    features[features.length - 1].index = maxLabel + 4;
    features[features.length - 1].value = getMinDiff(probabilities);

    Arrays.sort(features, new Comparator<svm_node>() {
      public int compare(svm_node o1, svm_node o2) {
        return Integer.compare(o1.index, o2.index);
      }
    });
    return features;
  }

}