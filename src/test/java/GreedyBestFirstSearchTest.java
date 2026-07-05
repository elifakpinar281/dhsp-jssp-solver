import at.fhv.evaluation.implementation.MakespanEstimateHeuristic;
import at.fhv.model.jssp.*;
import at.fhv.solver.implementation.GreedyBestFirstSearch;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;


import java.util.List;

class GreedyBestFirstSearchTest {
    @Test
    void testGreedyBestFirstSearch() {
        Job job0 = new Job(0, List.of(
                new Operation(0, 0, 0, 3),
                new Operation(1, 0, 1, 2)
        ));

        Job job1 = new Job(1, List.of(
                new Operation(0, 1, 1, 4),
                new Operation(1, 1, 0, 1)
        ));

        List<Machine> machines = List.of(new Machine(0), new Machine(1));
        JsspProblem jsspProblem = new JsspProblem(machines, List.of(job0, job1));

        GreedyBestFirstSearch solver = new GreedyBestFirstSearch(new MakespanEstimateHeuristic(jsspProblem));
        Schedule schedule = solver.solve(jsspProblem);

        int makespan = 0;
        for (ScheduledOperation operation : schedule.operations()) {
            if (operation.endTime() > makespan) {
                makespan = operation.endTime();
            }
        }
        assertEquals(6, makespan);
    }
}
