package at.fhv.solver.impl.decoder;

import at.fhv.model.jssp.Job;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Machine;
import at.fhv.model.jssp.Operation;
import at.fhv.solver.IStartDecoder;
import at.fhv.solver.impl.tabu.MachineSequences;

import java.util.*;

public class DwellStartDecoder implements IStartDecoder {
    private final Random random;
    private final double riskFactor;
    private final double restricted;

    public DwellStartDecoder(Random random) {
        this(random, 1.0, 0.34);
    }

    public DwellStartDecoder(Random random, double riskFactor, double restricted) {
        this.random = random;
        this.riskFactor = riskFactor;
        this.restricted = restricted;
    }

    @Override
    public MachineSequences decode(JsspProblem jsspProblem) {
        int jobCount = jsspProblem.getJobs().size();
        int[] nextOperation = new int[jobCount];
        int[] machineAvailableTime = new int[jsspProblem.getMachines().size()];
        int[] jobAvailableTime = new int[jobCount];

        Map<Integer, List<Operation>> order = new HashMap<>();
        for(Machine machine : jsspProblem.getMachines()) {
            order.put(machine.machineId(), new ArrayList<>());
        }

        int totalOperations = 0;
        for (Job job : jsspProblem.getJobs()) {
            totalOperations += job.operations().size();
        }

        for (int scheduled = 0; scheduled < totalOperations; scheduled++) {
            List<Candidate> candidates = new ArrayList<>();

            for (Job job : jsspProblem.getJobs()) {
                int jobId = job.jobId();
                int id = nextOperation[jobId];
                if (id >= job.operations().size()) { continue; }

                Operation candidate = job.operations().get(id);
                int earliestStart = Math.max(machineAvailableTime[candidate.machineId()], jobAvailableTime[jobId]);
                Integer remainingDwell = dwell(job, id, jobAvailableTime[jobId], earliestStart);
                boolean atRisk = remainingDwell != null && remainingDwell <= riskFactor * candidate.processingTime();
                candidates.add(new Candidate(job, candidate, remainingDwell == null ? Integer.MAX_VALUE : remainingDwell, atRisk));
            }

            if (candidates.isEmpty()) {break;}

            Candidate chosen = pick(candidates);
            int jobId = chosen.job().jobId();
            Operation operation = chosen.operation();
            int earliestStart = Math.max(machineAvailableTime[operation.machineId()], jobAvailableTime[jobId]);
            int endTime = earliestStart + operation.processingTime();

            order.get(operation.machineId()).add(operation);
            nextOperation[jobId]++;
            machineAvailableTime[operation.machineId()] = endTime;
            jobAvailableTime[jobId] = endTime;
        }
        return new MachineSequences(order);

    }

    private Candidate pick(List<Candidate> candidates) {
        List<Candidate> atRisk = candidates.stream().filter(Candidate::atRisk).toList();
        if (atRisk.isEmpty()) { return candidates.get(random.nextInt(candidates.size())); }

        List<Candidate> sorted = new ArrayList<>(atRisk);
        sorted.sort((a, b) -> Integer.compare(a.remainingDwell(), b.remainingDwell()));

        int rclSize = Math.max(1, (int) Math.ceil(sorted.size() * restricted));
        List<Candidate> restricted = sorted.subList(0, Math.min(rclSize, sorted.size()));
        return restricted.get(random.nextInt(restricted.size()));
    }

    private Integer dwell(Job job, int opIndex, int jobAvailableTime, int earliestStart) {
        if (opIndex == 0) { return null; }

        Operation predecessor = job.operations().get(opIndex - 1);
        if (!predecessor.hasDwellLimit()) { return null; }

        int deadline = jobAvailableTime + predecessor.maxDwellTime();
        return deadline - earliestStart;
    }


}
