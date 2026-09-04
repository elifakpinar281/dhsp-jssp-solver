import at.fhv.evaluation.implementation.jssp.MakespanEstimateHeuristic;
import at.fhv.model.jssp.*;
import at.fhv.solver.impl.beam.BeamSearch;
import at.fhv.solver.validation.ScheduleValidator;
import at.fhv.solver.validation.ValidationResult;
import at.fhv.stats.SearchStatistics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

class BeamSearchTest {
    private JsspProblem build() {
        Job job0 = new Job(0, List.of(
                new Operation(0, 0, 0, 3),
                new Operation(1, 0, 1, 2)
        ));

        Job job1 = new Job(1, List.of(
                new Operation(0, 1, 1, 4),
                new Operation(1, 1, 0, 1)
        ));
        List<Machine> machines = List.of(new Machine(0), new Machine(1));
        return new JsspProblem(machines, List.of(job0, job1));
    }

    @Test
    void beamFindsOptimum() {
        JsspProblem problem = build();
        BeamSearch solver = new BeamSearch(new MakespanEstimateHeuristic(problem), 5, new SearchStatistics());
        Schedule schedule = solver.solve(problem);

        ScheduleValidator validator = new ScheduleValidator();
        ValidationResult result = validator.validateSchedule(problem, schedule);
        int makespan = ScheduleValidator.makespan(schedule);

        assertEquals(6, makespan);
        assertTrue(result.valid(), "Schedule not valid: " + result.violations());
    }

    @Test
    void narrowBeamReturnsValidSchedule() {
        JsspProblem problem = build();
        BeamSearch solver = new BeamSearch(new MakespanEstimateHeuristic(problem), 1, new SearchStatistics());
        Schedule schedule = solver.solve(problem);

        ScheduleValidator validator = new ScheduleValidator();
        ValidationResult result = validator.validateSchedule(problem, schedule);

        int makespan = ScheduleValidator.makespan(schedule);

        assertTrue(result.valid(), "Schedule not valid: " + result.violations());
        assertTrue(makespan >= 6, "Makespan can not exceed 6, was: " + makespan);
    }
}
