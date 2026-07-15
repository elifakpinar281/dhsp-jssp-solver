package at.fhv.solver.impl.decoder;

import at.fhv.model.jssp.*;
import at.fhv.solver.IStartDecoder;
import at.fhv.solver.State;
import at.fhv.solver.impl.tabu.MachineSequences;

import java.util.*;

public class DwellStartDecoder implements IStartDecoder {
    private final Random random;
    private final double restricted;

    public DwellStartDecoder(Random random) {
        this(random, 0.34);
    }

    public DwellStartDecoder(Random random, double riskFactor, double restricted) {
        this(random, restricted);
    }

    public DwellStartDecoder(Random random, double restricted) {
        this.random = random;
        this.restricted = restricted;
    }

    private record Candidate(Operation operation, Transition transition, int completion) {}

    private static final int MAX_ATTEMPTS = 20;

    @Override
    public MachineSequences decode(JsspProblem jsspProblem) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            MachineSequences sequences = decodeOnce(jsspProblem);
            if (sequences != null) { return sequences; }
        }
        throw new IllegalStateException("No feasible start solution found after " + MAX_ATTEMPTS + " attempts");
    }

    private MachineSequences decodeOnce(JsspProblem jsspProblem) {
        Map<Integer, List<Placed>> placed = new HashMap<>();
        Map<Integer, Integer> capacities = new HashMap<>();
        for (Machine machine : jsspProblem.getMachines()) {
            placed.put(machine.machineId(), new ArrayList<>());
            capacities.put(machine.machineId(), machine.capacity());
        }

        State state = createInitialState(jsspProblem);

        while (!jsspProblem.isGoal(state)) {
            List<Candidate> candidates = new ArrayList<>();

            for (Operation operation : jsspProblem.getAvailableOperations(state)) {
                Transition transition = jsspProblem.applyOperation(state, operation);
                if (transition == null) { continue; }

                List<ScheduledOperation> scheduled = transition.scheduledOperations();
                int completion = scheduled.get(scheduled.size() - 1).endTime();
                candidates.add(new Candidate(operation, transition, completion));
            }

            if (candidates.isEmpty()) { return null; }

            Candidate chosen = pick(candidates);
            List<Operation> chain = jsspProblem.dwellChain(chosen.operation().jobId(), state.nextOperation()[chosen.operation().jobId()]);
            List<ScheduledOperation> scheduled = chosen.transition().scheduledOperations();

            for (int i = 0; i < chain.size(); i++) {
                placed.get(chain.get(i).machineId()).add(new Placed(chain.get(i), scheduled.get(i).startTime()));
            }
            state = chosen.transition().state();
        }

        Map<Integer, List<Operation>> order = new HashMap<>();
        for (Map.Entry<Integer, List<Placed>> entry : placed.entrySet()) {
            List<Placed> machineOperations = new ArrayList<>(entry.getValue());
            machineOperations.sort(Comparator.comparingInt(Placed::startTime));

            List<Operation> operations = new ArrayList<>();
            for (Placed placedOperation : machineOperations) {
                operations.add(placedOperation.operation());
            }
            order.put(entry.getKey(), operations);
        }
        return new MachineSequences(order, capacities);
    }

    private record Placed(Operation operation, int startTime) {}

    private State createInitialState(JsspProblem jsspProblem) {
        int[] nextOperation = new int[jsspProblem.getJobs().size()];
        int[] bathAvailableTime = new int[jsspProblem.totalBaths()];
        int[] jobAvailableTime = new int[jsspProblem.getJobs().size()];
        int[] jobBath = new int[jsspProblem.getJobs().size()];
        Arrays.fill(jobBath, State.NO_BATH);
        return new State(nextOperation, bathAvailableTime, jobAvailableTime, jobBath);
    }

    private Candidate pick(List<Candidate> candidates) {
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingInt(Candidate::completion));

        int size = Math.max(1, (int) Math.ceil(sorted.size() * restricted));
        return sorted.get(random.nextInt(Math.min(size, sorted.size())));
    }
}