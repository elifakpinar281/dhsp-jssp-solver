## Greedy
Im Frontier sind die Nodes, die noch "angeschaut" werden sollen. Diese sind sortiert, sodass der beste immer als Nächstes drankommt.
Frontier ist eine PriorityQueue. Mit den Comparator lege ich fest, was "klein" ist. Zuerst wird nach Heuristik verglichen - kleinere Bewertung gewinnt hier. Bei Gleichstand werden Nodes bevorzugt, bei dem mehr Operationen schon eingeplant sind.
Reached ist eine Menge von States, die ich schon gesehen habe, damit ich mir manche "Pläne" nicht mehrmals ansehe.

Es wird immer das vielversprechendste Node genommen mit poll() -> kleinste Heuristic bzw. greedy Entscheidung
Es wird mit isGoal überprüft, ob schon fertig
Expandieren -> Nodes erzeugen von einem und jedes neue zur Frontier hinzufügen. (applyOperation kann auch null liefern, wenn die Operation gerade nicht platzierbar ist - solche Kinder fallen einfach weg)
Reached verhindert, dass ich denselben State (Teilplan) mehrmals einreihe und nochmal erforsche.

Wenn ich zu viele Nodes expandier oder zu viele States gesehen habe, höre ich mit dem Suchen auf und erzwinge einen Abschluss vom deepest node.
Wenn es nichts im frontier gibt & auch kein goal gefunden, dann wird null zurückgeben (keine Lösung gefunden)

Volle Suche -> behält eine Frontier und kann zu einem anderen Zweig zurückgehen und den probieren. -> Greedy Best-First Search
completeGreedily() -> Fallback - Greedy also kein Backtracking und keine Frontier
Vom tiefsten Knoten aus schaut es bei jedem Schritt die Kinder an, nimmt das einzelne bestbewertete, geht dorthin und wiederholt das, bis es fertig ist


## Beam Search
Suche mit begrenzter Breite. Es wird Ebene für Ebene durchgegangen (1 Ebene = wie viele Operationen schon eingeplant sind). Pro Ebene werden nur die besten k Nodes behalten.
Der Rest wird weggeworfen ("gepruned").
Die Suche ist somit nicht vollständig und findet nicht garantiert die optimale Lösung.

levels ist TreeMap<Integer, List<Node>>
Key ist Tiefe (scheduledCount) und Value die Nodes auf dieser Tiefe.
TreeMap hält die Keys sortiert, darum gibt pollFirstEntry() immer die kleinste "offene" Tiefe zuerst.

visited -> schon gesehene States, damit ich keine doppelten anschaue
bestGoal -> bisher beste gefundene Ziel. Beam Search stoppt nicht beim ersten Goal, sondern sucht weiter und merkt sich das mit dem kleinsten Makespan.

Für jeden Node im Beam wird überprüft
- Ist es ein Goal? -> Dann wird nicht mehr expandiert. Es wird geprüft ob kleinerer Makespan als mein BestGoal - gegebenfalls bestGoal updaten, dann weitermachen
- Sonst expandieren -> applyOperation kann null liefern - fällt dann weg. Jedes neue Kind wird unter seiner eigenen depth einsortiert (eine dwell chain kann mehrere Ebenen weiterspringen)

Wenn ich zu viel expandiert habe kann ich aufhören und bestGoal zurückgeben.
Am Ende wären alle Ebenen abgearbeitet -> bestGoal zurückgeben

Heuristik gibt an, welche Nodes "überleben"


## BULB
Man darf eine begrenzte Anzahl Man von der besten Wahl abweichen und eine Alternative probieren.
maxDiscrepancies legt fest, wie oft das erlaubt ist.

Slice ist ein Stück aus der Liste
Also wenn ich A B C überprüfe dann wäre das eine Slice. 
D E F wäre eine andere Slice

Discrepancy heißt jetzt, dass ich eine Slice nehme, die nicht die beste ist.

in solve() wird mehr Backtracking erlaubt.
- Anlauf mit discrepancies = 0 -> reiner Beam und es ist keine Abweichung erlaubt
- Findet dies kein Ziel, dann Anlauf mit 1 -> Beam, der einmal abweichen darf
- Dann bis maxDiscrepancies

Jeder Anlauf fängt bei null an (hashtable.clear())
Man sucht absichtlich mehrmals. 
Sobald ein Ablauf ein Ziel findet, wird zurückgegeben.

-1.0 ... normaler Slice - es wird einfach weiter in die depth gesucht
1.0 ... GOAL_FOUND
+ unendlich ... NO_PATH also Sackgasse bzw. Speicherlimit erreicht
value >= 0 ... entweder Ziel oder Sackgasse - also ein Endzustand für den Zweig
pathLength < NO_PATH ... ein Ziel wurde erreicht

BULBNode hat zusätzlich zum normalen Node noch eine dDepth - eine Ebenen Tiefe also wie viele Expansions depths von dem Startnode
BULBSliceResult ist Ergebnis eines "Slice Versuchs" -> slice sind die gewählten KNoten, value ist idk, index idk

hashtable -> schon gesehene States werden nicht doppelt angesehen & auch die aktive Suche wird da gespeichert - beim Backtracken werden Slices rausgenommen durch removeFromTable. 
Ab Index wird durch die Sucessors gegangen & bis zu k neue Nodes "aufgenommen" - die in die hashtable gespeichert werden. Wenn die hashtable zu groß ist (maxStored) - dann wird dieser Slice entfernt und NO_PATH gesetzt. Zurückgegeben wird dann Slice, Value -1.0 und der neue index
Der Index gibt an, wo die nächste Slice beginnt


BULBProbe(depth, discrepancies) gibt an, welcher Slice als Nächstes untersucht wird
Wenn ein Goal gefunden ist oder zB Sackgasse gibt dann wird es durch nextSlice() fertig - ERgebnis wird einfach zurückgegeben (result.value())
Wenn discrepancies gleich 0 ist, dann gibt es keine Abweichungen mehr, ich nehme dann die beste Slice und habe dann sozusagen eine normale Beam Suche.
Wenn discrepancies über 0 ist, dann habe ich noch Abweichungen. Kann also nochmal eine andere Slice ausprobieren.
Die beste Slice wird dann einfach kurz entfernt (removeFromTable), eine Alternative wird untersucht, Discrepancy-Zahl wird um 1 reduziert. Und weiter. Index geht immer weiter zu den nächsten Slices.
Wenn keine Alternative geht, dann wird die beste Slice genommen & discrepancies gesetzt (keine Abweichung wenn ich die beste Slice wieder nehme).

compareStates vergleicht States & dient nur als Tie-Breaker beim Sortieren


## Beamstack
Beamstack ist vollständig und findet garantiert die optimale Lösung, wenn man ihn lange genug laufen lässt.
Beam Search mit einer "Stack", der sich merkt, was weggeschnitten wurde - kann dann zurückgehen/backtracken
Beim "Wegwerfen" bzw pruning wird der f-Wert gemerkt - dieser sagt, ab wann es abgeschnitten worden ist.
In einem späteren Durchlauf (Sweep) kann es dann die pruned Nodes nachholen.

upperBound -> mein niedrigstes Makespan
f-Wert -> die untere Schranke wenn ich von einem Node aus gehe. wenn es f > upperBound schon ist dann wird dieser Node nicht weiter untersucht
(f wird nie überschätzt -> deshalb verliert man durch das Wegwerfen nie die optimale Lösung)

Die funktion f kommt eigentlich aus max(jobBound, machineBound) - beide dieser Bounds sagen, wie groß der Makespan mind. sein muss
Grund jobBound ->
Job 1 hat bereits 50 (aktuelle Zeit)
Job 1 muss noch 20 + 30 + 40
Also jobBound = 50 + 20 + 30 + 40 = 140
Dieser Job kann frühestens bei 140 fertig sein, also dann der gesamte Plan nicht vor 140 ist
Wir nehmen hier irgendwie immer den langsamsten Job, da das ganze erst fertig ist, wann alle Jobs es sind

Grund machineBound ->
Pro Maschine wird die restliche Last (Summe der Restzeiten aller Ops auf dieser Maschine) gesammelt.
Wenn eine Maschine capacity Bäder hat, braucht sie mind. ca. Last/capacity Zeit -> auch eine Untergrenze für den Makespan.
Es wird die Maschine mit der höchsten Untergrenze genommen.

Range
Range sagt, ab welchen f Werten mich Nodes noch interessieren (nur Nodes mit fmin <= f < fmax kommen auf dieser Ebene durch)
So kann die Suche nach und nach immer mehr vom Suchraum abdecken
items = List<Range> -> speichert für jede Ebene die Range = der eigentliche "Beam Stack" (das Gedächtnis)
Ein Sweep ist ein Durchlauf durch den Baum mit den aktuell gespeicherten Ranges. Es macht Beam Search und speichert die pruned Nodes (über fmax), damit sie später in einem Sweep untersucht werden können.

pruneLayer -> die Knoten werden nach f sortiert und die besten kommen zuerst
fmax -> wenn ich in einem Sweep alles bis f<120 untersucht habe weiß ich dann das ab 120 der Bereich ist, den ich noch nicht untersucht habe
(fmax = kleinster f-Wert den ich gerade weggeworfen habe)

Nach jedem Sweep werden die Ranges in items verschoben, damit der nächste Sweep ein anderes f-Band anschaut
- Bessere? -> upperBound updaten, bestGoal merken. Ranges die ganz über der neuen Schranke liegen wegwerfen, oberstes Fenster auf das nächste Band schieben (fmin = altes fmax, fmax = upperBound).
- Nichts Besseres? -> leere Fenster vom Stack abräumen (backtracken nach oben), oberstes Fenster auf sein nächstes Band schieben.

Ende wenn items komplett leer ist -> jeder f-Bereich ist abgedeckt -> es kann nichts Besseres mehr geben






