package at.fhv.solver.implementation.tabu;

import at.fhv.model.jssp.*;

import java.util.*;

public class ScheduleEvaluator {
    private final JsspProblem jsspProblem;

    public ScheduleEvaluator(JsspProblem jsspProblem) {
        this.jsspProblem = jsspProblem;
    }

    public record EvaluationResult(
            int makespan,
            List<Operation> criticalPath,
            Map<Operation, Integer> head,
            Map<Operation, Integer> tail
    ) {}

    public EvaluationResult evaluate(MachineSequences sequences) {
        Map<Operation, Integer> head = new HashMap<>();
        Map<Operation, Integer> tail = new HashMap<>();

        List<Operation> to = topologicalOrder(sequences);
        for (Operation operation : to) {
            int jobEnd = 0;
            int machineEnd = 0;
            Operation jobPredecessor = jsspProblem.jobPredecessor(operation);
            if (jobPredecessor != null) {
                jobEnd = head.get(jobPredecessor) + jobPredecessor.processingTime();
            }

            Operation machinePredecessor = sequences.machinePredecessor(operation);
            if (machinePredecessor != null) {
                machineEnd = head.get(machinePredecessor) + machinePredecessor.processingTime();
            }
            head.put(operation, Math.max(jobEnd, machineEnd));
        }

        int makespan = 0;
        for (Operation operation : to) {
            int finish = head.get(operation)+operation.processingTime();
            makespan = Math.max(makespan, finish);
        }

        List<Operation> reverseTo = new ArrayList<>(to);
        Collections.reverse(reverseTo);

        for (Operation operation : reverseTo) {
            int jobTail = 0;
            int machineTail = 0;

            Operation jobSuccessor = jsspProblem.jobSuccessor(operation);
            if (jobSuccessor != null) {
                jobTail = tail.get(jobSuccessor) + jobSuccessor.processingTime();
            }

            Operation machineSuccessor = sequences.machineSuccessor(operation);
            if (machineSuccessor != null) {
                machineTail = tail.get(machineSuccessor) + machineSuccessor.processingTime();
            }

            tail.put(operation, Math.max(jobTail, machineTail));
        }

        List<Operation> criticalPath = new ArrayList<>();

        for (Operation operation : to) {
            if (head.get(operation) + operation.processingTime() + tail.get(operation) == makespan) {
                criticalPath.add(operation);
            }
        }

        return new EvaluationResult(makespan, criticalPath, head, tail);
    }

    private List<Operation> topologicalOrder(MachineSequences sequences) {
        List<Operation> operations = new ArrayList<>();
        for (Job job : jsspProblem.getJobs()) {
            operations.addAll(job.operations());
        }

        if (operations.isEmpty()) {return new ArrayList<>();}

        Map<Operation, Integer> inDegree = new HashMap<>();
        Queue<Operation> queue = new LinkedList<>();

        for (Operation operation : operations) {
            int degree = 0;
            if (jsspProblem.jobPredecessor(operation) != null) {
                degree++;
            }

            if (sequences.machinePredecessor(operation) != null) {
                degree++;
            }

            inDegree.put(operation, degree);
        }

        for (Operation operation : operations) {
            if (inDegree.get(operation) == 0) {
                queue.add(operation);
            }
        }

        List<Operation> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            Operation current = queue.poll();
            result.add(current);

            List<Operation> successors = new ArrayList<>();
            Operation jobSuccessors = jsspProblem.jobSuccessor(current);
            if (jobSuccessors != null) {successors.add(jobSuccessors);}

            Operation machineSuccessors = sequences.machineSuccessor(current);
            if (machineSuccessors != null) {successors.add(machineSuccessors);}

            for (Operation next : successors) {
                    inDegree.put(next, inDegree.get(next) - 1);
                    if (inDegree.get(next) == 0) {
                        queue.add(next);
                    }
                }
            }
        if (result.size() != operations.size()) {
            throw new IllegalStateException("Cycle detected");
        }

        return result;
    }

    private int headValue(Map<Operation, Integer> head, Operation operation) {
        if (operation == null ) {return 0;}
        return head.get(operation) + operation.processingTime();
    }

    private int tailValue(Map<Operation, Integer> tail, Operation operation) {
        if (operation == null) {return 0;}
        return tail.get(operation) + operation.processingTime();
    }

    public Schedule toSchedule(EvaluationResult result) {
        List<ScheduledOperation> scheduledOperations = new ArrayList<>();

        for (Job job : jsspProblem.getJobs()) {
            for (Operation op : job.operations()) {
                int start = result.head().get(op);
                int end = start + op.processingTime();

                scheduledOperations.add(
                        new ScheduledOperation(
                                op.jobId(),
                                op.machineId(),
                                start,
                                end
                        )
                );

            }
        }
        return new Schedule(scheduledOperations);
    }

    public EvaluationResult evaluateMove(MachineSequences sequences, Move move, Map<Operation, Integer> oldHead, Map<Operation, Integer> oldTail) {
        MachineSequences newSequences = sequences.swapped(move);

        Map<Operation, Integer> head = new HashMap<>(oldHead);
        Map<Operation, Integer> tail = new HashMap<>(oldTail);

        Set<Operation> affectedForward = forwardReachable(Set.of(move.operationA(), move.operationB()), newSequences);

        for (Operation op : topologicalOrder(affectedForward, newSequences)) {
            int jobEnd = headValue(head, jsspProblem.jobPredecessor(op));
            int machineEnd = headValue(head, newSequences.machinePredecessor(op));
            head.put(op, Math.max(jobEnd, machineEnd));
        }

        Set<Operation> affectedBackward = backwardReachable(Set.of(move.operationA(), move.operationB()), newSequences);
        List<Operation> reverse = new ArrayList<>(topologicalOrder(affectedBackward, newSequences));
        Collections.reverse(reverse);

        for (Operation op : reverse) {
            int jobTail = tailValue(tail, jsspProblem.jobSuccessor(op));
            int machineTail = tailValue(tail, newSequences.machineSuccessor(op));
            tail.put(op, Math.max(jobTail, machineTail));
        }

        int makespan = 0;

        for(Operation op : topologicalOrder(newSequences)) {
            makespan = Math.max(makespan, head.get(op) + op.processingTime());
        }


        List<Operation> criticalPath = new ArrayList<>();

        for (Operation op : topologicalOrder(newSequences)) {
            if (head.get(op) + op.processingTime() + tail.get(op) == makespan) {
                criticalPath.add(op);
            }
        }

        return new EvaluationResult(makespan, criticalPath, head, tail);
    }

    private Set<Operation> forwardReachable(Set<Operation> start, MachineSequences sequences) {
        Set<Operation> visited = new HashSet<>();
        Queue<Operation> queue = new ArrayDeque<>(start);

        while (!queue.isEmpty()) {
            Operation current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }

            Operation jobNext = jsspProblem.jobSuccessor(current);

            if (jobNext != null) {
                queue.add(jobNext);
            }

            Operation machineNext = sequences.machineSuccessor(current);

            if (machineNext != null) {
                queue.add(machineNext);
            }
        }
        return visited;
    }

    private Set<Operation> backwardReachable(Set<Operation> start, MachineSequences sequences) {
        Set<Operation> visited = new HashSet<>();
        Queue<Operation> queue = new LinkedList<>(start);

        while (!queue.isEmpty()) {
            Operation current = queue.poll();

            if (!visited.add(current)) {
                continue;
            }

            Operation jobPrev = jsspProblem.jobPredecessor(current);

            if (jobPrev != null) {
                queue.add(jobPrev);
            }

            Operation machinePrev = sequences.machinePredecessor(current);

            if (machinePrev != null) {
                queue.add(machinePrev);
            }
        }
        return visited;
    }

    private List<Operation> topologicalOrder(Set<Operation> operations, MachineSequences sequences) {
        Map<Operation,Integer> degree = new HashMap<>();
        Queue<Operation> queue = new LinkedList<>();

        for(Operation op : operations){
            int count = 0;
            Operation jobPrev = jsspProblem.jobPredecessor(op);

            if(jobPrev != null && operations.contains(jobPrev)){
                count++;
            }

            Operation machinePrev = sequences.machinePredecessor(op);

            if(machinePrev != null && operations.contains(machinePrev)){
                count++;
            }
            degree.put(op,count);
        }

        for(Operation op : operations){
            if(degree.get(op)==0){
                queue.add(op);
            }
        }

        List<Operation> result = new ArrayList<>();

        while(!queue.isEmpty()){
            Operation current = queue.poll();
            result.add(current);
            List<Operation> successors = new ArrayList<>();
            Operation jobNext = jsspProblem.jobSuccessor(current);

            if(jobNext != null){ successors.add(jobNext);}

            Operation machineNext = sequences.machineSuccessor(current);

            if(machineNext != null){successors.add(machineNext);}
            for(Operation next : successors){
                if(!operations.contains(next)){continue;}
                degree.put(next, degree.get(next)-1);

                if(degree.get(next)==0){queue.add(next);}
            }
        }

        if (result.size() != operations.size()) {
            throw new IllegalStateException("Cycle detected");
        }

        return result;
    }
}
