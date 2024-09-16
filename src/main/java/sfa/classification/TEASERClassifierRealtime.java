package sfa.classification;

import com.carrotsearch.hppc.*;
import com.carrotsearch.hppc.cursors.DoubleDoubleCursor;
import de.bwaldvogel.liblinear.SolverType;
import libsvm.*;
import sfa.timeseries.TimeSeries;

import java.util.*;

/**
 * TEASER: A framework for early and accurate times series classification
 * <p>
 *   Univariate classifier with WEASEL as slave
 * </p>
 */
public class TEASERClassifierRealtime extends Classifier {

  public double max_prediction_time = 0; // beim Training wird gemessen, wie schnell die prediction ist, damit der Classifier für die Prediction angepasst werden kann. (in milliseconds)

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

  public EarlyClassificationModel fitTeaser(final TimeSeries[] samples) {
    try {

      int min = Math.max(3, MIN_WINDOW_LENGTH); // ist 3
      int max = getMax(samples, MAX_WINDOW_LENGTH); // Integer.MAX_VALUE // ist min(maxwindowlen(250) oder die länge der längsten TS)
      double step = max / S; // steps of 5% // mindestens 12,5

      this.model = new EarlyClassificationModel();

      for (int s = 2; s <= S; s++) {
        // train TEASER
        model.offsets[s] = (int) Math.round(step * s); // model.offsets enthält bis zu welchem Datenpunkt klassifiziert werden soll? z.b. 25, 37, 50, 62, ... // in position 0 und 1 ist der Wert -1
        TimeSeries[] data = extractUntilOffset(samples, model.offsets[s], true); // kürzt alle TS bis zum Offset

        if (model.offsets[s] >= min) { // immer wahr
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

  // nur für Predictions einer einzelnen TS
  public Double[] predict_and_measure(final TimeSeries[] testSamples, double frequency) {
    // hier die Parameter anpassen

    OffsetPrediction temp = predict(testSamples, true);
    return new Double[]{temp.labels[0], temp.offset, (double) temp.correct, (double) temp.N};
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

    //System.out.print("OffsetArray: "); // DEBUG - um die Offsets anzuzeigen
    //for(int i = 0; i<model.offsets.length; i++) {
    //  System.out.print(model.offsets[i]);
    //  System.out.print(" / ");
    //}
    //System.out.println();
    //System.out.println(Arrays.toString(extractUntilOffset(testSamples, model.offsets[2], true)[0].getData()));

    predict:
    for (int s = 0; s < model.slaveModels.length; s++) { // S + 1 Wealsel (slaves) (im normalfall 21) mit jedem durchlauf wurde auf 5% mehr daten trainiert
      if (model.masterModels[s] != null) { // array mit trainierten oneClassSVMs (trainingsdaten in 5% schnitten ansteigend) // die ersten beiden werden nicht trainiert (0,1 sind null)
        // extract samples of length offset
        TimeSeries[] data = extractUntilOffset(testSamples, model.offsets[s], testing); // die ersten beiden werte sind -1 // ich habe immer nur 1 TS gleichzeitig

        // Ausgabe aktueller Snapshot
        if(testing) {System.out.println("My s: " + s);}
        if(testing) { // gekürzte TS ausgeben
          for(TimeSeries ts : data) {
            for(Double d : ts.getData()) {
              System.out.print(d);
              System.out.print(" ");
            }
          }
          System.out.println();
        }

        // Slave Classification
        this.slaveClassifier.setModel(model.slaveModels[s]); // TODO ugly
        long startTimeSlave = System.nanoTime();
        Predictions result = this.slaveClassifier.predictProbabilities(data); //  wahrscheinlichkeiten pro Klasse
        long estimatedTimeSlave = System.nanoTime() - startTimeSlave;
        //if(!testing) {max_prediction_time = max_prediction_time > estimatedTimeSlave ? max_prediction_time : (double) estimatedTimeSlave;} // längste prediction dauer speichern
        if(true) { // testing, sonst wird auch beim fitten die Zeit des Slaves ausgegeben
          System.out.print("Slave Duration: ");
          System.out.println((double) estimatedTimeSlave / 1000000); //TimeUnit.NANOSECONDS.toMillis(estimatedTime); // convert to milliseconds
        }

        for (int ind = 0; ind < data.length; ind++) { // data sind die TS aus testSamples aber gekürzt nach dem Offset // loop einmal pro TS
          if (predictedLabels[ind] == null) { // in den ersten beiden iterationen
            double predictedLabel = result.labels[ind]; // label von Weasel predicted
            double[] probabilities = result.probabilities[ind]; // die Wahrscheinlichkeiten aller Klassen von weasel predicted

            // Master Klassifikation // mit Zeitausgabe
            long startTimeMaster = System.nanoTime();
            double predictNow = svm.svm_predict(
                model.masterModels[s],
                generateFeatures(probabilities, result.realLabels)); // SVM berechnet, ob predicted wird // ist -1.0 oder 1.0 
            long estimatedTimeMaster = System.nanoTime() - startTimeMaster;
            if(testing) { // testing, sonst wird auch beim fitten die Zeit des Masters ausgegeben
              System.out.print("Master Duration: ");
              System.out.println((double) estimatedTimeMaster / 1000000); //TimeUnit.NANOSECONDS.toMillis(estimatedTime); // convert to milliseconds
            }

            if (s >= S // es soll eine prediction geben
                || model.offsets[s] >= testSamples[ind].getLength()
                || predictNow == 1
                ) {
              int counts = getCount(predictions[ind], predictedLabel); 
              if (counts >= model.threshold
                  || s >= S
                  || model.offsets[s] >= testSamples[ind].getLength()) { 
                predictedLabels[ind] = predictedLabel; // Prediction festlegen

                if(testing) {System.out.println("PREDICTION");}

                for(int i = 0; i < data.length; i++) {
                  if(testing) {
                    if(predictedLabels[i] == null) {
                      System.out.print("null ");
                    } else {
                      System.out.print(predictedLabels[i].toString() + " ");
                    }
                  }
                }
                if(testing) {System.out.println();}

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
          if (count == testSamples.length) { // stoppen, wenn alle TS eine prediction haben
            //if(testing) {System.out.println("PREDICTION");}
            break predict;
          }
        }
      }
    }

    // per Class counts
    if (testing) {
      for (DoubleDoubleCursor c : perClassEarliness) {
        System.out.println("Class\t" + c.key + "\t Earliness \t" + c.value / perClassCount.get(c.key));
      }
    }

    //System.out.println (Arrays.toString(predictedLabels));

    if (testing && PRINT_EARLINESS) {
      for (int ind = 0; ind < offsets.length; ind++) {
        int e = offsets[ind];
        System.out.print(predictedLabels[ind].toString() + "   " + testSamples[ind].getLabel().toString()); // debug
        System.out.print("[" + e + "," + (compareLabels(predictedLabels[ind], testSamples[ind].getLabel()) ? "True" : "False") + "],");
        System.out.print(""); // debug
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