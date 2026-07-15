package at.fhv.solver.impl.tabu;

import java.util.List;

public interface INeighbourhood {
    public List<Move> generate(ScheduleEvaluator.EvaluationResult evaluationResult, MachineSequences machineSequences);
}
