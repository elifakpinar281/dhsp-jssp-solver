package at.fhv.solver.validation;

import at.fhv.model.jssp.*;

import java.util.ArrayList;
import java.util.Comparator;
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
        checkMachineOverlap(jsspProblem, scheduledOperations, violations);
        checkTimeConsistency(scheduledOperations, violations);
        checkMaxDwell(jsspProblem, scheduledOperations, violations);

        return new ValidationResult(violations.isEmpty(), violations);
    }

    private void checkMaxDwell(JsspProblem jsspProblem, List<ScheduledOperation> scheduledOperations, List<String> violations) {
        for (Job job : jsspProblem.getJobs()) {
            int jobId = job.jobId();
            List<ScheduledOperation> jobOperations = new ArrayList<>();
            for (ScheduledOperation scheduledOperation : scheduledOperations) {
                if (scheduledOperation.jobId() == jobId) { jobOperations.add(scheduledOperation); }
            }
            jobOperations.sort(Comparator.comparingInt(ScheduledOperation::startTime));
            List<Operation> required = job.operations();

            if (jobOperations.size() != required.size()) { continue; }

            for (int i = 0; i < required.size(); i++) {
                Operation operation = required.get(i);

                int dwell = (i + 1 < required.size())
                        ? jobOperations.get(i + 1).startTime() - jobOperations.get(i).startTime()
                        : jobOperations.get(i).endTime() - jobOperations.get(i).startTime();

                if (dwell < operation.processingTime()) {
                    violations.add("Job " + jobId + " step " + i + " (machine " + operation.machineId() + ") dwell " + dwell + " is below min dwell time " + operation.processingTime());
                }
                if (operation.hasDwellLimit() && dwell > operation.maxDwellTime()) {
                    violations.add("Job " + jobId + " step " + i + " (machine " + operation.machineId() + ") dwell " + dwell + " exceeds max dwell time " + operation.maxDwellTime());
                }
            }
        }
    }

    private void checkOperationCount(JsspProblem jsspProblem, List<ScheduledOperation> scheduledOperations, List<String> violations) {
        int expected = 0;
        for (Job job : jsspProblem.getJobs()) {
            expected = expected + job.operations().size();
        }
        if (scheduledOperations.size() != expected) {
            violations.add("Mismatch, expected: " + expected + ", scheduled: " + scheduledOperations.size());
        }
    }

    private void checkJobRouting(JsspProblem jsspProblem, List<ScheduledOperation> scheduledOperations, List<String> violations) {
        for (Job job : jsspProblem.getJobs()) {
            int jobid = job.jobId();
            List<ScheduledOperation> jobOperations = new ArrayList<>();
            for (ScheduledOperation scheduledOperation : scheduledOperations) {
                if (scheduledOperation.jobId() == jobid) { jobOperations.add(scheduledOperation); }
            }
            jobOperations.sort(Comparator.comparingInt(ScheduledOperation::startTime));
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

                int duration = scheduledOperation.endTime() - scheduledOperation.startTime();
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
                violations.add("Operation/Job " + operation.jobId() + " has a negative starttime of: " + operation.startTime());
            }

            if (operation.endTime() < 0) {
                violations.add("Operation/Job " + operation.jobId() + " has a negative endtime of: " + operation.endTime());
            }

            if (operation.startTime() > operation.endTime()) {
                violations.add("Operation/Job " + operation.jobId() + " has a starttime of " + operation.startTime() + ", which is greater than its endtime " + operation.endTime());
            }
        }
    }

    private void checkMachineOverlap(JsspProblem jsspProblem, List<ScheduledOperation> scheduledOperations, List<String> violations) {
        Map<Integer, List<Occupancy>> planned = new HashMap<>();

        for (Job job : jsspProblem.getJobs()) {
            List<ScheduledOperation> jobOperations = new ArrayList<>();
            for (ScheduledOperation scheduledOperation : scheduledOperations) {
                if (scheduledOperation.jobId() == job.jobId()) { jobOperations.add(scheduledOperation); }
            }
            jobOperations.sort(Comparator.comparingInt(ScheduledOperation::startTime));

            for (int i = 0; i < jobOperations.size(); i++) {
                ScheduledOperation current = jobOperations.get(i);
                int leaveTime = (jsspProblem.isBlocking() && i + 1 < jobOperations.size())
                        ? jobOperations.get(i + 1).startTime()
                        : current.endTime();
                planned.computeIfAbsent(current.machineId(), key -> new ArrayList<>()).add(new Occupancy(current.startTime(), leaveTime));
            }
        }

        for (Map.Entry<Integer, List<Occupancy>> entry : planned.entrySet()) {
            int machineId = entry.getKey();
            int capacity = jsspProblem.capacityOf(machineId);
            List<Occupancy> occupancies = entry.getValue();

            for (Occupancy reference : occupancies) {
                int time = reference.enter();
                int concurrent = 0;

                for (Occupancy other : occupancies) {
                    if (other.enter() <= time && time < other.leave()) { concurrent++; }
                }

                if (concurrent > capacity) {
                    violations.add("Machine " + machineId + " holds " + concurrent + " carriers at time " + time + " but capacity is " + capacity);
                    break;
                }
            }
        }
    }

    private record Occupancy(int enter, int leave) {}
}