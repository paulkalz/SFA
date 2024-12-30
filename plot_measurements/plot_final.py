import os
import pandas as pd
import numpy as np
from matplotlib import pyplot as plt

# Skript um die Grafiken aus den Messwerten zu erzeugen
# neuere variante

#dateipfad = os.path.abspath("plot_measurements/measurements.txt") # nutzt aktuelle logs
dateipfad = os.path.abspath("plot_measurements/measurements_clean_88datasets.txt")
#dateipfad = os.path.abspath("plot_measurements/measurements_clean_hyperparams.txt")

with open(dateipfad, 'r', encoding='utf-8') as datei:
    zeilen = datei.readlines()

zeilen = [zeile.strip() for zeile in zeilen]

#measurements = []
#for i in range(len(zeilen)): 
#    if (zeilen[i][0] != "[" and zeilen[i][0] != "-" and zeilen[i][0] != "+"): # neues Dataset beginnt
#        measurements = []
#        headerzeile = zeilen[i].split(" ")
#        plt.title(headerzeile[0] + " (" + headerzeile[1] + "Hz)")
#        plt.xlabel('Predictions')
#        plt.ylabel('Frequenz in Hz')
#        plt.grid(True)
#        plt.axhline(y=float(headerzeile[1]), color='r', linestyle='-') # reale samplingrate des datasets
#        plt.axhline(y=float(headerzeile[2]), color='g', linestyle='--') # während des fittings gemessene minimale frequenz
#    else:
#        if(zeilen[i][0] == "["):
#            measurements.append(list(map(float, zeilen[i][1:-1].split(", "))))
#    if i+1 >= len(zeilen): # für die letzte zeile
#        plt.boxplot(measurements)
#        plt.show()
#    else:
#        if zeilen[i+1] == "" or (zeilen[i+1][0] != "[" and zeilen[i+1][0] != '-' and zeilen[i+1][0] != "+"):
#          plt.boxplot(measurements)
#          plt.show()


def trim_zeros(arr):
    while arr and arr[-1] == 0:
        arr.pop()
    return arr

def fillArray(arr): # schneidet nullen am ende ab und ersetzt mittlere nullen durch den letzten messwert
    times = list(map(float, arr[1:-1].split(","))) #trim_zeros(list(map(float, arr[2:-1].split(", "))))
    letzerwert = None
    for j in range(len(times)): # die nullen werden durch den letzen gemessenen wert ersetzt
        if times[j] != 0:
            letzerwert = times[j]
        else:
            if letzerwert is not None:
                times[j] = letzerwert
    return times

def trim_duplicates(arr):
    last_value = arr[-1]
    count = 0
    for value in reversed(arr): # duplikate zählen
        if value == last_value:
            count += 1
        else:
            break
    if count > 1:
        return arr[:-count + 1]  # doppelte werte abschneiden
    return arr # keine doppelten werte am ende

def getPredictionTime(ts):
    arr = list(map(float, ts[1:-1].split(","))) #trim_zeros(list(map(float, arr[2:-1].split(", "))))
    for zahl in reversed(arr):
        if zahl != 0:
            return zahl
    return arr[0]

def getClassificationFrequency(ts, ts_len):
    time = getPredictionTime(ts) / 1000 # time in seconds
    arr = list(map(float, ts[1:-1].split(","))) #trim_zeros(list(map(float, arr[2:-1].split(", "))))
    usedPoints = len(processTS(ts, ts_len))-1
    return usedPoints / time # returns frequency in hz

def getRealtimeTime(used_datapoints, samplingrate): # zeit, die für used_datapoints Punkte bei Klassifikation in der samplingrate gebraucht wird (in ms)
    return used_datapoints / (samplingrate / 1000)

def skipping_plot():
    measurements = []
    counter = 1;
    for i in range(len(zeilen)): 
        if (zeilen[i][0] != "[" and zeilen[i][0] != "-" and zeilen[i][0] != "+"): # neues Dataset beginnt
            measurements = []
            headerzeile = zeilen[i].split(" ")
            plt.subplot(2,5,counter)
            counter +=1
            plt.title(headerzeile[0] + " (" + headerzeile[1] + "Hz)\n" + headerzeile[9] + "ms " + headerzeile[10] + "ms\n" + headerzeile[5] + " " + headerzeile[6] + " \n" + headerzeile[7] + " " + headerzeile[8])
            plt.subplots_adjust(hspace=0.5)
            plt.xlabel('Snapshots')
            plt.ylabel('Predictiontime in Ms')
            plt.grid(True)
            richtlinie = []
            richtlinie.append(0)
            for j in range(1, int(float(headerzeile[4]))+1): # 3=laeenge 4=S
                richtlinie.append(((float(headerzeile[3]) / float(headerzeile[4])) * j) / (float(headerzeile[1])/ 1000)) # ms an dieser stelle bei samplingrate
            plt.plot(richtlinie, color='black', linestyle='--')
        else:
            if(zeilen[i][0] == "-" or zeilen[i][0] == "+"):
                times = trim_zeros(list(map(float, zeilen[i][2:-1].split(", "))))
                letzerwert = None;
                for j in range(len(times)): # die nullen werden durch den letzen gemessenen wert ersetzt
                    if times[j] != 0:
                        letzerwert = times[j]
                    else:
                        if letzerwert is not None:
                            times[j] = letzerwert
                plt.plot(times, linewidth=1, color='red' if zeilen[i][0]=='-' else 'blue')
        if i+1 >= len(zeilen): # für die letzte zeile
            plt.show()
            counter=1
        else:
            if zeilen[i+1] == "" or (zeilen[i+1][0] != "[" and zeilen[i+1][0] != '-' and zeilen[i+1][0] != "+"):
                if counter== 11: 
                    plt.show()   
                    counter = 1
#skipping_plot()

# S werte müssen in gleichen abständen in ein array der länge ts_len eingetragen werden
def snapshotTStoPointTS(arr, S, ts_len): # duch das neue arr gehen, nach den punken eines snapshots einen neuen wert einfügen
    step = float(ts_len) / (S-1) 
    offsets = []
    for i in range(0, S+1):
        offsets.append(int(step * i))
    #print(str(S-1) + " " + str(ts_len) +" "+ str(len(arr)) + " " + str(step))
    offsetstr = " ".join(str(x) for x in offsets)
    #print(offsetstr + " " + str(step))
    result = []
    result.append(arr[0]) # die Zeit eines Snapshots wird nach seinem letzten Punkt angerechnet
    temp = 1
    for i in range(1, int(float(ts_len))+1): # +1 weil die dauer des letzten snapshots erst nach dem letzten Datenpunkt eingetragen wird
        if i in offsets:
            result.append(arr[temp])
            temp += 1
        else:
            result.append(result[i-1])
    return result

def processTS(arr, ts_len): # input arr ist der string eines arrays (eine zeile)
    # füllt lücken in der messung, verteilt die snapshotmessungen auf die datenpunkte, schneidet messungen nach dem klassifikationszeitpunkt ab
    #print("new")
    #print(arr)
    res = fillArray(arr)
    #print(res)
    #print(snapshotTStoPointTS(res, len(res), ts_len))
    #print(len(snapshotTStoPointTS(res, len(res), ts_len)))
    #print(trim_duplicates(snapshotTStoPointTS(res, len(res), ts_len)))
    #print(len(trim_duplicates(snapshotTStoPointTS(res, len(res), ts_len))))
    return trim_duplicates(snapshotTStoPointTS(res, len(res), ts_len)) # S = (arr) da wir eine messung pro snapshot haben

#print(len(processTS("[0.010815, 0.020696, 0.490537, 1.527674, 3.152528, 5.39828, 9.24487, 15.081347000000001, 21.599874, 31.106322, 43.4644]", 288)))

def getUsedPoints(arr, ts_len, k): # funktioniert auf allen testfällen # besser als die vorherige version
    if list(map(float, arr[1:-1].split(",")))[len(list(map(float, arr[1:-1].split(","))))-1] != 0:
        return float(ts_len) # auch unschöner fix, wenn der letzte Snapshot genutzt wurde, wurden alle datenpunkte genutzt

    res = fillArray(arr)
    S = len(res)-1
    step = float(ts_len) / S
    step = step / k
    offsets = []
    for i in range(0, S+1):
        offsets.append(int((step * i)+0.5)) # + 0.5 ist zum runden auf den nähesten int
    #print(str(S) + " " + str(ts_len) +" "+ str(len(arr)) + " " + str(step))
    offsetstr = " ".join(str(x) for x in offsets)
    #print(offsetstr + " " + str(step))
    return min( offsets[len(trim_duplicates(res))-1] * k, float(ts_len)) # min ist kein guter fix, aber besser als davor

#print(getUsedPoints("[0.6598, 0.6639, 0.7144, 0.8585, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0]", 24))
#print(getUsedPoints("[0.002626, 0.005236, 0.007791, 0.010327, 0.012876, 0.015396, 0.017894, 0.020491, 0.023003, 0.02552, 0.028077, 0.0306, 0.033245, 0.036221, 0.038791, 0.041362, 0.043953, 0.046536, 0.049416, 0.051965, 0.054526]", 24, 10))
#print(getUsedPoints("[1.1321, 1.2134, 1.2195, 1.2248, 1.2291, 1.3096, 1.551, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0]", 24, 2))
# 0 0 1 2 2 3 4 4 5 5 6 7 7 8 8 9 10 10 11 11 12

def add_lists(list1, list2):
    max_length = max(len(list1), len(list2))
    result = []
    for i in range(max_length):
        value1 = list1[i] if i < len(list1) else 0
        value2 = list2[i] if i < len(list2) else 0
        result.append(value1 + value2)
    return result

def countInd(liste, counter_list):
    for i in range(len(liste)):
        counter_list[i] += 1
    return counter_list

def plotAverageMeasurement(startline, ts_len,  color, label): # plots a ts with the averaged values for every processed ts in a measurement # sollte ungekürzte ts verwenden
    ts_len = int(ts_len)
    print("seaching measurement values (and averaging them) beginning from line: " + str(startline))
    ts = [0] * (ts_len+1)
    lines_counter = 0
    #print(str(ts_len) + " " + str(len(lines_counter)) + " " + str(len(ts)))
    for i in range(startline+1, len(zeilen)): # alle messwerte dieser messung werden gesammelt
        if(zeilen[i][0] == "T"):
            break
        if(zeilen[i][0] == "["):
            temp_ts = fillArray(zeilen[i])
            temp = snapshotTStoPointTS(temp_ts, len(temp_ts), ts_len)
            #lines_counter = countInd(temp, lines_counter)
            lines_counter += 1
            ts = add_lists(ts, temp)
    # hier muss die summe geteilt werden
    #print(str(ts_len) + " " + str(len(lines_counter)) + " " + str(len(ts)))
    #print(ts)
    for x in range(len(ts)):
        ts[x] = ts[x] / lines_counter
    # dann geplotted
    plt.plot(trim_duplicates(ts), linewidth=1,  color=color, label=label)


def plotAverageDefault(startline, ts_len_in, color, label): # bei den average graphen gibt es spikes, wenn viele ts fertig werden (nicht gut, graph muss monoton sein)
    ts_len = int(ts_len_in)
    print("seaching default teaser values beginning from line: " + str(startline))
    ts = [0] * ts_len
    lines_counter = 0
    for i in range(startline, len(zeilen)):
        if(zeilen[i].split(" ")[0] == "TEASER_Default"): # default teaser wurde gefunden
            for j in range(i+1, len(zeilen)): # alle werte plotten
                if(zeilen[j][0] == "T"):
                    break
                if(zeilen[j][0] == "["):
                    temp_ts = fillArray(zeilen[j])
                    temp = snapshotTStoPointTS(temp_ts, len(temp_ts), ts_len)
                    #lines_counter = countInd(temp, lines_counter)
                    lines_counter += 1
                    ts = add_lists(ts, temp)
            # hier muss die summe geteilt werden
            #print(str(ts_len) + " " + str(len(lines_counter)) + " " + str(len(ts)))
            for x in range(len(ts)):
                ts[x] = ts[x] / lines_counter
            # dann geplotted
            plt.plot(trim_duplicates(ts), linewidth=1, color=color, label=label)
            break

def plotWorstMeasurement(startline, ts_len, color, label): # ist mir erstmal wichtiger als der average
    print("seaching worst measurement values beginning from line: " + str(startline))
    max = 0;
    worstline = 0;
    for i in range(startline+1, len(zeilen)):
        if(zeilen[i][0] == "T"):
            break
        if(zeilen[i][0] == "["):
            temp = getPredictionTime(zeilen[i])
            if(temp > max):
                max = temp
                worstline = i
    plt.plot(processTS(zeilen[worstline], ts_len), linewidth=1, color=color, label=label)

def plot_realtimeline(samplingrate, S, ts_len): # alle inputs als string // S sollte auch Datenpunkte sein können / für STeaser muss die x achse über punkte laufen um s und default zu vergleichen
    richtlinie = []
    richtlinie.append(0)
    for j in range(1, int(float(S))+1): # für jeden snapshot
        richtlinie.append(((float(ts_len) / float(S)) * j) / (float(samplingrate)/ 1000)) # ms an dieser stelle bei samplingrate
    plt.plot(richtlinie, color='black', linestyle='--')

def plot_default(startline): # input: eine feste frequenz, default, skipping, sx5, kx5 // output: default vs skipping, default vs sx5, default vs kx5
    print("seaching default teaser values beginning from line: " + str(startline))
    for i in range(startline, len(zeilen)):
        if(zeilen[i].split(" ")[0] == "TEASER_Default"): # default teaser wurde gefunden
            for j in range(i+1, len(zeilen)): # alle werte plotten
                if(zeilen[j][0] == "T"):
                    break
                if(zeilen[j][0] == "["):
                    plt.plot(fillArray(zeilen[j]), linewidth=1, color='blue')
            break
    #plt.show()

def plot_default_points(startline, S, ts_len): # input: eine feste frequenz, default, skipping, sx5, kx5 // output: default vs skipping, default vs sx5, default vs kx5
    print("seaching default teaser values beginning from line: " + str(startline))
    for i in range(startline, len(zeilen)):
        if(zeilen[i].split(" ")[0] == "TEASER_Default"): # default teaser wurde gefunden
            for j in range(i+1, len(zeilen)): # alle werte plotten
                if(zeilen[j][0] == "T"):
                    break
                if(zeilen[j][0] == "["):
                    #print(fillArray(zeilen[j]))
                    #print(snapshotTStoPointTS(fillArray(zeilen[j]), S, ts_len))
                    plt.plot(processTS(zeilen[j], ts_len), linewidth=1, color='blue')
            break
    #plt.show()

def plot_first_default_point(startline, ts_len):
    print("seaching default teaser values beginning from line: " + str(startline))
    for i in range(startline, len(zeilen)):
        if(zeilen[i].split(" ")[0] == "TEASER_Default"): # default teaser wurde gefunden
            for j in range(i+1, len(zeilen)): # alle werte plotten
                if(zeilen[j][0] == "T"):
                    break
                if(zeilen[j][0] == "["):
                    #print(fillArray(zeilen[j]))
                    #print(snapshotTStoPointTS(fillArray(zeilen[j]), S, ts_len))
                    plt.plot(processTS(zeilen[j], ts_len), linewidth=1, color='blue')
                    break
            break
    #plt.show()

def plot_first_measurement_points(startline, ts_len): # input: eine feste frequenz, default, skipping, sx5, kx5 // output: default vs skipping, default vs sx5, default vs kx5
    print("seaching measurement values beginning from line: " + str(startline))
    for i in range(startline+1, len(zeilen)):
        if(zeilen[i][0] == "T"):
            break
        if(zeilen[i][0] == "["):
            plt.plot(processTS(zeilen[i], ts_len), linewidth=1, color='red')
            break

def plot_measurement(startline): # input: eine feste frequenz, default, skipping, sx5, kx5 // output: default vs skipping, default vs sx5, default vs kx5
    print("seaching measurement values beginning from line: " + str(startline))
    for i in range(startline+1, len(zeilen)):
        if(zeilen[i][0] == "T"):
            break
        if(zeilen[i][0] == "["):
            plt.plot(fillArray(zeilen[i]), linewidth=1, color='red')
    #plt.show()

def plot_measurement_points(startline, ts_len): # input: eine feste frequenz, default, skipping, sx5, kx5 // output: default vs skipping, default vs sx5, default vs kx5
    print("seaching measurement values beginning from line: " + str(startline))
    for i in range(startline+1, len(zeilen)):
        if(zeilen[i][0] == "T"):
            break
        if(zeilen[i][0] == "["):
            plt.plot(processTS(zeilen[i], ts_len), linewidth=1, color='red')

dataset_counter = 0; # startzeile des aktuellen datasets
current_dataset_headline = ""
current_dataset_headline_index = 0
def four_strategies_plot(): # x-Achse sind datapoints, y-Achse sind ms
    counter = 0; # subplot index
    for i in range(0, len(zeilen)):
        if(zeilen[i][0] == "N"): # neues Dataset beginnt
            print("new Dataset found: " + zeilen[i])
            plt.suptitle(zeilen[i].split("_")[1] + "\n ")
            current_dataset_headline = zeilen[i]
            current_dataset_headline_index = i
        if (zeilen[i][0] == "T"): # neue messung beginnt
            counter += 1
            plt.subplot(5,6, counter)
            headerzeile = zeilen[i].split(" ")
            plt.title(headerzeile[0] + "\n" + headerzeile[1][:4] + " acc " + headerzeile[2][:4] + " early \n" + headerzeile[3][:4] + "avg. ms " + headerzeile[4].split(".")[0] + "min_Hz")
            plt.subplots_adjust(hspace=0.5)
            plt.ylabel('Predictiontime in ms')
            plt.grid(True)
            plt.xlabel('Datapoints')
            ts_len = current_dataset_headline.split(" ")[2]
            plot_default_points(current_dataset_headline_index, 21, ts_len)
            #plot_first_default_point(current_dataset_headline_index, ts_len)
            #plotAverageDefault(current_dataset_headline_index, ts_len, "yellow", zeilen[i].split(" ")[0])
            if not zeilen[i-1] == current_dataset_headline: # der erste subplot sind nur die default werte
                plot_measurement_points(i, ts_len)
                #plot_first_measurement_points(i, ts_len)
                #plotAverageMeasurement(i, ts_len, "red", zeilen[i].split(" ")[0])
            plot_realtimeline(current_dataset_headline.split(" ")[1], ts_len, ts_len) # wenn S == len(TS), dann ist die x-Achse datapoints
        if counter == 30: # neue Grafik anfangen
            counter = 0
            plt.show()
            
    plt.show()

#four_strategies_plot() # average oder punkte auskommentieren

dataset_counter = 0; # startzeile des aktuellen datasets
current_dataset_headline = ""
current_dataset_headline_index = 0
def default_plot(): # x-Achse sind datapoints, y-Achse sind ms
    counter = 0; # subplot index
    for i in range(len(zeilen)):
        if(zeilen[i][0] == "N"): # neues Dataset beginnt (eigentlich neue frequenz)
            print("new Dataset found: " + zeilen[i])
            plt.suptitle(zeilen[i].split("_")[1] + "\n ")
            current_dataset_headline = zeilen[i]
            current_dataset_headline_index = i
        if (zeilen[i].split(" ")[0] == "TEASER_Default"): # neue messung beginnt
            counter += 1
            plt.subplot(4,5, counter)
            headerzeile = zeilen[i].split(" ")
            plt.title(current_dataset_headline.split("_")[1] + "\n" + headerzeile[1][:4] + " acc " + headerzeile[2][:4] + " early \n" + headerzeile[3][:4] + "avg. ms " + headerzeile[4].split(".")[0] + "Hz")
            plt.subplots_adjust(hspace=0.5)
            plt.ylabel('Predictiontime in ms')
            plt.grid(True)
            plt.xlabel('Datapoints')
            ts_len = current_dataset_headline.split(" ")[2]
            plot_default_points(current_dataset_headline_index, 21, ts_len)
            #plot_first_default_point(current_dataset_headline_index, ts_len)
            #plotAverageDefault(current_dataset_headline_index, ts_len, "yellow", zeilen[i].split(" ")[0])
            if not zeilen[i-1] == current_dataset_headline: # der erste subplot sind nur die default werte
                #plot_measurement_points(i, ts_len)
                plot_first_measurement_points(i, ts_len)
                #plotAverageMeasurement(i, ts_len, "red", zeilen[i].split(" ")[0])
            plot_realtimeline(current_dataset_headline.split(" ")[1], ts_len, ts_len) # wenn S == len(TS), dann ist die x-Achse datapoints
        if counter == 20: # neue Grafik anfangen
            counter = 0
            plt.show()
            
    plt.show()

#default_plot()

dataset_counter = 0; # startzeile des aktuellen datasets
current_dataset_headline = ""
current_dataset_headline_index = 0;
def plot_dataset(): # plot eines Datasets, ein Diagramm pro frequenz mit allen Methoden (worst ts)
    counter = 0; # subplot index
    methods_counter = 0;
    colors = ["red", "yellow", "green", "black", "magenta"]
    for i in range(len(zeilen)):
        if(zeilen[i][0] == "D"):
            print("new Dataset found: " + zeilen[i])
            counter = 0
            methods_counter = 0
            if(not i == 0):
                plt.legend(loc="upper left")
                plt.show()
        if(zeilen[i][0] == "N"): # neues Dataset beginnt (neue frequenz)
            print("new Frequency found: " + zeilen[i])
            plt.suptitle(zeilen[i].split("_")[1].split(" ")[0])
            current_dataset_headline = zeilen[i]
            current_dataset_headline_index = i
            counter += 1
            methods_counter = 0
            plt.subplot(1, 5, counter)
            headerzeile = zeilen[i].split(" ")
            plt.title(headerzeile[1] + "Hz")
            plt.subplots_adjust(hspace=0.5)
            plt.ylabel('Predictiontime in ms')
            plt.grid(True)
            plt.xlabel('Datapoints')
            ts_len = current_dataset_headline.split(" ")[2]
            plot_realtimeline(current_dataset_headline.split(" ")[1], ts_len, ts_len) # wenn S == len(TS), dann ist die x-Achse datapoints
            plotWorstMeasurement(current_dataset_headline_index+1, ts_len, "blue", "TEASER_Default") # worst ts from default teaser
            #plot_first_default_point(current_dataset_headline_index, ts_len)
            #plotAverageDefault(current_dataset_headline_index, ts_len, "blue", "TEASER_Default")
        if (zeilen[i][0] == "T"): # neue messung beginnt (neue teaser methode)
            if not zeilen[i-1] == current_dataset_headline: # der erste subplot sind nur die default werte
                plotWorstMeasurement(i, ts_len, colors[methods_counter], zeilen[i].split(" ")[0])
                #plot_first_measurement_points(i, ts_len)
                #plotAverageMeasurement(i, ts_len, colors[methods_counter], zeilen[i].split(" ")[0])
                methods_counter += 1
        if counter == 6: # anzahl der frequenzen pro dataset // neue Grafik anfangen
            counter = 0
            plt.legend(loc="upper left")
            plt.show()
    plt.legend(loc="upper left")
    plt.show()

#plot_dataset() # avg. oder worst auskommentieren


# Anteil der TS, die in realtime klassifiziert wurden
def getRealtimePercentage(startline, samplingrate, ts_len): # startline muss der Beginn einer neuen Messmethode sein // zeile muss mit "T" beginnen
    counter = 0
    realtime_counter = 0
    for i in range(startline+1, len(zeilen)):
        if(zeilen[i][0] != "["):
            break
        else:
            counter += 1
            processed_ts = processTS(zeilen[i], ts_len)
            used_datapoints = len(processed_ts)-1
            used_time = processed_ts[-1]
            if used_time <= getRealtimeTime(used_datapoints, samplingrate):
                realtime_counter += 1 # diese TS wurde in Realtime Klassifiziert
            #print(processed_ts)
            #print(str(samplingrate) + " " + str(ts_len) + " " + str(used_datapoints) + " " + str(used_time) + " " + str(getRealtimeTime(used_datapoints, samplingrate)))
    return realtime_counter / counter

def getAvgPredictionTime(startline):
    avgPredictionTime = 0
    counter = 0
    for i in range(startline+1, len(zeilen)):
        if(zeilen[i][0] != "["):
            break
        else:
            counter += 1
            avgPredictionTime += getPredictionTime(zeilen[i])
    return avgPredictionTime / counter

def getAvgPredictionFrequency(startline, ts_len):
    avgPredictionFrequency = 0
    counter = 0
    for i in range(startline+1, len(zeilen)):
        if(zeilen[i][0] != "["):
            break
        else:
            counter += 1
            processed_ts = processTS(zeilen[i], ts_len)
            used_datapoints = len(processed_ts)-1
            used_time = processed_ts[-1]
            ts_frequency = used_datapoints / (used_time / 1000)
            #print(str(ts_frequency) + " " + str(getClassificationFrequency(zeilen[i], ts_len))) # zum test
            if(ts_frequency < 1000000): # bei kurzen TS mit großem K enthält der erste Snapshot alle Datenpunkte -> sofort Klassifizieren
                #print(zeilen[i])
                #print(i)
                #print(processed_ts)
                #print(used_datapoints)
                #print(used_time/1000)
                continue
            counter += 1
            avgPredictionFrequency += ts_frequency
    return avgPredictionFrequency / counter



def getDatasetScores(startline): # acc, early, realtime_percentage pro Methode pro testfrequenz // für gesamtaussagen // startline = index der zeile mit "DATASET: "
    # Diese Methode sammelt nur die daten eines Datensets, andere Methoden machen dann die Statistik
    print("seaching Statistical Data: " + str(int((startline / len(zeilen))*100)) + "%")
    res = []
    current_frequency = 0
    dataset_name = zeilen[startline].split(" ")[1]
    ts_len = zeilen[startline+1].split(" ")[2]
    for i in range(startline+1, len(zeilen)):
        if(zeilen[i][0] == "N"): # neues Dataset beginnt (neue frequenz)
            current_frequency = zeilen[i].split(" ")[1]
        if(zeilen[i][0] == "T"): # neue Methode beginnt (in dieser Zeile sind alle Informationen)
            stats_line = zeilen[i].split(" ")
            name = stats_line[0].split("(")[0]
            minPredictionFrequency = float(stats_line[4]) # nicht besonders gut darzustellen
            avgPredictionFrequency = getAvgPredictionFrequency(i, ts_len)
            if len(stats_line[0].split("(")) > 1:
                name = stats_line[0].split("(")[0][:-1]
            res.append([dataset_name, current_frequency, name, float(stats_line[1]), float(stats_line[2]), getRealtimePercentage(i, float(current_frequency), ts_len), getAvgPredictionTime(i), avgPredictionFrequency]) # [dataset, freq, method, acc, earl, realtime_percentage, avgPredictionTime]
        if(zeilen[i][0] == "D"):
            break # das nächste Dataset beginnt
    
    return res

def getAllDatasetScores(): # aus dem result einen dataframe machen
    res = []
    for i in range(len(zeilen)):
        if zeilen[i][0] == "D":
            res += getDatasetScores(i)
    return res

def calculateAverageBarchart(): #  min_frequency ist ein sehr schlechter wert, hat wenig aussagekraft
    df = pd.DataFrame(getAllDatasetScores(), columns=['Dataset', 'frequency', 'Method', 'Accuracy', 'Earliness', 'realtime_percentage', 'avg_PredictionTime', 'avgPredictionFrequency'])
    df['Accuracy'] = df['Accuracy'].astype(float)
    df['Earliness'] = df['Earliness'].astype(float)
    df['realtime_percentage'] = df['realtime_percentage'].astype(float)
    df['avg_PredictionTime'] = df['avg_PredictionTime'].astype(float)
    df['avgPredictionFrequency'] = df['avgPredictionFrequency'].astype(float)
    #print(df.head(50))
    #print(df.groupby(['frequency', 'Method']).agg({'Accuracy': ['mean'], 'Earliness': ['mean'], 'realtime_percentage': ['mean'], 'avg_PredictionTime': ['mean']}).head(50))
    grouped_df = df.groupby(['frequency', 'Method'], as_index=False).agg({'Accuracy': ['mean'], 'Earliness': ['mean'], 'realtime_percentage': ['mean'], 'avg_PredictionTime': ['mean'], 'avgPredictionFrequency': ['min']})
    grouped_df = grouped_df.drop(['avg_PredictionTime'], axis=1) # Prediction time ist nicht auf der glechen Skala (0 bis 1, wie die anderen werte)
    grouped_df = grouped_df.drop(['avgPredictionFrequency'], axis=1) # auch nicht gleiche Skala
    for i in df['frequency'].unique():
        frequency_group_df = grouped_df.loc[grouped_df['frequency'] == i].drop(['frequency'], axis=1).sort_values(by=['Method'], ascending=True)
        #print(frequency_group_df.head(50))
        # plot grouped bar chart 
        frequency_group_df.plot(x='Method', 
                kind='bar', 
                stacked=False, 
                title=''+i+'Hz',
                rot=0)
        #plt.show()

#calculateAverageBarchart()

def calculateAverageBarchart_twoAxis(): #  acc, early, avg_predictionTime # Diese Grafik von einzelnen Datasets macht keinen sinn
    df = pd.DataFrame(getAllDatasetScores(), columns=['Dataset', 'frequency', 'Method', 'Accuracy', 'Earliness', 'realtime_percentage', 'avg_PredictionTime', 'avgPredictionFrequency'])
    df['Accuracy'] = df['Accuracy'].astype(float)
    df['Earliness'] = df['Earliness'].astype(float)
    df['realtime_percentage'] = df['realtime_percentage'].astype(float)
    df['avg_PredictionTime'] = df['avg_PredictionTime'].astype(float)
    #print(df.head(50))
    #print(df.groupby(['frequency', 'Method']).agg({'Accuracy': ['mean'], 'Earliness': ['mean'], 'realtime_percentage': ['mean'], 'avg_PredictionTime': ['mean']}).head(50))
    grouped_df = df.groupby(['frequency', 'Method'], as_index=False).agg({'Accuracy': ['mean'], 'Earliness': ['mean'], 'realtime_percentage': ['mean'], 'avg_PredictionTime': ['mean'], 'avgPredictionFrequency': ['mean']})
    #grouped_df = grouped_df.drop(['avg_PredictionTime'], axis=1) # Prediction time ist nicht auf der glechen Skala (0 bis 1, wie die anderen werte)
    for i in df['frequency'].unique():
        frequency_group_df = grouped_df.loc[grouped_df['frequency'] == i].drop(['frequency'], axis=1).sort_values(by=['Method'], ascending=True)
        normal_group_df = frequency_group_df.drop(['avg_PredictionTime'], axis=1) # alle aches sind auf einer skala
        #print(frequency_group_df.head(50))
        # plot grouped bar chart
        #fig = plt.figure()
        #ax = fig.add_subplot(111)
        #ax2 = ax.twinx()
        #width = 0.4
        #fig.suptitle(''+i+'Hz')
        #normal_group_df.plot(x='Method', kind='bar', stacked=False, ax=ax, position=0, width=width, rot=0)
        #frequency_group_df.avg_PredictionTime.plot(kind='bar', color='red', ax=ax2, width=width/3, position=-3)

        fig = plt.figure()
        ax = fig.add_subplot(111)
        width = 0.1
        fig.suptitle(''+i+'Hz')
        frequency_group_df.plot(x='Method', y='Accuracy', kind='bar', color='red', stacked=False, ax=ax, rot=0, position=2, width=width)
        frequency_group_df.plot(x='Method', y='Earliness', kind='bar', color='blue', ax=ax, rot=0, position=1, width=width)
        frequency_group_df.plot(x='Method', y='realtime_percentage', kind='bar', color='green', ax=ax, rot=0, position=0, width=width)
        #ax.legend(['Accuracy', 'Earliness', 'realtime_percentage'])
        ax2 = ax.twinx()
        frequency_group_df.plot(x='Method', y='avg_PredictionTime', kind='bar', color='purple', ax=ax2, rot=0, position=-1, width=width)
        #frequency_group_df.plot(x='Method', y='avgPredictionFrequency', kind='bar', color='black', ax=ax2, rot=0, position=-2, width=width)
        #ax2.legend(['avg_PredictionTime'])
        ax.set_ylabel('Accuracy / Earliness / RealtimePercentage')
        ax2.set_ylabel('avg. PredictionTime in Milliseconds')
        # Legenden kombinieren
        #labels1 = ['Accuracy', 'Earliness', 'Realtime Percentage']
        #labels2 = ['Avg_PredictionTime']
        handles1, labels1 = ax.get_legend_handles_labels()
        handles2, labels2 = ax2.get_legend_handles_labels()

        # Kombinierte Legende erstellen
        ax2.legend(handles1 + handles2, ['Accuracy', 'Earliness', 'Realtime Percentage', 'PredictionTime (avg.)'], loc='upper right')#, bbox_to_anchor=(1, 1), bbox_transform=fig.transFigure)
        ax.get_legend().remove()
        ax.set_xlim(-0.5, len(frequency_group_df) - 0.5) # Setze die x-Achsen-Grenzen
        #plt.legend() # die legende ist an zufälliger Stelle, und hat keine einträge, neue farben wählen, alle bars in den frame rücken
        #plt.tight_layout(rect=[0,0,1,0.9])
        plt.show()

#calculateAverageBarchart_twoAxis()


#four_strategies_plot() # def_skip_s_sskip_k_kskip_pointsForMeasurement

###################################################################################

#---- Dataset ist geladen ----

def correctName(name):
    if name.find("_") < 0:
        return name.split(" ")[0] + "\n" + name.split(" ")[1] + "Hz"
    elif name == "TEASER_Default":
        return "Default-TEASER"
    elif name == "TEASER_Skip":
        return "Skipping-TEASER"
    elif name.split("_")[1] == "S":
        return "S-TEASER " + name.split("_")[2]
    elif name.split("_")[1] == "K":
        return "K-TEASER " + name.split("_")[2]
    elif name.split("_")[1] == "SSkip":
        return "Skipping-S-TEASER " + name.split("_")[2]
    elif name.split("_")[1] == "KSkip":
        return "Skipping-K-TEASER " + name.split("_")[2]
    else:
        return "Error"

# habe diese methode nachgerechnet, funktioniert
def getTimeliness(startline, frequenz, ts_len): # startline ist der index der zeile, die mit T beginnt # die methode berechnet also die durchschnittliche timeliness einer teaservariante in einer frequenz in einem datensatz
    counter = 0
    timeliness = 0
    for i in range(startline+1, len(zeilen)):
        if(zeilen[i][0] != "["):
            break
        else:
            counter += 1
            k = 1 # default case
            if(zeilen[startline][zeilen[startline].split(" ")[0].find('_')+1] == 'K'):
                k = int(zeilen[startline].split(" ")[0][zeilen[startline].split(" ")[0].find('(')+1:-1])
            used_datapoints = getUsedPoints(zeilen[i], ts_len, k)
            clf_dur = getPredictionTime(zeilen[i])
            recv_dur = getRealtimeTime(used_datapoints, frequenz)
            timeliness += clf_dur / recv_dur
    return timeliness / counter

def getMaxTimeliness(startline, frequenz, ts_len): # startline ist der index der zeile, die mit T beginnt # die methode berechnet also die durchschnittliche timeliness einer teaservariante in einer frequenz in einem datensatz
    timeliness = 0
    for i in range(startline+1, len(zeilen)):
        if(zeilen[i][0] != "["):
            break
        else:
            k = 1 # default case
            if(zeilen[startline][zeilen[startline].split(" ")[0].find('_')+1] == 'K'):
                k = int(zeilen[startline].split(" ")[0][zeilen[startline].split(" ")[0].find('(')+1:-1])
            used_datapoints = getUsedPoints(zeilen[i], ts_len, k)
            clf_dur = getPredictionTime(zeilen[i])
            recv_dur = getRealtimeTime(used_datapoints, frequenz)
            if timeliness < (clf_dur / recv_dur):
                timeliness = clf_dur / recv_dur 
    return timeliness

def getEarliness(startline, ts_len): # startline ist der index der zeile, die mit T beginnt # die methode berechnet also die durchschnittliche timeliness einer teaservariante in einer frequenz in einem datensatz
    counter = 0
    earliness = 0
    for i in range(startline+1, len(zeilen)):
        if(zeilen[i][0] != "["):
            break
        else:
            counter += 1
            k = 1 # default case
            if(zeilen[startline][zeilen[startline].split(" ")[0].find('_')+1] == 'K'):
                k = int(zeilen[startline].split(" ")[0][zeilen[startline].split(" ")[0].find('(')+1:-1])
            used_datapoints = getUsedPoints(zeilen[i], ts_len, k)
            #print(processed_ts)
            #print(used_datapoints)
            earliness += used_datapoints / int(ts_len)
    return earliness / counter

def getDatasetStats(startline): # acc, early, realtime_percentage pro Methode pro testfrequenz // für gesamtaussagen // startline = index der zeile mit "DATASET: "
    # Diese Methode sammelt nur die daten eines Datensets, andere Methoden machen dann die Statistik
    print("seaching Statistical Data in: " + zeilen[startline] + "    " + str(int((startline / len(zeilen))*100)) + "%") # ladebalken
    res = []
    current_frequency = 0
    dataset_name = zeilen[startline].split(" ")[1]
    ts_len = zeilen[startline+1].split(" ")[2]
    for i in range(startline+1, len(zeilen)):
        if(zeilen[i][0] == "N"): # neues Dataset beginnt (neue frequenz)
            current_frequency = zeilen[i].split(" ")[1]
        if(zeilen[i][0] == "T"): # neue Methode beginnt (in dieser Zeile sind alle Informationen) # variant.getTyp() + " " + String.valueOf(variant.getAccuracy()) + " " + String.valueOf(variant.getEarlyness()) + " " + String.valueOf(variant.getPredictionTime()) + " " + String.valueOf(variant.getMinPredictionFrequency()) + "\n";
            stats_line = zeilen[i].split(" ")
            name = stats_line[0].split("(")[0]
            # Werte für eine Mehode einer Frequenz in einem Datensatz berechnen
            minPredictionFrequency = float(stats_line[4]) # nicht besonders gut darzustellen
            avgPredictionFrequency = getAvgPredictionFrequency(i, ts_len)
            realtimePercentage = getRealtimePercentage(i, float(current_frequency), ts_len)
            avgPredictionTime = getAvgPredictionTime(i)

            accuracy = float(stats_line[1])
            earliness = float(stats_line[2]) # könnte ich auch aus der datei berechnen
            timeliness = getMaxTimeliness(i, float(current_frequency), ts_len)

            earliness2 = getEarliness(i, ts_len)
            if len(stats_line[0].split("(")) > 1: # das nimmt das hpyerparameter von s und k teaser aus dem namen weg
                name = stats_line[0].split("(")[0][:-1]
            # [dataset, freq, method, acc, earl, realtime_percentage, avgPredictionTime]
            res.append([dataset_name, current_frequency, name, accuracy, earliness, timeliness, earliness2])
        if(zeilen[i][0] == "D"):
            break # das nächste Dataset beginnt
    
    return res

def getAllDatasetStats(startline): # aus dem result einen dataframe machen
    res = []
    counter = 0
    #ausgeschlosseneDatasets = ["MoteStrain", "TwoLeadECG", "SmoothSubspace", "BME", "ECGFiveDays", "Fungi", "CBF", "UMD", "DiatomSizeReduction", "InsectEPGSmallTrain", "Coffee", "FaceFour", "ArrowHead", "Symbols", "ShapeletSim", "BirdChicken", "BeetleFly", "Wine", "Beef", "Plane", "OliveOil", "SyntheticControl", "Lightning7", "FacesUCR", "InsectEPGRegularTrain", "Trace", "Meat", "MiddlePhalanxTW", "MiddlePhalanxOutlineAgeGroup", "DistalPhalanxOutlineAgeGroup", "DistalPhalanxTW", "ProximalPhalanxTW", "ProximalPhalanxOutlineAgeGroup", "Herring", "Car", "Rock", "MedicalImages", "Lightning2", "MiddlePhalanxOutlineCorrect", "DistalPhalanxOutlineCorrect", "ProximalPhalanxOutlineCorrect", "Ham", "Mallat", "InsectWingbeatSound", "SwedishLeaf", "CinCECGTorso", "Adiac", "WordSynonyms", "FaceAll", "Fish", "OSULeaf", "MixedShapesSmallTrain"] # pseudo time series
    #ausgeschlosseneDatasets = ["PowerCons", "Chinatown", "MelbournePedestrian", "ItalyPowerDemand", "ChlorineConcentration", "DodgerLoopDay", "DodgerLoopWeekend", "DodgerLoopGame", "FreezerRegularTrain", "FreezerSmallTrain", "HouseTwenty", "GunPoint", "GunPointAgeSpan", "GunPointMaleVersusFemale", "GunPointOldVersusYoung", "AllGestureWiimoteX", "AllGestureWiimoteY", "AllGestureWiimoteZ", "PickupGestureWiimoteZ", "ShakeGestureWiimoteZ", "GestureMidAirD1", "GestureMidAirD2", "GestureMidAirD3", "GesturePebbleZ1", "GesturePebbleZ2", "ToeSegmentation1", "ToeSegmentation2", "SonyAIBORobotSurface1", "SonyAIBORobotSurface2", "ECG200", "CricketX", "CricketY", "CricketZ", "ECG5000", "EOGHorizontalSignal", "PLAID"] # ecte TS
    ausgeschlosseneDatasets = []
    for i in range(startline, len(zeilen)):
        if zeilen[i][0] == "D" and (zeilen[i].split(" ")[1].strip() not in ausgeschlosseneDatasets):
            res += getDatasetStats(i)
            counter += 1
    print(str(counter) + " Datasets gefunden")
    return res

def generate_ucr_results_dataframe():
    pd.options.display.float_format = '{:.10f}'.format # zum anzeigen der zahlen in nicht wissenschaflicher noation, auf 10 stellen gerundet
    df = pd.DataFrame(getAllDatasetStats(0), columns=['Dataset', 'Frequency', 'Method', 'Accuracy', 'Earliness', 'Timeliness', 'e'])
    df['Frequency'] = df['Frequency'].astype(float)
    df['Accuracy'] = df['Accuracy'].astype(float)
    df['Earliness'] = df['Earliness'].astype(float)
    df['Timeliness'] = df['Timeliness'].astype(float)
    df['e'] = df['e'].astype(float)
    df['Differenz'] = df['e'] - df['Earliness'] # debugging
    print(df.to_string())
    df = df.drop(df[df['Accuracy'] == 0].index) # teaser hat keine prediction abgegeben
    print(df.to_string())
    #print(df.groupby(['frequency', 'Method']).agg({'Accuracy': ['mean'], 'Earliness': ['mean'], 'realtime_percentage': ['mean'], 'avg_PredictionTime': ['mean']}).head(50))
    grouped_df = df.groupby(['Frequency', 'Method'], as_index=False).agg({'Accuracy': ['mean'], 'Earliness': ['mean'], 'Timeliness': ['mean'], 'e':['mean']})
    grouped_df['HarmonicMean'] = (2 * (1 - grouped_df['Earliness']) * grouped_df['Accuracy']) / ((1 - grouped_df['Earliness']) + grouped_df['Accuracy'])
    print(grouped_df.to_string())
    return grouped_df


dataset_counter = 0; # startzeile des aktuellen datasets
current_dataset_headline = ""
current_dataset_headline_index = 0
def hyperparams_plot(): # x-Achse sind datapoints, y-Achse sind ms
    counter = 0 # subplot index
    counter2 = 1
    for i in range(0, len(zeilen)): # 1516417 als startline für PLAID
        if(zeilen[i][0] == "N"): # neue frequenz beginnt
            print("new Dataset found: " + zeilen[i])
            plt.suptitle(correctName(zeilen[i].split("_")[1]) + "\n ")
            current_dataset_headline = zeilen[i]
            current_dataset_headline_index = i
        if (zeilen[i][0] == "T"): # neue methode beginnt
            counter += 1
            plt.subplot(1,6, counter)
            headerzeile = zeilen[i].split(" ")
            ts_len = current_dataset_headline.split(" ")[2]
            plt.title(correctName(headerzeile[0]) + "\n" + headerzeile[1][:4] + " acc " + headerzeile[2][:4] + " early \n" + str(format(getMaxTimeliness(i, float(current_dataset_headline.split(" ")[1]), ts_len), '.2f')) + " max. timeliness")
            plt.subplots_adjust(hspace=0.5)
            plt.subplots_adjust(top=0.8)
            plt.subplots_adjust(left=0.05)
            plt.subplots_adjust(right=0.95)
            plt.subplots_adjust(wspace=0.35)
            plt.ylabel('Prediction Time (ms)')
            plt.grid(True)
            plt.xlabel('Data Points')
            plot_default_points(current_dataset_headline_index, 21, ts_len)
            #plot_first_default_point(current_dataset_headline_index, ts_len)
            #plotAverageDefault(current_dataset_headline_index, ts_len, "yellow", zeilen[i].split(" ")[0])
            if not zeilen[i-1] == current_dataset_headline: # der erste subplot sind nur die default werte
                plot_measurement_points(i, ts_len)
                #plot_first_measurement_points(i, ts_len)
                #plotAverageMeasurement(i, ts_len, "red", zeilen[i].split(" ")[0])
            plot_realtimeline(current_dataset_headline.split(" ")[1], ts_len, ts_len) # wenn S == len(TS), dann ist die x-Achse datapoints
        if counter == 6: # neue Grafik anfangen
            counter = 0
            plt.show()
            if counter2 == 1:
                counter = 1
                counter2 = 0
                plt.suptitle(correctName(current_dataset_headline.split("_")[1]) + "\n ")
                plt.subplot(1,6, counter)
                headerzeile = zeilen[current_dataset_headline_index + 1].split(" ")
                plt.title(correctName(headerzeile[0]) + "\n" + headerzeile[1][:4] + " acc " + headerzeile[2][:4] + " early \n" + str(format(getMaxTimeliness(current_dataset_headline_index + 1, float(current_dataset_headline.split(" ")[1]), ts_len), '.2f')) + " max. timeliness")
                plt.subplots_adjust(hspace=0.5)
                plt.subplots_adjust(top=0.8)
                plt.subplots_adjust(left=0.05)
                plt.subplots_adjust(right=0.95)
                plt.subplots_adjust(wspace=0.35)
                plt.ylabel('Prediction Time (ms)')
                plt.grid(True)
                plt.xlabel('Data Points')
                ts_len = current_dataset_headline.split(" ")[2]
                plot_default_points(current_dataset_headline_index, 21, ts_len)
                plot_realtimeline(current_dataset_headline.split(" ")[1], ts_len, ts_len) # wenn S == len(TS), dann ist die x-Achse datapoints
            else:
                counter2 = 1
            
    plt.show()

dataset_counter = 0; # startzeile des aktuellen datasets
current_dataset_headline = ""
current_dataset_headline_index = 0
def all_methods_plot(): # x-Achse sind datapoints, y-Achse sind ms
    counter = 0 # subplot index
    counter2 = 1
    plt.rcParams.update({'font.size': 16})
    for i in range(1516417, len(zeilen)): # 1516417 als startline für PLAID
        if(zeilen[i][0] == "N"): # neue frequenz beginnt
            print("new Dataset found: " + zeilen[i])
            plt.suptitle(correctName(zeilen[i].split("_")[1]) + "\n ")
            current_dataset_headline = zeilen[i]
            current_dataset_headline_index = i
        if (zeilen[i][0] == "T"): # neue methode beginnt
            counter += 1
            plt.subplot(1,6, counter)
            headerzeile = zeilen[i].split(" ")
            ts_len = current_dataset_headline.split(" ")[2]
            plt.title(correctName(headerzeile[0]) + "\n" + headerzeile[1][:4] + " acc. " + headerzeile[2][:4] + " early. \n" + str(format(getMaxTimeliness(i, float(current_dataset_headline.split(" ")[1]), ts_len), '.2f')) + " max. timeliness")
            plt.subplots_adjust(hspace=0.4)
            plt.subplots_adjust(top=0.85)
            plt.subplots_adjust(left=0.05)
            plt.subplots_adjust(right=0.97)
            plt.subplots_adjust(bottom=0.08)
            plt.subplots_adjust(wspace=0.3)
            plt.ylabel('Prediction Time (ms)')
            plt.grid(True)
            plt.xlabel('Data Points')
            plot_default_points(current_dataset_headline_index, 21, ts_len)
            #plot_first_default_point(current_dataset_headline_index, ts_len)
            #plotAverageDefault(current_dataset_headline_index, ts_len, "yellow", zeilen[i].split(" ")[0])
            if not zeilen[i-1] == current_dataset_headline: # der erste subplot sind nur die default werte
                plot_measurement_points(i, ts_len)
                #plot_first_measurement_points(i, ts_len)
                #plotAverageMeasurement(i, ts_len, "red", zeilen[i].split(" ")[0])
            plot_realtimeline(current_dataset_headline.split(" ")[1], ts_len, ts_len) # wenn S == len(TS), dann ist die x-Achse datapoints
        if counter == 6: # neue Grafik anfangen
            counter = 0
            plt.show()
            if counter2 == 1:
                counter = 1
                counter2 = 0
                plt.suptitle(correctName(current_dataset_headline.split("_")[1]) + "\n ")
                plt.subplot(1,6, counter)
                headerzeile = zeilen[current_dataset_headline_index + 1].split(" ")
                plt.title(correctName(headerzeile[0]) + "\n" + headerzeile[1][:4] + " acc. " + headerzeile[2][:4] + " early. \n" + str(format(getMaxTimeliness(current_dataset_headline_index + 1, float(current_dataset_headline.split(" ")[1]), ts_len), '.2f')) + " max. timeliness")
                plt.subplots_adjust(hspace=0.4)
                plt.subplots_adjust(top=0.85)
                plt.subplots_adjust(left=0.05)
                plt.subplots_adjust(right=0.97)
                plt.subplots_adjust(bottom=0.08)
                plt.subplots_adjust(wspace=0.3)
                plt.ylabel('Prediction Time (ms)')
                plt.grid(True)
                plt.xlabel('Data Points')
                ts_len = current_dataset_headline.split(" ")[2]
                plot_default_points(current_dataset_headline_index, 21, ts_len)
                plot_realtimeline(current_dataset_headline.split(" ")[1], ts_len, ts_len) # wenn S == len(TS), dann ist die x-Achse datapoints
            else:
                counter2 = 1
            
    plt.show()

#all_methods_plot()


def color_plot():

    # diese colormap für den timeliness plot benutzen, dann sind werte über 1 automatisch rot

    import numpy as np
    import matplotlib.pyplot as plt
    from matplotlib.colors import LinearSegmentedColormap
    plt.rcParams.update({'font.size': 16})

    
    grouped_df = generate_ucr_results_dataframe()


    # eigene generierung der colormap:
    max_value = grouped_df['Timeliness'].max()
    n_bins = 1000
    blue_to_white = []
    white_to_red = []
    white_index = int((1/max_value)*n_bins)
    for i in range(white_index):
        blue_to_white.append((i / white_index, i / white_index, 1))
    for i in range(n_bins - (white_index+1)): # n_bins - (white_index+1)
        white_to_red.append((1, 1-(i / (n_bins - (white_index+1))), 1-(i / (n_bins - (white_index+1)))))
    combined_colors = blue_to_white + white_to_red
    x_values = np.linspace(0, 1, len(combined_colors))
    custom_cmap = LinearSegmentedColormap.from_list('custom_cmap', list(zip(x_values, combined_colors)))



    accuracy_matrix = grouped_df.pivot(index='Frequency', columns='Method', values=('Accuracy', 'mean')).sort_index()
    timeliness_matrix = grouped_df.pivot(index='Frequency', columns='Method', values=('Timeliness', 'mean')).sort_index()
    earliness_matrix = grouped_df.pivot(index='Frequency', columns='Method', values=('Earliness', 'mean')).sort_index()
    harmoincmean_matrix = grouped_df.pivot(index='Frequency', columns='Method', values=('HarmonicMean')).sort_index()

    fig, ax = plt.subplots(2, 2, figsize=(9, 18))
    fig.suptitle('Results on the UCR datasets', fontsize=16)
    colormap = 'bwr'

    c1 = ax[0,0].imshow(accuracy_matrix.values, aspect='auto', cmap=colormap)
    ax[0,0].set_xticks(np.arange(len(accuracy_matrix.columns)))
    ax[0,0].set_xticklabels(["Default-TEASER", "K-TEASER", "Skipping-K-TEASER", "S-TEASER", "Skipping-S-TEASER", "Skipping-TEASER"], rotation=90)
    ax[0,0].set_yticks(np.arange(len(accuracy_matrix.index)))
    ax[0,0].set_yticklabels(accuracy_matrix.index)
    plt.colorbar(c1, ax=ax[0,0])
    ax[0,0].set_title('Accuracy')
    ax[0,0].set_xlabel('Method')
    ax[0,0].set_ylabel('Frequency')

    c2 = ax[0,1].imshow(earliness_matrix.values, aspect='auto', cmap=colormap)
    ax[0,1].set_xticks(np.arange(len(earliness_matrix.columns)))
    ax[0,1].set_xticklabels(["Default-TEASER", "K-TEASER", "Skipping-K-TEASER", "S-TEASER", "Skipping-S-TEASER", "Skipping-TEASER"], rotation=90)
    ax[0,1].set_yticks(np.arange(len(earliness_matrix.index)))
    ax[0,1].set_yticklabels(earliness_matrix.index)
    plt.colorbar(c2, ax=ax[0,1])
    ax[0,1].set_title('Earliness')
    ax[0,1].set_xlabel('Method')
    ax[0,1].set_ylabel('Frequency')

    c3 = ax[1,0].imshow(harmoincmean_matrix.values, aspect='auto', cmap=colormap)
    ax[1,0].set_xticks(np.arange(len(harmoincmean_matrix.columns)))
    ax[1,0].set_xticklabels(["Default-TEASER", "K-TEASER", "Skipping-K-TEASER", "S-TEASER", "Skipping-S-TEASER", "Skipping-TEASER"], rotation=90)
    ax[1,0].set_yticks(np.arange(len(harmoincmean_matrix.index)))
    ax[1,0].set_yticklabels(harmoincmean_matrix.index)
    plt.colorbar(c3, ax=ax[1,0])
    ax[1,0].set_title('HarmoincMean')
    ax[1,0].set_xlabel('Method')
    ax[1,0].set_ylabel('Frequency')

    c4 = ax[1,1].imshow(timeliness_matrix.values, aspect='auto', cmap=custom_cmap) # timeliness ist immer max jedes datasets, und dann der durchschnitt der datasets einer gruppe
    ax[1,1].set_xticks(np.arange(len(timeliness_matrix.columns)))
    ax[1,1].set_xticklabels(["Default-TEASER", "K-TEASER", "Skipping-K-TEASER", "S-TEASER", "Skipping-S-TEASER", "Skipping-TEASER"], rotation=90) # ["Default-TEASER", "K-TEASER", "Skipping-K-TEASER", "S-TEASER", "Skipping-S-TEASER", "Skipping-TEASER"]
    ax[1,1].set_yticks(np.arange(len(timeliness_matrix.index)))
    ax[1,1].set_yticklabels(timeliness_matrix.index)
    plt.colorbar(c4, ax=ax[1,1])
    ax[1,1].set_title('Timeliness')
    ax[1,1].set_xlabel('Method')
    ax[1,1].set_ylabel('Frequency')

    plt.subplots_adjust(hspace=0.7)
    plt.show()




# neue methode für die colormap, nicht mehr alle 4 grafiken nebeneinander
def color_plot2():

    # diese colormap für den timeliness plot benutzen, dann sind werte über 1 automatisch rot

    pd.options.display.float_format = '{:.10f}'.format # zum anzeigen der zahlen in nicht wissenschaflicher noation, auf 10 stellen gerundet
    df = pd.DataFrame(getAllDatasetStats(0), columns=['Dataset', 'Frequency', 'Method', 'Accuracy', 'Earliness', 'Timeliness', 'e'])
    df['Frequency'] = df['Frequency'].astype(float)
    df['Accuracy'] = df['Accuracy'].astype(float)
    df['Earliness'] = df['Earliness'].astype(float)
    df['Timeliness'] = df['Timeliness'].astype(float)
    df['e'] = df['e'].astype(float)
    df['Differenz'] = df['e'] - df['Earliness'] # debugging
    #print(df.to_string())
    df = df.drop(df[df['Accuracy'] == 0].index)
    #df.loc[(df['Dataset'].isin(["PowerCons", "Chinatown", "MelbournePedestrian", "ItalyPowerDemand", "ChlorineConcentration", "DodgerLoopDay", "DodgerLoopWeekend", "DodgerLoopGame", 
    #"FreezerRegularTrain", "FreezerSmallTrain", "HouseTwenty", "GunPoint", "GunPointAgeSpan", "GunPointMaleVersusFemale", "GunPointOldVersusYoung", "AllGestureWiimoteX", 
    #"AllGestureWiimoteY", "AllGestureWiimoteZ", "PickupGestureWiimoteZ", "ShakeGestureWiimoteZ", "GestureMidAirD1", "GestureMidAirD2", "GestureMidAirD3", "GesturePebbleZ1", 
    #"GesturePebbleZ2", "ToeSegmentation1", "ToeSegmentation2", "SonyAIBORobotSurface1", "SonyAIBORobotSurface2", "ECG200", "CricketX", "CricketY", "CricketZ", "ECG5000", 
    #"EOGHorizontalSignal", "PLAID"])) & (df['Frequency'] < float(1000)), 'Frequency'] = float(1)
    df.loc[(df['Frequency'] < float(1000)), 'Frequency'] = float(1)
    #df.loc[((df['Frequency'] > float(100)) & (df['Frequency'] < float(1000))), 'Frequency'] = float(200)
    #print(df.to_string())
    #print(df.groupby(['frequency', 'Method']).agg({'Accuracy': ['mean'], 'Earliness': ['mean'], 'realtime_percentage': ['mean'], 'avg_PredictionTime': ['mean']}).head(50))
    grouped_df = df.groupby(['Frequency', 'Method'], as_index=False).agg({'Accuracy': ['mean'], 'Earliness': ['mean'], 'Timeliness': ['mean']})
    grouped_df['HarmonicMean'] = (2 * (1 - grouped_df['Earliness']) * grouped_df['Accuracy']) / ((1 - grouped_df['Earliness']) + grouped_df['Accuracy'])
    print(grouped_df.to_string())

    import numpy as np
    import matplotlib.pyplot as plt
    from matplotlib.colors import LinearSegmentedColormap
    plt.rcParams.update({'font.size': 16})

    


    # eigene generierung der colormap:
    max_value = grouped_df['Timeliness'].max()
    n_bins = 1000
    blue_to_white = []
    white_to_red = []
    white_index = int((1/max_value)*n_bins)
    for i in range(white_index):
        blue_to_white.append((i / white_index, i / white_index, 1))
    for i in range(n_bins - (white_index+1)): # n_bins - (white_index+1)
        white_to_red.append((1, 1-(i / (n_bins - (white_index+1))), 1-(i / (n_bins - (white_index+1)))))
    combined_colors = blue_to_white + white_to_red
    x_values = np.linspace(0, 1, len(combined_colors))
    custom_cmap = LinearSegmentedColormap.from_list('custom_cmap', list(zip(x_values, combined_colors)))



    accuracy_matrix = grouped_df.pivot(index='Frequency', columns='Method', values=('Accuracy', 'mean')).sort_index()
    timeliness_matrix = grouped_df.pivot(index='Frequency', columns='Method', values=('Timeliness', 'mean')).sort_index()
    earliness_matrix = grouped_df.pivot(index='Frequency', columns='Method', values=('Earliness', 'mean')).sort_index()
    harmoincmean_matrix = grouped_df.pivot(index='Frequency', columns='Method', values=('HarmonicMean')).sort_index()

    fig, ax = plt.subplots(1, 2, figsize=(12, 12))
    fig.suptitle('Results on the UCR datasets', fontsize=18)
    colormap = 'bwr'
    plt.subplots_adjust(hspace=0.2)
    plt.subplots_adjust(top=0.92)
    plt.subplots_adjust(left=0.15)
    plt.subplots_adjust(right=0.94)
    plt.subplots_adjust(bottom=0.22)
    plt.subplots_adjust(wspace=0.7)

    y_tick_lables = accuracy_matrix.index.tolist()
    #print(y_tick_lables)
    for i in range(len(y_tick_lables)):
        if y_tick_lables[i] == 1:
            y_tick_lables[i] = "<1000.0"
    y_tick_lables = pd.Index(y_tick_lables)

    c1 = ax[0].imshow(accuracy_matrix.values, aspect='auto', cmap=colormap)
    ax[0].set_xticks(np.arange(len(accuracy_matrix.columns)))
    ax[0].set_xticklabels(["Default-TEASER", "K-TEASER", "Skipping-K-TEASER", "S-TEASER", "Skipping-S-TEASER", "Skipping-TEASER"], rotation=90)
    ax[0].set_yticks(np.arange(len(accuracy_matrix.index)))
    ax[0].set_yticklabels(y_tick_lables)
    plt.colorbar(c1, ax=ax[0])
    ax[0].set_title('Accuracy')
    ax[0].set_xlabel('Method')
    ax[0].set_ylabel('Frequency')

    c2 = ax[1].imshow(earliness_matrix.values, aspect='auto', cmap=colormap)
    ax[1].set_xticks(np.arange(len(earliness_matrix.columns)))
    ax[1].set_xticklabels(["Default-TEASER", "K-TEASER", "Skipping-K-TEASER", "S-TEASER", "Skipping-S-TEASER", "Skipping-TEASER"], rotation=90)
    ax[1].set_yticks(np.arange(len(earliness_matrix.index)))
    ax[1].set_yticklabels(y_tick_lables)
    plt.colorbar(c2, ax=ax[1])
    ax[1].set_title('Earliness')
    ax[1].set_xlabel('Method')
    ax[1].set_ylabel('Frequency')

    fig, ax = plt.subplots(1, 2, figsize=(12, 12))
    fig.suptitle('Results on the UCR datasets', fontsize=18)
    plt.subplots_adjust(hspace=0.2)
    plt.subplots_adjust(top=0.92)
    plt.subplots_adjust(left=0.15)
    plt.subplots_adjust(right=0.94)
    plt.subplots_adjust(bottom=0.22)
    plt.subplots_adjust(wspace=0.7)

    c3 = ax[0].imshow(harmoincmean_matrix.values, aspect='auto', cmap=colormap)
    ax[0].set_xticks(np.arange(len(harmoincmean_matrix.columns)))
    ax[0].set_xticklabels(["Default-TEASER", "K-TEASER", "Skipping-K-TEASER", "S-TEASER", "Skipping-S-TEASER", "Skipping-TEASER"], rotation=90)
    ax[0].set_yticks(np.arange(len(harmoincmean_matrix.index)))
    ax[0].set_yticklabels(y_tick_lables)
    plt.colorbar(c3, ax=ax[0])
    ax[0].set_title('Harmoinc Mean')
    ax[0].set_xlabel('Method')
    ax[0].set_ylabel('Frequency')

    c4 = ax[1].imshow(timeliness_matrix.values, aspect='auto', cmap=custom_cmap) # timeliness ist immer max jedes datasets, und dann der durchschnitt der datasets einer gruppe
    ax[1].set_xticks(np.arange(len(timeliness_matrix.columns)))
    ax[1].set_xticklabels(["Default-TEASER", "K-TEASER", "Skipping-K-TEASER", "S-TEASER", "Skipping-S-TEASER", "Skipping-TEASER"], rotation=90) # ["Default-TEASER", "K-TEASER", "Skipping-K-TEASER", "S-TEASER", "Skipping-S-TEASER", "Skipping-TEASER"]
    ax[1].set_yticks(np.arange(len(timeliness_matrix.index)))
    ax[1].set_yticklabels(y_tick_lables)
    plt.colorbar(c4, ax=ax[1])
    ax[1].set_title('Timeliness')
    ax[1].set_xlabel('Method')
    ax[1].set_ylabel('Frequency')

    plt.show()


# tabelle mit acc, earliness, timeliness
#generate_ucr_results_dataframe()

# vorallem zum debuggen
# four_strategies_plot # sind verschiede alle strategien nebeneinander

# alle methoden, alle frequenzen für ein dataset
# plot_dataset 

# impact der hyperparameter darstellen (nur für hyperparameter-messungen)
#hyperparams_plot()

#all_methods_plot()

# colormap for all datasets
#color_plot() # 2x2
color_plot2() # 1x2 + 1x2 # und <1000Hz zusammengefasst


