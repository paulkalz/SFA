import os
from matplotlib import pyplot as plt

dateipfad = os.path.abspath("plot_measurements/measurements.txt")

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
    times = list(map(float, arr[2:-1].split(", "))) #trim_zeros(list(map(float, arr[2:-1].split(", "))))
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
    step = float(ts_len) / S 
    offsets = []
    for i in range(1, S+1):
        offsets.append(int(step * i))
    #print(str(S) + " " + ts_len +" "+ str(len(arr)) + " " + str(step))
    #print(offsets)
    result = []
    result.append(0)
    temp = 0
    for i in range(1, int(float(ts_len))):
        if i in offsets:
            result.append(arr[temp])
            temp += 1
        else:
            result.append(result[i-1])
    return result

def processTS(arr, ts_len): # input arr ist der string eines arrays (eine zeile)
    # füllt lücken in der messung, verteilt die snapshotmessungen auf die datenpunkte, schneidet messungen nach dem klassifikationszeitpunkt ab
    res = fillArray(arr)
    return trim_duplicates(snapshotTStoPointTS(res, len(res), ts_len)) # S = (arr) da wir eine messung pro snapshot haben

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
                    break;
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
                    break;
                if(zeilen[j][0] == "["):
                    #print(fillArray(zeilen[j]))
                    #print(snapshotTStoPointTS(fillArray(zeilen[j]), S, ts_len))
                    plt.plot(processTS(zeilen[j], ts_len), linewidth=1, color='blue')
            break
    #plt.show()

def plot_measurement(startline): # input: eine feste frequenz, default, skipping, sx5, kx5 // output: default vs skipping, default vs sx5, default vs kx5
    print("seaching measurement values beginning from line: " + str(startline))
    for i in range(startline+1, len(zeilen)):
        if(zeilen[i][0] == "T"):
            break;
        if(zeilen[i][0] == "["):
            plt.plot(fillArray(zeilen[i]), linewidth=1, color='red')
    #plt.show()

def plot_measurement_points(startline, ts_len): # input: eine feste frequenz, default, skipping, sx5, kx5 // output: default vs skipping, default vs sx5, default vs kx5
    print("seaching measurement values beginning from line: " + str(startline))
    for i in range(startline+1, len(zeilen)):
        if(zeilen[i][0] == "T"):
            break;
        if(zeilen[i][0] == "["):
            plt.plot(processTS(zeilen[i], ts_len), linewidth=1, color='red')

dataset_counter = 0; # startzeile des aktuellen datasets
current_dataset_headline = ""
current_dataset_headline_index = 0;
def four_strategies_plot(): # x-Achse sind datapoints, y-Achse sind ms
    counter = 0; # subplot index
    for i in range(len(zeilen)):
        if(zeilen[i][0] == "N"): # neues Dataset beginnt
            print("new Dataset found: " + zeilen[i])
            plt.suptitle(zeilen[i].split("_")[1] + "\n ")
            current_dataset_headline = zeilen[i]
            current_dataset_headline_index = i
        if (zeilen[i][0] == "T"): # neue messung beginnt
            counter += 1
            plt.subplot(2,6, counter)
            headerzeile = zeilen[i].split(" ")
            plt.title(headerzeile[0] + "\n" + headerzeile[1][:4] + " acc " + headerzeile[2][:4] + " early \n" + headerzeile[3][:4] + "avg. ms " + headerzeile[4])
            plt.subplots_adjust(hspace=0.5)
            plt.ylabel('Predictiontime in ms')
            plt.grid(True)
            plt.xlabel('Datapoints')
            ts_len = current_dataset_headline.split(" ")[2]
            plot_default_points(current_dataset_headline_index, 21, ts_len)
            if not zeilen[i-1] == current_dataset_headline: # der erste subplot sind nur die default werte
                plot_measurement_points(i, ts_len)
            plot_realtimeline(current_dataset_headline.split(" ")[1], ts_len, ts_len) # wenn S == len(TS), dann ist die x-Achse datapoints
        if counter == 12:
            counter = 0
            plt.show()
            
    plt.show()



four_strategies_plot()