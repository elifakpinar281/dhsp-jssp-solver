package at.fhv.solver.validation;


import at.fhv.model.fjssp.FjsspJob;
import at.fhv.model.fjssp.FjsspOperation;
import at.fhv.model.fjssp.FjsspProblem;
import at.fhv.model.jssp.Schedule;
import at.fhv.model.jssp.ScheduledOperation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FjsspValidator {
    public ValidationResult validateSchedule(FjsspProblem problem, Schedule schedule) {
        List<String> violations = new ArrayList<>();
        if (schedule == null || schedule.operations() == null) {
            violations.add("Schedule or operations is null");
            return new ValidationResult(false, violations);
        }
        List<ScheduledOperation> scheduledOperations = schedule.operations();

        checkOperationCount(problem, scheduledOperations, violations);
        checkJobRouting(problem, scheduledOperations, violations);
        checkBathOverlap(problem, scheduledOperations, violations);
        checkTimeConsistency(scheduledOperations, violations);
        checkMaxDwell(problem, scheduledOperations, violations);

        return new ValidationResult(violations.isEmpty(), violations);
    }

    private void checkOperationCount(FjsspProblem problem, List<ScheduledOperation> scheduledOperations, List<String> violations) {
        int expected = 0;
        for (FjsspJob job : problem.getJobs()) {
            expected = expected + job.operations().size();
        }
        if (scheduledOperations.size() != expected) {
            violations.add("Mismatch, expected: " + expected + ", scheduled: " + scheduledOperations.size());
        }
    }

    private void checkJobRouting(FjsspProblem problem, List<ScheduledOperation> scheduledOperations, List<String> violations) {
        for (FjsspJob job : problem.getJobs()) {
            int jobId = job.jobId();
            List<ScheduledOperation> jobOperations = operationsOf(scheduledOperations, jobId);
            jobOperations.sort(Comparator.comparingInt(ScheduledOperation::startTime));
            List<FjsspOperation> required = job.operations();

            if (jobOperations.size() != required.size()) {
                violations.add("For job " + jobId + "- expected: " + required.size() + ", found: " + jobOperations.size());
                continue;
            }

            int previous = 0;
            for (int i = 0; i < required.size(); i++) {
                ScheduledOperation scheduled = jobOperations.get(i);
                FjsspOperation operation = required.get(i);
                int assignedBath = scheduled.machineId();

                if (!operation.eligibleMachines().contains(assignedBath)) {
                    violations.add("Job " + jobId + " step " + i + " runs on bath " + assignedBath + " but that is not an eligible machine");
                    continue;
                }

                int duration = scheduled.endTime() - scheduled.startTime();
                int expectedDuration = operation.processingOnOneMachine(assignedBath);
                if (duration != expectedDuration) {
                    violations.add("Job " + jobId + " step " + i + "- duration " + duration + ", processing time on bath " + assignedBath + " should be " + expectedDuration);
                }

                if (scheduled.startTime() < previous) {
                    violations.add("Job " + jobId + " step " + i + " starts at " + scheduled.startTime() + " before previous step ended at " + previous);
                }
                previous = scheduled.endTime();
            }
        }
    }

    private void checkBathOverlap(FjsspProblem problem, List<ScheduledOperation> scheduledOperations, List<String> violations) {
        Map<Integer, List<Occupancy>> planned = new HashMap<>();

        for (FjsspJob job : problem.getJobs()) {
            List<ScheduledOperation> jobOperations = operationsOf(scheduledOperations, job.jobId());
            jobOperations.sort(Comparator.comparingInt(ScheduledOperation::startTime));

            for (int i = 0; i < jobOperations.size(); i++) {
                ScheduledOperation current = jobOperations.get(i);
                int leaveTime = (problem.isBlocking() && i + 1 < jobOperations.size())
                        ? jobOperations.get(i + 1).startTime()
                        : current.endTime();
                planned.computeIfAbsent(current.machineId(), key -> new ArrayList<>()).add(new Occupancy(current.startTime(), leaveTime));
            }
        }

        for (Map.Entry<Integer, List<Occupancy>> entry : planned.entrySet()) {
            int bathId = entry.getKey();
            List<Occupancy> occupancies = entry.getValue();

            for (Occupancy reference : occupancies) {
                int time = reference.enter();
                int concurrent = 0;
                for (Occupancy other : occupancies) {
                    if (other.enter() <= time && time < other.leave()) { concurrent++; }
                }
                if (concurrent > 1) {
                    violations.add("Bath " + bathId + " holds " + concurrent + " carriers at time " + time + " but capacity is 1");
                    break;
                }
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

    private void checkMaxDwell(FjsspProblem problem, List<ScheduledOperation> scheduledOperations, List<String> violations) {
        for (FjsspJob job : problem.getJobs()) {
            int jobId = job.jobId();
            List<ScheduledOperation> jobOperations = operationsOf(scheduledOperations, jobId);
            jobOperations.sort(Comparator.comparingInt(ScheduledOperation::startTime));
            List<FjsspOperation> required = job.operations();

            if (jobOperations.size() != required.size()) { continue; }

            for (int i = 0; i < required.size(); i++) {
                FjsspOperation operation = required.get(i);
                int assignedBath = jobOperations.get(i).machineId();
                int processingTime = operation.eligibleMachines().contains(assignedBath) ? operation.processingOnOneMachine(assignedBath) : operation.minimalProcessingTime();

                int dwell = (i + 1 < required.size())
                        ? jobOperations.get(i + 1).startTime() - jobOperations.get(i).startTime()
                        : jobOperations.get(i).endTime() - jobOperations.get(i).startTime();

                if (dwell < processingTime) {
                    violations.add("Job " + jobId + " step " + i + " (bath " + assignedBath + ") dwell " + dwell + " is below min dwell time " + processingTime);
                }
                if (operation.hasDwellLimit() && dwell > operation.maxDwellTime()) {
                    violations.add("Job " + jobId + " step " + i + " (bath " + assignedBath + ") dwell " + dwell + " exceeds max dwell time " + operation.maxDwellTime());
                }
            }
        }
    }

    private List<ScheduledOperation> operationsOf(List<ScheduledOperation> scheduledOperations, int jobId) {
        List<ScheduledOperation> result = new ArrayList<>();
        for (ScheduledOperation scheduledOperation : scheduledOperations) {
            if (scheduledOperation.jobId() == jobId) { result.add(scheduledOperation); }
        }
        return result;
    }

    private record Occupancy(int enter, int leave) {}
}
