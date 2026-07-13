package at.fhv.solver.implementation.tabu;

import java.util.List;

public interface INeighbourhood {
    public List<Move> generate(ScheduleEvaluator.EvaluationResult evaluationResult);
}
