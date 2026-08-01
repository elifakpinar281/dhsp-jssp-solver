import at.fhv.model.jssp.*;
import at.fhv.solver.IStartDecoder;
import at.fhv.solver.impl.decoder.RandomStartDecoder;
import at.fhv.solver.impl.tabu.INeighbourhood;
import at.fhv.solver.impl.tabu.ScheduleEvaluator;
import at.fhv.solver.impl.tabu.TabuSearch;
import at.fhv.solver.impl.tabu.impl.N5Neighbourhood;
import at.fhv.solver.validation.ScheduleValidator;
import at.fhv.solver.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabuSearchTest {

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

    private TabuSearch buildSolver(JsspProblem problem) {
        ScheduleEvaluator evaluator = new ScheduleEvaluator(problem);
        INeighbourhood neighbourhood = new N5Neighbourhood();
        IStartDecoder decoder = new RandomStartDecoder(new Random(42));
        return new TabuSearch(evaluator, neighbourhood, 5, 50, decoder);
    }

    @Test
    void tabuFindsOptimum() {
        JsspProblem problem = build();
        TabuSearch solver = buildSolver(problem);
        Schedule schedule = solver.solve(problem);

        ScheduleValidator validator = new ScheduleValidator();
        ValidationResult result = validator.validateSchedule(problem, schedule);
        int makespan = ScheduleValidator.makespan(schedule);

        assertEquals(6, makespan);
        assertTrue(result.valid(), "Schedule not valid: " + result.violations());
    }

    @Test
    void tabuReturnsValidSchedule() {
        JsspProblem problem = build();
        TabuSearch solver = buildSolver(problem);
        Schedule schedule = solver.solve(problem);

        ScheduleValidator validator = new ScheduleValidator();
        ValidationResult result = validator.validateSchedule(problem, schedule);
        int makespan = ScheduleValidator.makespan(schedule);

        assertTrue(result.valid(), "Schedule not valid: " + result.violations());
        assertTrue(makespan >= 6, "Makespan can not be less than 6, it was: " + makespan);
    }
}