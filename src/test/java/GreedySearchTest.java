import at.fhv.evaluation.implementation.jssp.MakespanEstimateHeuristic;
import at.fhv.model.jssp.*;
import at.fhv.solver.impl.greedy.GreedySearch;
import at.fhv.solver.validation.ScheduleValidator;
import at.fhv.solver.validation.ValidationResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


import java.util.List;

class GreedySearchTest {
    @Test
    void testGreedySearch() {
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

        GreedySearch solver = new GreedySearch(new MakespanEstimateHeuristic(jsspProblem));
        Schedule schedule = solver.solve(jsspProblem);
        ScheduleValidator validator = new ScheduleValidator();
        ValidationResult result = validator.validateSchedule(jsspProblem, schedule);

        int makespan = 0;
        for (ScheduledOperation operation : schedule.operations()) {
            if (operation.endTime() > makespan) {
                makespan = operation.endTime();
            }
        }
        assertEquals(6, makespan);
        assertTrue(result.valid(), "Schedule not valid: " + result.violations());
    }
}
