package at.fhv.benchmark;

import at.fhv.model.jssp.*;

import java.util.ArrayList;
import java.util.List;

public class ScheduleValidator {
    public ValidationResult validateSchedule(JsspProblem jsspProblem, Schedule schedule) {
        List<String> violations = new ArrayList<>();
        if (schedule == null || schedule.operations() == null) {
            violations.add("Schedule or operations is null");
            return new ValidationResult(false, violations);
        }
        List<ScheduledOperation> scheduledOperations = schedule.operations();

        checkOperationCount(jsspProblem, scheduledOperations, violations);
        checkJobRouting(jsspProblem, scheduledOperations, violations);
        checkMachineOverlap(jsspProblem, scheduledOperations, violations);
        checkTimeConsistency(scheduledOperations, violations);

        return new ValidationResult(violations.isEmpty(), violations);
    }

    private void checkOperationCount(JsspProblem jsspProblem, List<ScheduledOperation> scheduledOperations, List<String> violations) {
        int expected = 0;
        for (Job job : jsspProblem.getJobs()) {
            expected = expected+job.operations().size();
        }
        if (scheduledOperations.size() != expected) { violations.add("Mismatch, expected: " + expected + ", scheduled: " + scheduledOperations.size()); }
    }

    private void checkJobRouting(JsspProblem jsspProblem, List<ScheduledOperation> scheduledOperations, List<String> violations) {
        for (Job job : jsspProblem.getJobs()) {
            int jobid = job.jobId();
            List<ScheduledOperation> jobOperations = new ArrayList<>();
            for (ScheduledOperation scheduledOperation : scheduledOperations) {
                if (scheduledOperation.jobId() == jobid) { jobOperations.add(scheduledOperation); }
            }
            jobOperations.sort((j1, j2) -> Integer.compare(j1.startTime(), j2.startTime()));
            List<Operation> required = job.operations();

            if (jobOperations.size() != required.size()) {
                violations.add("For job " + jobid + "- expected: " + required.size() + ", found: " + jobOperations.size());
                continue;
            }

            int previous = 0;
            for (int i = 0; i < required.size(); i++) {
                ScheduledOperation scheduledOperation = jobOperations.get(i);
                Operation expected = required.get(i);

                if (scheduledOperation.machineId() != expected.machineId()) {
                    violations.add("Job " + jobid + " step " + i + " runs on " + scheduledOperation.machineId() + " but should run on " + expected.machineId());
                }

                int duration = scheduledOperation.endTime()-scheduledOperation.startTime();
                if (duration != expected.processingTime()) {
                    violations.add("Job " + jobid + " step " + i + "- duration " + duration + ", processing time should be " + expected.processingTime());
                }

                if (scheduledOperation.startTime() < previous) {
                    violations.add("Job " + jobid + " step " + i + " starts at " + scheduledOperation.startTime() + " before previous step ended at " + previous);
                }
                previous = scheduledOperation.endTime();
            }
        }
    }

    private void checkTimeConsistency(List<ScheduledOperation> scheduledOperations, List<String> violations) {
        // TODO
    }

    private void checkMachineOverlap(JsspProblem jsspProblem, List<ScheduledOperation> scheduledOperations, List<String> violations) {
        // TODO
    }

    public static int makespan(Schedule schedule) {
        int makespan = 0;
        for (ScheduledOperation scheduledOperation : schedule.operations()) {
            if (scheduledOperation.endTime() > makespan) { makespan = scheduledOperation.endTime(); }
        }
        return makespan;
    }
}
