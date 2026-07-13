package at.fhv.solver.implementation.tabu;

import java.util.HashMap;
import java.util.Map;

public class TabuList{
    private final Map<Move.MoveAttribute, Integer> map = new HashMap<>();


    public boolean isTabu(Move.MoveAttribute attribute, int iteration ) {
        return map.containsKey(attribute) && map.get(attribute) > iteration;
    }

    public void setTabu(Move.MoveAttribute attribute, int iteration) {
        map.put(attribute, iteration);
    }
}
