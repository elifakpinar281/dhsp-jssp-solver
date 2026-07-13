package at.fhv.solver;

import at.fhv.model.jssp.JsspProblem;
import at.fhv.solver.impl.tabu.MachineSequences;

public interface IStartDecoder {
    MachineSequences decode(JsspProblem problem);
}
