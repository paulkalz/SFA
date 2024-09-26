// Copyright (c) 2016 - Patrick Schäfer (patrick.schaefer@zib.de)
// Distributed under the GLP 3.0 (See accompanying file LICENSE)
package sfa;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import sfa.classification.Classifier;
import sfa.classification.TEASERClassifier;
import sfa.classification.TEASERClassifierRealtime;
import sfa.timeseries.TimeSeries;
import sfa.timeseries.TimeSeriesLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.min;

@RunWith(JUnit4.class)
public class UCREarlyClassificationTestRealTime {
  
  // The datasets to use
  public static String[] datasets = new String[]{
          "Chinatown", // acc = 93%
          //"ECG200",
          //"GunPoint",
          //"SonyAIBORobotSurface1",
          //"DodgerLoopDay", // acc = 54%, weil heaperror
          //"EOGHorizontalSignal",
  };
  public static String[] ucr_freq_datasets = new String[]{
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
            //"DodgerLoopDay", // Heap Error // 1300 Hz
          "DodgerLoopGame",
          "DodgerLoopWeekend",
            //"Earthquakes", // datei ist ziemlich groß
          "ECG200",
            //"ECG5000", // datei ist ziemlich groß
          "ECGFiveDays",
            //"ElectricDevices", // datei ist ziemlich groß
            //"EOGHorizontalSignal", // datei ist ziemlich groß
            //"EOGVerticalSignal", // datei ist ziemlich groß
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

  HashMap<String, Double> datasetFrequencys = new HashMap<String, Double>() {{ // echte Samplingfrequenzen der Datasets in Hz
      put("Chinatown", 0.0002777);
      put("DodgerLoopDay", 0.00333);
      put("ECG200", 128.0);
      put("ECG5000", 250.0);
      put("EOGHorizontalSignal", 1000.0);
      put("EOGVerticalSignal", 1000.0);
      put("GunPoint", 30.0);
      put("Phoneme", 22050.0); // ?
      put("PLAID", 30000.0); // ?
      put("SonyAIBORobotSurface1", 123.0);
  }};

  // helper function
  public void printtooutputfile(String s) {
    try {
      Files.write(Paths.get("SFA\\plot_measurements\\measurements.txt"), s.getBytes(), StandardOpenOption.APPEND);
    }catch (IOException e) {
      System.out.println("Exception while writing to outputfile");
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

      for (String s : datasets) { 
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
              TEASERClassifierRealtime t = new TEASERClassifierRealtime();
              TEASERClassifierRealtime.S = 20.0;
              //TEASERClassifierRealtime.PRINT_EARLINESS = true;

              // Modell trainieren
              Classifier.Score scoreT = t.fit_and_measure(trainSamples); // fit_and_measure trainiert Teaser und misst die minimale prediction frequenz auf den trainingdaten (t.min_prediction_frequency)
              System.out.println(scoreT);

              double min_fitfrequency = t.min_prediction_frequency;
              double samplingrate = datasetFrequencys.getOrDefault(s, 0.0);
              System.out.println("Dataset samplingrate: " + samplingrate + "Hz | minimal fitting prediction frequency: " + min_fitfrequency + "Hz");
              printtooutputfile(s + " " + samplingrate + " " + min_fitfrequency + '\n');
              
              // Prediction
              System.out.println("Start the Prediction");
              for(int i = 0; i < 5; i++) {
                Double[][] pred = t.predict_and_measure_dataset(testSamples, samplingrate, "default"); // pred[0] ist die acc, pred[1] sind die frequenzen aller Samples
                System.out.println("Accuracy of the Datasetprediction(" + (i+1) + "): " + pred[0][0]);

                // log to Outputfile
                printtooutputfile(Arrays.toString(pred[1]));
                printtooutputfile("\n");
              }
              for(int i = 0; i < 5; i++) {
                Double[][] pred = t.predict_and_measure_dataset(testSamples, samplingrate, "odds"); // pred[0] ist die acc, pred[1] sind die frequenzen aller Samples
                System.out.println("Accuracy of the Datasetprediction(" + (i+1) + "): " + pred[0][0]);

                // log to Outputfile
                printtooutputfile(Arrays.toString(pred[1]));
                printtooutputfile("\n");
              }
            }
          }
        } else {
          // not really an error. just a hint:
          System.out.println("Dataset could not be found: " + d.getAbsolutePath() + ". " +
                  "Please download datasets from [http://www.cs.ucr.edu/~eamonn/time_series_data/].");
        }
      }
    } finally {
      TimeSeries.APPLY_Z_NORM = true; // FIXME static variable breaks some test cases!
    }
  }

  public static void main(String[] args) throws IOException {
    UCREarlyClassificationTestRealTime ucr = new UCREarlyClassificationTestRealTime();
    ucr.testUCRClassification();
  }
}
