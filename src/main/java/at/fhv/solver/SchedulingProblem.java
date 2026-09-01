package at.fhv.solver;

import at.fhv.model.jssp.Transition;

import java.util.List;

public interface SchedulingProblem {
    State createInitialState();
    boolean isGoal(State state);

    // gültigen Nachfolge-Übergänge eines Zustands - bei JSSP -> eine eingeplante Operation, bei FJSSP zusätzlich Wahl des Bades
    List<Transition> expand(State state);
    int totalOperations();
}
