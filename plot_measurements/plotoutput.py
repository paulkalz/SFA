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

measurements = []
counter = 1;
for i in range(len(zeilen)): 
    if (zeilen[i][0] != "[" and zeilen[i][0] != "-" and zeilen[i][0] != "+"): # neues Dataset beginnt
        measurements = []
        headerzeile = zeilen[i].split(" ")
        plt.subplot(2,5,counter)
        counter +=1
        plt.title(headerzeile[0] + " (" + headerzeile[1] + "Hz) \n" + headerzeile[5] + " " + headerzeile[6] + " \n" + headerzeile[7] + " " + headerzeile[8])
        plt.subplots_adjust(hspace=0.5)
        plt.xlabel('Snapshots')
        plt.ylabel('Predictiontime in Ms')
        plt.grid(True)
        richtlinie = []
        richtlinie.append(0)
        for j in range(1, int(float(headerzeile[4]))+1): # 3=lqenge 4=S
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

# pro dataset ein plot mit boxplots aller Frequenzen und ein plot aller ausfuehrungszeiten