package at.fhv.benchmark;

import at.fhv.model.jssp.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        checkMachineOverlap(scheduledOperations, violations);
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
        for (ScheduledOperation operation : scheduledOperations) {
            if (operation.startTime() < 0) {
                violations.add("Operation/Job " + operation.jobId() + " has a negative starttime of: "  + operation.startTime());
            }

            if (operation.endTime() < 0) {
                violations.add("Operation/Job " + operation.jobId() + " has a negative endtime of: "  + operation.endTime());
            }

            if (operation.startTime() > operation.endTime()) {
                violations.add("Operation/Job " + operation.jobId() + " has a starttime of " + operation.startTime() + ", which is greater than its endtime " + operation.endTime());
            }
        }
    }

    private void checkMachineOverlap(List<ScheduledOperation> scheduledOperations, List<String> violations) {
        Map<Integer, List<ScheduledOperation>> planned = new HashMap<>();
        for (ScheduledOperation scheduledOperation : scheduledOperations) {
            int machine = scheduledOperation.machineId();
            if (planned.containsKey(machine)) {
                planned.get(machine).add(scheduledOperation);
            } else {
                List<ScheduledOperation> machineOperations = new ArrayList<>();
                machineOperations.add(scheduledOperation);
                planned.put(machine, machineOperations);
            }
        }

        for (Map.Entry<Integer, List<ScheduledOperation>> entry : planned.entrySet()) {
            List<ScheduledOperation> machineOperations = entry.getValue();

            for (int i = 0; i < machineOperations.size(); i++ ) {
                ScheduledOperation op1 = machineOperations.get(i);
                for (int j = i + 1; j < machineOperations.size(); j++) {
                    ScheduledOperation op2 = machineOperations.get(j);

                    if (op1.startTime() < op2.endTime() && op2.startTime() < op1.endTime()) {
                        violations.add("Overlap 1: " + op1 + ", and Operation 2: " + op2 + " overlap in time");

                    }
                }
            }
        }
    }

    public static int makespan(Schedule schedule) {
        int makespan = 0;
        for (ScheduledOperation scheduledOperation : schedule.operations()) {
            if (scheduledOperation.endTime() > makespan) { makespan = scheduledOperation.endTime(); }
        }
        return makespan;
    }
}
