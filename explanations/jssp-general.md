# JSSP Class - Regelwerk :)
- Welche Jobs gibt es?
- Welche Operation kommt nach welcher?
- Welche Maschinen/Bäder gibt es?
- Was darf als Nächstes gemacht werden?
- Welche Operationen sind blockiert?
- Welche Operationen sind erlaubt?
- Was passiert, wenn ich eine Operation einplane?

Im Vergleich ist State der aktuelle Zustand. Jeder Move erzeugt einen neuen State & verändert den alten nicht.

Im Konstruktor werden alle Bäder nummeriert.
bathOffset -> Bei welchem Bad beginnen die Maschinen? (z.B. Maschine 0 bei Bad 0, Maschine 1 bei Bad 2 usw.)
isGoal() -> Prüft, ob alle Jobs fertig sind - Ziel irgendwie dann endet es
earliestFreeBath() -> Welches Bad dieser Maschine wird am frühesten frei? (z.B. B0 frei ab 20, B1 frei ab 15, B3 ist blockiert - methode liefert b1)

applyOperation()
- Bestimmt die dwell chain -> List<Operation> chain = dwellChain(jobId, firstIndex);
- Bestimmt den frühesten Start -> Job muss bereit sein und das Bad muss frei sein
- Probiert die Kette aus -> placeChain() - prüft, ob man die dwell chain ab einem Zeitpunkt unterbringen kann. 3 Möglichkeiten - chain placement ist valide, | blocked - kein Bad verfügbar bzw blockedByCarrier | invalid - passt momentan nicht, aber könnte später funktionieren also requiredStart
- Werte wird wenn nötig nach hinten verschoben
  - Versuch ab 10 
    - A: 10-20
    - B: 25-35
  B muss wegen Dwell spätestens um 22 starten
    - B startet um 25 aber es war 22 erlaubt also zu später

  - Versuch ab 13
    - A: 13-23
    - B: 25-35
    passt

Ganze Kette wird verschoben und nochmal ausprobiert, bis die Bad available time und dwell limit zusammenpassen

clone() damit man mit Kopie ausprobieren kann

Wenn placeChain() valid gibt dann wird commited durch commit(). Der State wird nicht verändert, es wird ein neuer erzeugt.

