package at.fhv.solver.implementation.tabu;

import at.fhv.evaluation.IHeuristic;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Schedule;
import at.fhv.solver.ISearchAlgorithm;

public class TabuSearch implements ISearchAlgorithm {
    IHeuristic heuristic;

    public TabuSearch(IHeuristic heuristic) {
        this.heuristic = heuristic;
    }

    @Override
    public Schedule solve(JsspProblem jsspProblem) {



        return null;
    }




}
