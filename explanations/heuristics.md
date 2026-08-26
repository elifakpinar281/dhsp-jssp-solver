# Evaluation
Kleinere Zahl ist besser bei mir.

## Slack
Puffer-Zeit bis man gegen die Max Time Regel verstoßen würde
Viel Slack ist also entspannter
NO_BINDING... unendlicher Slack

Der Slack einer bestimmten Operation ändert sich während des Lösens nie.
Er wird beim ersten Abfragen einmal berechnet und gespeichert (cache, lazy)

computeSlack()
Zuerst wird nach der dwell chain ab dieser Operation gefragt.
Besteht die chain nur aus einer Operation, gibt es nichts danach (NO_BINDING).
Sonst: Slack pro Glied = maxDwellTime - processingTime.
Es wird der kleinste davon ausgegeben
Ist der Wert negativ -> 0 (unmöglich einzuhalten).


## MakespanEstimateHeuristic
Die Hauptheuristik
Schätzt eine Untergrenze -> so früh kann das frühestens fertig sein
Sie nimmt das Maximum aus zwei Schranken und addiert eine Penalty für die Dwell Times

jobBound -> Für jeden Job wird der Zeitpunkt genommen, ab dem es verfügbar ist und addiert dann die processing time aller restlichen Operationen dazu.
Best case für den Job ist, wenn er nie warten müsste

machineBound -> Zählt die restliche Arbeit pro Maschine zusammen (remainingLoad).
(freeSum + remainingLoad) / capacity.
freeSum = Summe der Zeitpunkte, ab denen die Becken frei werden
Wenn man die ganze Arbeit gleichmäßig auf die Bäder verteilt - wann wäre die Maschine fertig
Ein blockiertes Becken wird als 0 gezählt

return Math.max(jobBound, machineBound) + penalty
Das Maximum ist die stärkere Untergrenze

calculateDwellPenalty -> States, bei denen die remaining dwell nahe ist, werden schlechter bewertet


## NextTMax/ NextTStart
Merksatz: wenig Slack -> große Zahl -> wird beim Minimieren vermieden.
Die Heuristik MEIDET also enge Dwell-Zustände, bevorzugt sie nicht.
(-> mit Noah klären, ob das so gewollt ist)

NextTMax/ NextTStart eh klar -> Es werden enge dwell times gemieden ist das okay?

## CombineHeuristics
Primär ist Makespan, NextTMax und NextTStart sind nur Tie-Breaks

Jeder Tie-Break wird durch normalize in den Bereich (-1, 1) gezogen und mit einem immer kleineren Faktor multipliziert
Tie-Breaks entscheiden nur bei Gleichstand der Makespan - dann erstes Tie-Break, dann das zweite

normalize(x) = x / (1 + |x|) ... behält das Vorzeichen von x.
Deckelt jeden Tie-Break auf Betrag < 1, damit ein großer Tie-Break-Wert keine echten Makespan-Unterschiede nimmt