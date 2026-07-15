package at.fhv.solver.impl.decoder;

import at.fhv.model.jssp.Job;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Machine;
import at.fhv.model.jssp.Operation;
import at.fhv.solver.IStartDecoder;
import at.fhv.solver.impl.tabu.MachineSequences;

import java.util.*;

public class RandomStartDecoder implements IStartDecoder {
    private final Random random;

    public RandomStartDecoder(Random random) {
        this.random = random;
    }

    @Override
    public MachineSequences decode(JsspProblem problem) {
        List<Integer> jobSequence = new ArrayList<>();
        for (Job job : problem.getJobs()) {
            for (int i = 0; i < job.operations().size(); i++) {
                jobSequence.add(job.jobId());
            }
        }
        Collections.shuffle(jobSequence, random);
        int[] nextOperation = new int[problem.getJobs().size()];

        Map<Integer, List<Operation>> order = new HashMap<>();
        Map<Integer, Integer> capacities = new HashMap<>();

        for (Machine machine : problem.getMachines()) {
            order.put(machine.machineId(), new ArrayList<>());
            capacities.put(machine.machineId(), machine.capacity());
        }

        for (int jobId : jobSequence) {
            Job job = problem.getJobs().get(jobId);
            Operation operation = job.operations().get(nextOperation[jobId]);
            order.get(operation.machineId()).add(operation);
            nextOperation[jobId]++;
        }

        return new MachineSequences(order, capacities);
    }
}
