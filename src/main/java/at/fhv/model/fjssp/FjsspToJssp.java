package at.fhv.model.fjssp;

import at.fhv.model.jssp.Job;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Machine;
import at.fhv.model.jssp.Operation;

import java.util.ArrayList;
import java.util.List;

public final class FjsspToJssp {
    private FjsspToJssp() {}

    public static JsspProblem toJssp(FjsspProblem fjssp) {
        List<Machine> machines = new ArrayList<>();
        for (int station = 0; station < fjssp.stationCount(); station++) {
            machines.add(new Machine(station, fjssp.stationCapacity(station)));
        }

        List<Job> jobs = new ArrayList<>();
        for (FjsspJob fjsspJob : fjssp.getJobs()) {
            List<Operation> operations = new ArrayList<>();
            for (FjsspOperation operation : fjsspJob.operations()) {
                int fullTime = operation.minimalProcessingTime(); // alle Baeder der Station identisch
                operations.add(new Operation(operation.operationId(), operation.jobId(), operation.stationId(), fullTime, operation.maxDwellTime()));
            }
            jobs.add(new Job(fjsspJob.jobId(), operations));
        }

        return new JsspProblem(machines, jobs, fjssp.isBlocking());
    }
}