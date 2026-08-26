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
