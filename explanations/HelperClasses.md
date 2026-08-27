
## MemorySampler
Gibt die Space Complexity an (also Peak Memory Usage). 
start() startet den Sampler & mit run() würde es im Hauptthread laufen. 
Meine Intention ist es, dass der Sampler in einem eigenen Thread läuft, da es sonst evtl zu Überschneidungen mit dem Hauptthread kommen könnte.
Alle 5 Sekunden wird die aktuelle Memory Usage abgefragt und in einer Liste gespeichert.

volatile = sorgt dafür, dass Threads immer die aktuelle Version der Variable sehen
totalMemory()... Wie viel Speicher sich die JVM vom Betriebssystem geholt hat
freeMemory()... Wie viel davon noch frei ist
Differenz -> Wie viel gerade tatsächlich belegt ist

Wenn man interrupt() aufruft, während der Sampler "sleeped", wird der Schlaf abgebrochen und es wird eine InterruptedException (checked ex) geworfen - damit er "aufwacht" und beendet wird.
/(1024 / 1024) -> Umrechnung in MB


## Machine Overlap in ScheduleValidator
In JSSP hat jede Maschine capacity = 1 d.h. eine Maschine macht zu jedem Zeitpunkt eine Operation.
Overlapping ist nicht erlaubt. 
Bei unserer Anlage gibt es identische Bäder also ist die capacity über 1 
Aktuell - Es dürfen bis zur capacity mehrere Produkte gleichzeitig in der gleichen Maschine sein.

## State
nextOperation[job] ... welche Operation ist die nächste für den Job
bathAvailableTime[bath] ... wann ist das Bad wieder frei
jobAvailableTime[job] ... wann ist der Job wieder verfügbar
jobBath[job]... in welchem Bad ist der Job gerade
NO_BATH = -1 ... Job ist in keinem Bad (-1, da die Indizes der Bäder bei 0 beginnen)
BLOCKED = Integer.MAX_VALUE ... Job ist bei jedem Vergleich der schlechteste Kandidat und wird nie ausgewählt

NextTStartHeuristic: if (start == State.BLOCKED) { return PENALTY_BLOCKED; } -> 1e9, dann meidet die Suche das


## Stats
expansions... Wie viel Knoten bisher expandiert
reached... Wie viele verschiedene States schon gesehen 
frontier... Wie viele Nodes noch geprüft werden müssen
maxDepth... tiefste Stelle im Suchbaum, die bisher erreicht wurde
Immer "last" werte, da ich den jeweils letzten Wert möchte als eine Art Zusammenfassung
Frontier ist keine Zusammenfassung sondern eher ein Verlauf

BufferedWriter = Objekt zum Schreiben von Text in eine Datei. Er sammelt Text in einem Puffer und gibt ihn dann in einem großen Block zurück.
flush() zwingt Puffer, auf die Platte zu schreiben, auch wenn das Programm abstürzt.

## RunLog
Was evtl fehlt - stoppedByLimit und frontier 
Kontextabhängig zB expanded reached & maxDepth ergeben nicht so viel Sinn für Tabu Search (da locals search, dafür iterations nur bei tabu) - dann Wert 0.


## RunLogWriter
Mit Gson