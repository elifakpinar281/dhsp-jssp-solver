package at.fhv.solver;

import at.fhv.model.jssp.Job;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Machine;
import at.fhv.model.jssp.Operation;

import java.util.List;

public final class LowerBound {
    private LowerBound() {}

    public static int forJssp(JsspProblem problem) {
        int value = longestJob(problem);
        value = Math.max(value, busiestMachine(problem));
        value = Math.max(value, busiestMachineWithTimeBeforeAndAfter(problem));
        return value;
    }

    private static int longestJob(JsspProblem problem) {
        int longest = 0;
        for (Job job : problem.getJobs()) {
            int jobTime = 0;
            for (Operation step : job.operations()) {
                jobTime = jobTime + step.processingTime();
            }
            if (jobTime > longest) { longest = jobTime; }
        }
        return longest;
    }

    private static int busiestMachine(JsspProblem problem) {
        long[] workPerMachine = workPerMachine(problem);

        int busiest = 0;
        for (Machine machine : problem.getMachines()) {
            int id = machine.machineId();
            int baths = Math.max(1, problem.capacityOf(id));
            int value = roundUp(workPerMachine[id], baths);
            if (value > busiest) { busiest = value; }
        }
        return busiest;
    }

    private static int busiestMachineWithTimeBeforeAndAfter(JsspProblem problem) {
        int machineCount = problem.getMachines().size();
        long[] work = new long[machineCount];
        int[] smallestTimeBefore = new int[machineCount];
        int[] smallestTimeAfter = new int[machineCount];

        for (int id = 0; id < machineCount; id++) {
            smallestTimeBefore[id] = Integer.MAX_VALUE;
            smallestTimeAfter[id] = Integer.MAX_VALUE;
        }

        for (Job job : problem.getJobs()) {
            List<Operation> steps = job.operations();

            int jobTotal = 0;
            for (Operation step : steps) {
                jobTotal = jobTotal + step.processingTime();
            }

            int timeBefore = 0;
            for (Operation step : steps) {
                int id = step.machineId();
                int timeAfter = jobTotal - timeBefore - step.processingTime();

                work[id] = work[id] + step.processingTime();
                if (timeBefore < smallestTimeBefore[id]) { smallestTimeBefore[id] = timeBefore; }
                if (timeAfter < smallestTimeAfter[id]) { smallestTimeAfter[id] = timeAfter; }

                timeBefore = timeBefore + step.processingTime();
            }
        }

        int busiest = 0;
        for (Machine machine : problem.getMachines()) {
            int id = machine.machineId();
            if (work[id] == 0) {
                continue;
            }
            int baths = Math.max(1, problem.capacityOf(id));
            int value = smallestTimeBefore[id] + roundUp(work[id], baths) + smallestTimeAfter[id];
            if (value > busiest) { busiest = value; }
        }
        return busiest;
    }

    private static long[] workPerMachine(JsspProblem problem) {
        long[] work = new long[problem.getMachines().size()];
        for (Job job : problem.getJobs()) {
            for (Operation step : job.operations()) {
                work[step.machineId()] = work[step.machineId()] + step.processingTime();
            }
        }
        return work;
    }

    private static int roundUp(long a, int b) {
        return (int) ((a + b - 1) / b);
    }
}