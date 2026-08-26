package at.fhv.solver;

import at.fhv.model.jssp.JsspProblem;
import at.fhv.solver.impl.tabu.MachineSequences;

// Start Decoder ist recht interessant, da es einen großen Unterschied macht, wo die Suche startet :)
public interface IStartDecoder {
    MachineSequences decode(JsspProblem problem);
}
