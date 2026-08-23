package at.fhv.solver.impl.decoder;

import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Schedule;
import at.fhv.solver.ISearchAlgorithm;
import at.fhv.solver.IStartDecoder;
import at.fhv.solver.impl.tabu.MachineSequences;

public class StartDecoder implements IStartDecoder {
    private final ISearchAlgorithm constructive;
    private final IStartDecoder fallback;
    private boolean consumed = false;

    public StartDecoder(ISearchAlgorithm constructive, IStartDecoder fallback) {
        this.constructive = constructive;
        this.fallback = fallback;
    }

    @Override
    public MachineSequences decode(JsspProblem jsspProblem) {
        if (consumed) { return fallback.decode(jsspProblem); }
        consumed = true;
        Schedule schedule = constructive.solve(jsspProblem);
        if (schedule == null) { return fallback.decode(jsspProblem); }

        return MachineSequences.fromSchedule(jsspProblem, schedule);
    }
}
