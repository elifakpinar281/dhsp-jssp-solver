package at.fhv.solver.impl.tabu;

import at.fhv.model.fjssp.FjsspProblem;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Schedule;
import at.fhv.model.jssp.ScheduledOperation;
import at.fhv.solver.ISearchAlgorithm;
import at.fhv.solver.SchedulingProblem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FjsspTabuSearch implements ISearchAlgorithm {
    private final ISearchAlgorithm innerTabu;
    private final JsspProblem induced;
    private final FjsspProblem fjssp;

    public FjsspTabuSearch(ISearchAlgorithm innerTabu, JsspProblem induced, FjsspProblem fjssp) {
        this.innerTabu = innerTabu;
        this.induced = induced;
        this.fjssp = fjssp;
    }

    @Override
    public Schedule solve(SchedulingProblem problem) {
        Schedule stationSchedule = innerTabu.solve(induced);
        if (stationSchedule == null) { return null; }
        return assignBaths(stationSchedule);
    }

    private record Task(int job, int station, int start, int end, int leave) {}

    private Schedule assignBaths(Schedule stationSchedule) {
        List<Task> tasks = buildTasks(stationSchedule.operations());

        tasks.sort(Comparator.comparingInt(Task::start));
        Map<Integer, int[]> bathFreeAt = new HashMap<>(); // Station -> freie-ab-Zeit je Bad

        List<ScheduledOperation> result = new ArrayList<>();
        for (Task task : tasks) {
            int capacity = fjssp.stationCapacity(task.station());
            int offset = fjssp.stationOffset(task.station());
            int[] freeAt = bathFreeAt.computeIfAbsent(task.station(), key -> newFreeArray(capacity));

            int lane = firstFreeLane(freeAt, task.start());
            if (lane == -1) { return null; }

            freeAt[lane] = task.leave();
            result.add(new ScheduledOperation(task.job(), offset + lane, task.start(), task.end()));
        }
        return new Schedule(result);
    }

    private int firstFreeLane(int[] freeAt, int start) {
        for (int lane = 0; lane < freeAt.length; lane++) {
            if (freeAt[lane] <= start) { return lane; }
        }
        return -1;
    }

    private int[] newFreeArray(int capacity) {
        int[] freeAt = new int[capacity];
        Arrays.fill(freeAt, Integer.MIN_VALUE);
        return freeAt;
    }

    private List<Task> buildTasks(List<ScheduledOperation> operations) {
        Map<Integer, List<ScheduledOperation>> byJob = new HashMap<>();
        for (ScheduledOperation operation : operations) {
            byJob.computeIfAbsent(operation.jobId(), key -> new ArrayList<>()).add(operation);
        }

        List<Task> tasks = new ArrayList<>();
        for (List<ScheduledOperation> jobOperations : byJob.values()) {
            jobOperations.sort(Comparator.comparingInt(ScheduledOperation::startTime));
            for (int i = 0; i < jobOperations.size(); i++) {
                ScheduledOperation operation = jobOperations.get(i);
                int leave;
                if (fjssp.isBlocking() && i + 1 < jobOperations.size()) {
                    leave = jobOperations.get(i + 1).startTime();
                } else {
                    leave = operation.endTime();
                }
                tasks.add(new Task(operation.jobId(), operation.machineId(), operation.startTime(), operation.endTime(), leave));
            }
        }
        return tasks;
    }
}