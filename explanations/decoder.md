Decoder brauche ich, da Tabu Search eine lokale Suche ist d.h. die braucht schon eine fertige Startlösung, die 
dann verbessert wird.
Decoder gibt eine MachineSequence zurück (Reihenfolge der Jobs auf den Maschinen) und noch keine Zeiten - das macht dann Evaluator.

Startpunkt beeinflusst das Endergebnis enorm.

## Candidate
operation... welche Operation man als Nächstes einplanen könnte
transition... "was passieren würde" - neuer State und die dabei entstehenden ScheduledOperations aus Transition
completion... Endzeit der letzten Operation in der Transition
Man kann dann List<Candidate> sortieren und den besten Kandidaten dann verwenden.

## RandomStartDecoder
Wirklich zufällig
recht dumm
Zuerst wird eine Liste gebaut, in der Job Ids so oft vorkommen, wie der Job Operationen hat.
-> permutation with repetition

Collections.shuffle() -> mischt die Liste zufällig
Zufällige Reihenfolge

Das ist immer valid, da wenn eine ID in der Liste ist, dann wird die nächste offene Operation von diesem Job genommen durch nextOperation[jobId].
Operationen eines Jobs werden dann in ihrer richtigen Reihenfolge eingeplant.


## DwellStartDecoder
Zufällig, aber schaut auf Blocking, meine Dwell-Ketten und Bad-Kapazität.
"Cold" Start

decodeOnce baut einen Versuch, kann aber null zurückgeben, da sie zufällig entscheidet und die Anlage Blockings hat. Es könnten Sackgassen aufkommen also dass alle Bäder blockiert sind. 
Es gibt 20 max attepnds, sonst wird eine Exception geworfen. 

Solange dann noch nicht alles eingeplant ist mit !isGoal werden erstmal alle validen nächsten Züge eingesammelt durch getAvailableOperations(state)
Falls es zu einer Sackgasse kommt (candidates.isEmpty) wird null returned. 
Mit pick wird eines ausgewählt

dwellchain
Es gibt max dwell time
Das ergibt eine Kette von Operationen, die man nur am Stück platzieren kann.
dwellChain sammelt für den Job diese Kette. - also sie hängt Operationen an, solange die aktuelle ein Limit hat.

Placed - sagt welche Operation auf welcher Maschine zu welcher Zeit eingeplant wurde also Reihenfolge & Startzeit

machineOperations.sort() sortiert die Operationen auf der Maschine nach Startzeit, dann hat man die Reihenfolge auf einer Maschine
Bei operations.add(placedOperation.operation()) wird dann nur die Operation genommen und nicht die Zeit

Ich habe mehrere mögliche Kandidaten und Zeiten, wann diese fertig sind. Mit pick() wird die kleinste Zahl genommen, also die Operation, die am schnellsten fertig ist.
Wenn ich immer den besten Kandidaten nehmen würde, bekomme ich jedes Mal die gleiche Startlösung. Bei Tabu Search möchte ich aber verschiedene Starts ausprobieren.
Es wird daher durch den restricted-Wert zufällig innerhalb der guten Kandidaten ausgewählt

Exception in custom one


## StartDecoder
Lässt Beam Search (constructive) laufen und nimmt die gute Lösung als Start.
"Warm" Start

Delegiert die Startlösung.
Wenn etwas schiefläuft, wird der kalte start gewählt.

consumed flag sorgt dafür, dass nur einmal diese Suche verwendet wird, da Beam Search recht aufwändig ist.