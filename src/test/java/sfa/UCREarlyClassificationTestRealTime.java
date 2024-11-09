// Copyright (c) 2016 - Patrick Schäfer (patrick.schaefer@zib.de)
// Distributed under the GLP 3.0 (See accompanying file LICENSE)
package sfa;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import sfa.classification.Classifier;
import sfa.classification.TEASERClassifier;
import sfa.classification.TEASERClassifierRealtime;
import sfa.classification.TEASERClassifierRealtimeInst;
import sfa.classification.TEASERClassifierRealtimeManager;
import sfa.classification.TEASERClassifierRealtimeManager.predictionResults;
import sfa.timeseries.TimeSeries;
import sfa.timeseries.TimeSeriesLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static java.lang.Math.max;
import static java.lang.Math.min;

@RunWith(JUnit4.class)
public class UCREarlyClassificationTestRealTime {
  
  // The datasets to use
  public static String[] datasets = new String[]{
          "Chinatown", // acc = 93%
          "ECG200",
          "GunPoint",
          //"PLAID",
          //"SonyAIBORobotSurface1",
          //"DodgerLoopDay", // acc = 54%, weil heaperror
          //"EOGHorizontalSignal",
  };
  public static String[] ucr_freq_datasets = new String[]{ // 16h auf gruenau6
          "Chinatown",
          "DodgerLoopDay",
          "ECG200",
          "ECG5000",
          "EOGHorizontalSignal",
          "EOGVerticalSignal",
          "GunPoint",
          //"Phoneme",
          "PLAID",
          "SonyAIBORobotSurface1",
  };
  public static String[] datasets2 = new String[]{ 
          "ArrowHead",
          "Beef", 
          "BeetleFly",
          "BirdChicken",
          "Car",
          "CBF", 
          "Chinatown",
          "Coffee", 
          "Fish",
          "Fungi",
          "GunPoint", 
          "Ham",
          "Herring",
          "Meat",
          "OliveOil",
          "PickupGestureWiimoteZ", 
          "PLAID", 
          "Plane",
          "Trace",
          "Wine"
  };
  public static String[] datasets_ucr = new String[]{ 
            //"ACSF1", 
            //"Adiac", // Heap Error
            //"AllGestureWiimoteX", // Heap Error
            //"AllGestureWiimoteY", // Heap Error
            //"AllGestureWiimoteZ", // Heap Error
          "ArrowHead",
          "Beef", 
          "BeetleFly",
          "BirdChicken",
          "BME",
          "Car",
          "CBF", 
          "Chinatown",
            //"ChlorineConcentration", // datei ist ziemlich groß
            //"CinCECGTorso", // datei ist ziemlich groß
          "Coffee", 
            //"Computers", // datei ist ziemlich groß
            //"CricketX", // Heap Error
            //"CricketY", // Heap Error
            //"CricketZ", // Heap Error
            //"Crop", // 17000 Samples dauert zu lange
          "DiatomSizeReduction",
          "DistalPhalanxOutlineAgeGroup",
          "DistalPhalanxOutlineCorrect",
          "DistalPhalanxTW",
            "DodgerLoopDay", // Heap Error // 1300 Hz
          "DodgerLoopGame",
          "DodgerLoopWeekend",
            //"Earthquakes", // datei ist ziemlich groß
          "ECG200",
            //"ECG5000", // datei ist ziemlich groß
          "ECGFiveDays",
            //"ElectricDevices", // datei ist ziemlich groß
            "EOGHorizontalSignal", // datei ist ziemlich groß
            "EOGVerticalSignal", // datei ist ziemlich groß
            //"EthanolLevel", // datei ist ziemlich groß
            //"FaceAll", // datei ist ziemlich groß
          "FaceFour",
            //"FacesUCR", // datei ist ziemlich groß
            //"FiftyWords", // datei ist ziemlich groß
          "Fish",
            //"FordA", // datei ist ziemlich groß
            //"FordB", // datei ist ziemlich groß
            //"FreezerRegularTrain", // datei ist ziemlich groß
            //"FreezerSmallTrain", // datei ist ziemlich groß
          "Fungi",
            //"GestureMidAirD1", // Heap Error
            //"GestureMidAirD2", // Heap Error
            //"GestureMidAirD3", // Heap Error
            //"GesturePebbleZ1", // Heap Error
            //"GesturePebbleZ2", // Heap Error
          "GunPoint", 
          "GunPointAgeSpan",
          "GunPointMaleVersusFemale",
          "GunPointOldVersusYoung",
          "Ham",
            //"HandOutlines", // datei ist ziemlich groß
            //"Haptics", // datei ist ziemlich groß
          "Herring",
          "HouseTwenty",
            //"InlineSkate", // datei ist ziemlich groß
          "InsectEPGRegularTrain",
          "InsectEPGSmallTrain",
            //"InsectWingbeatSound", // datei ist ziemlich groß
          "ItalyPowerDemand",
            //"LargeKitchenAppliances", // datei ist ziemlich groß
          "Lightning2",
            //"Lightning7", // Heap Error
            //"Mallat", // datei ist ziemlich groß
          "Meat",
          "MedicalImages",
          "MelbournePedestrian",
          "MiddlePhalanxOutlineAgeGroup",
          "MiddlePhalanxOutlineCorrect",
          "MiddlePhalanxTW",
            //"MixedShapesRegularTrain", // datei ist ziemlich groß
            //"MixedShapesSmallTrain", // datei ist ziemlich groß
          "MoteStrain",
            //"NonInvasiveFetalECGThorax1", // datei ist ziemlich groß
            //"NonInvasiveFetalECGThorax2", // datei ist ziemlich groß
          "OliveOil",
            //"OSULeaf", // datei ist ziemlich groß
            //"PhalangesOutlinesCorrect", // datei ist ziemlich groß
            //"Phoneme", // datei ist ziemlich groß
            //"PickupGestureWiimoteZ", // Heap Error
            //"PigAirwayPressure", // datei ist ziemlich groß
            //"PigArtPressure", // datei ist ziemlich groß
            //"PigCVP", // datei ist ziemlich groß
            //"PLAID", // datei ist ziemlich groß
          "Plane",
          "PowerCons",
          "ProximalPhalanxOutlineAgeGroup",
          "ProximalPhalanxOutlineCorrect",
          "ProximalPhalanxTW",
            //"RefrigerationDevices", // datei ist ziemlich groß
            //"Rock", // Heap Error
            //"ScreenType", // datei ist ziemlich groß
            //"SemgHandGenderCh2", // datei ist ziemlich groß
            //"SemgHandMovementCh2", // datei ist ziemlich groß
            //"SemgHandSubjectCh2", // datei ist ziemlich groß
            //"ShakeGestureWiimoteZ", // Heap Error
          "ShapeletSim",
            //"ShapesAll", // datei ist ziemlich groß
            //"SmallKitchenAppliances", // datei ist ziemlich groß
          "SmoothSubspace",
          "SonyAIBORobotSurface1",
          "SonyAIBORobotSurface2",
            //"StarLightCurves", // datei ist ziemlich groß
            //"Strawberry", // datei ist ziemlich groß
            //"SwedishLeaf", // Heap Error
            //"Symbols", // datei ist ziemlich groß
          "SyntheticControl",
          "ToeSegmentation1",
          "ToeSegmentation2",
          "Trace",
          "TwoLeadECG",
            //"TwoPatterns", // datei ist ziemlich groß
          "UMD",
            //"UWaveGestureLibraryAll", // datei ist ziemlich groß
            //"UWaveGestureLibraryX", // datei ist ziemlich groß
            //"UWaveGestureLibraryY", // datei ist ziemlich groß
            //"UWaveGestureLibraryZ", // datei ist ziemlich groß
            //"Wafer", // datei ist ziemlich groß
          "Wine",
            //"WordSynonyms", // datei ist ziemlich groß
            //"Worms", // datei ist ziemlich groß
            //"WormsTwoClass", // datei ist ziemlich groß
            //"Yoga" // datei ist ziemlich groß
  };

  HashMap<String, Double[]> datasetFrequencys = new HashMap<String, Double[]>() {{ // echte Samplingfrequenzen der Datasets in Hz // und andere testfrequenzen
      put("Chinatown", new Double[] {0.0002777, 1000.0, 10000.0, 20000.0, 50000.0}); // 0.0002777
      put("DodgerLoopDay", new Double[] {0.00333, 1000.0, 10000.0, 20000.0, 50000.0});
      put("ECG200", new Double[] {128.0, 1000.0, 10000.0, 20000.0, 50000.0}); 
      put("ECG5000", new Double[] {250.0, 1000.0, 10000.0, 20000.0, 50000.0});
      put("EOGHorizontalSignal", new Double[] {1000.0, 1000.0, 20000.0, 10000.0, 50000.0});
      put("EOGVerticalSignal", new Double[] {1000.0, 1000.0, 10000.0, 20000.0, 50000.0});
      put("GunPoint", new Double[] {30.0, 1000.0, 10000.0, 20000.0, 50000.0});
      put("Phoneme", new Double[] {22050.0, 1000.0, 10000.0, 20000.0, 50000.0}); // ?
      put("PLAID", new Double[] {30000.0, 1000.0, 10000.0, 20000.0, 50000.0}); // ?
      put("SonyAIBORobotSurface1", new Double[] {123.0, 1000.0, 10000.0, 20000.0, 50000.0});
  }};

  // helper function
  public void printtooutputfile(String s) {
    try {
      Files.write(Paths.get("plot_measurements/measurements.txt").toAbsolutePath(), s.getBytes(), StandardOpenOption.APPEND);
    }catch (IOException e) {
      System.out.println("Exception while writing to outputfile: " + e);
    }
  }

  @Test
  public void testUCRClassification() throws IOException {
    try {
      // the relative path to the datasets
      ClassLoader classLoader = SFAWordsTest.class.getClassLoader();

      File dir = new File(classLoader.getResource("datasets/univariate/UCRArchive_2018").getFile()); 
      //File dir = new File("/Users/bzcschae/workspace/similarity/datasets/classification");

      TimeSeries.APPLY_Z_NORM = false;

      int n = datasets_ucr.length; // Anzahl der parallelen Streams
      ForkJoinPool customThreadPool = new ForkJoinPool(60); // gruenau6 hat 120 threads, ich darf maximal 50% davon verwenden
      customThreadPool.submit(() -> IntStream.range(0, n).parallel().forEach(stream -> {
      //IntStream.range(0, n).parallel().forEach(stream -> { // stream ist nur eine laufvariable
        String s = datasets_ucr[stream];
        
        System.out.println("Aktuelles Dataset: " + s);
        File d = new File(dir.getAbsolutePath() + "/" + s); 
        if (d.exists() && d.isDirectory()) { 
          for (File train : d.listFiles()) { 
            if (train.getName().toUpperCase().endsWith("TRAIN")) { 
              File test = new File(train.getAbsolutePath().replaceFirst("TRAIN", "TEST")); 
              if (!test.exists()) { 
                System.err.println("File " + test.getName() + " does not exist"); // error
                test = null;
              }
              Classifier.DEBUG = false;

              // Load the train/test splits
              TimeSeries[] testSamples = TimeSeriesLoader.loadDataset(test);
              TimeSeries[] trainSamples = TimeSeriesLoader.loadDataset(train);

              // The TEASER-classifier
              //TEASERClassifierRealtime t = new TEASERClassifierRealtime();

              //TEASERClassifierRealtime.S = max(20, testSamples[0].getLength() / 35); // ich will max 35 neue Datenpunkte pro snapshot und nicht weniger als 20 Snapshots
              //TEASERClassifierRealtime.S = 20;
              //System.out.println("S: " + TEASERClassifierRealtime.S);
              //TEASERClassifierRealtime.PRINT_EARLINESS = true;

              // Modell trainieren
              //Classifier.Score scoreT = t.fit_and_measure(trainSamples); // fit_and_measure trainiert Teaser und misst die minimale prediction frequenz auf den trainingdaten (t.min_prediction_frequency)
              //System.out.println(scoreT);
              try{
              // Prediction
              String loggingContent = "";
              loggingContent += "DATASET: " + s + "\n";
              // System.out.println("Start the Prediction"); 
              for(int j = 0; j < 1; j++) { // 1 Predictions pro Dataset
                for(int i = 0; i<5; i++) { // immer 5 predictions machen (mit unterschiedlichen Frequenzen)
                  //double test_frequency = ((datasetFrequencys.getOrDefault(s, new Double[] {})[1] - datasetFrequencys.getOrDefault(s, new Double[] {})[0])/5) * i + datasetFrequencys.getOrDefault(s, new Double[] {})[0]; // gleichverteilte werte zwischen den beiden angegebenen frequenzen
                  double test_frequency = datasetFrequencys.getOrDefault(s, new Double[] {100.0, 1000.0, 10000.0, 20000.0, 50000.0})[i];
                  //predict_and_output(testSamples, trainSamples, s, t, "realtime", test_frequency); // die zu erreichende frequenz steigt mit jeder iteration
                  
                  TEASERClassifierRealtimeManager manager = new TEASERClassifierRealtimeManager(testSamples, trainSamples, test_frequency);
                  predictionResults[][] result = manager.manageRealtime(); // do training, prediction and output
                  loggingContent += "NewDatasetFrequency:_" + s +" "+ test_frequency + " " + trainSamples[0].getLength() + "\n";
                  for(predictionResults[] strategy : result) {
                    //printtooutputfile(strategy[0].getTyp() + "\n"); // default, skipping, s, k
                     for(predictionResults variant : strategy) {
                       loggingContent += variant.getTyp() + " " + String.valueOf(variant.getAccuracy()) + " " + String.valueOf(variant.getEarlyness()) + " " + String.valueOf(variant.getPredictionTime()) + " " + String.valueOf(variant.getMinPredictionFrequency()) + "\n";
                        loggingContent += variant.getSnapshotTimesToString();
                      }
                    }
                }
              }
              printtooutputfile(loggingContent);
            } catch(OutOfMemoryError e) {
              System.out.println("HeapError in " + s + " " + e);
            }
            }
          }
        } else {
          // not really an error. just a hint:
          System.out.println("Dataset could not be found: " + d.getAbsolutePath() + ". " +
                  "Please download datasets from [http://www.cs.ucr.edu/~eamonn/time_series_data/].");
        }


      })).join(); // ab hier nicht mehr parallel
      System.out.println("Done");
    } finally {
      TimeSeries.APPLY_Z_NORM = true; // FIXME static variable breaks some test cases!
    }
  }

  public static void main(String[] args) throws IOException {
    UCREarlyClassificationTestRealTime ucr = new UCREarlyClassificationTestRealTime();
    ucr.testUCRClassification();
  }
}
