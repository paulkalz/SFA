import matplotlib.pyplot as plt

dateipfad = r"SFA\plot_measurements\measurements.txt"

with open(dateipfad, 'r', encoding='utf-8') as datei:
    zeilen = datei.readlines()

zeilen = [zeile.strip() for zeile in zeilen]

measurements = []
for i in range(len(zeilen)):
    if zeilen[i][0] != "[":
        measurements = []
        headerzeile = zeilen[i].split(" ")
        plt.title(headerzeile[0])
        plt.xlabel('Predictions')
        plt.ylabel('Frequenz in Hz')
        plt.grid(True)
        plt.axhline(y=float(headerzeile[1]), color='r', linestyle='-', label=f'Horizontale Linie bei y={float(headerzeile[1])}') # reale samplingrate des datasets
        plt.axhline(y=float(headerzeile[2]), color='g', linestyle='--', label=f'Horizontale Linie bei y={float(headerzeile[2])}') # während des fittings gemessene minimale frequenz
    else:
        if(zeilen[i][0] == "["):
            measurements.append(list(map(float, zeilen[i][1:-1].split(", "))))
    if i+1 >= len(zeilen): # für die letzte zeile
        plt.boxplot(measurements)
        plt.show()
    else:
        if zeilen[i+1] == "" or zeilen[i+1][0] != "[":
          plt.boxplot(measurements)
          plt.show()


