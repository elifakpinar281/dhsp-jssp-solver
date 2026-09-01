package at.fhv;

import at.fhv.model.exception.InvalidInstanceException;

import at.fhv.model.fjssp.FjsspJob;
import at.fhv.model.fjssp.FjsspOperation;
import at.fhv.model.fjssp.FjsspProblem;
import at.fhv.model.fjssp.MachineOption;
import at.fhv.model.jssp.Job;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Machine;
import at.fhv.model.jssp.Operation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Parser {
    public JsspProblem parse(String filePath) throws IOException {
        String content = stripComments(filePath);
        try (Scanner scanner = new Scanner(content)) {
            if (scanner.hasNext("(?i)JOBS")) {
                return parseKeywordFormat(scanner);
            }
            return parseClassicFormat(scanner);
        }
    }

    public FjsspProblem parseFjssp(String filePath) throws IOException {
        String content = stripComments(filePath);
        try (Scanner scanner = new Scanner(content)) {
            if (!scanner.hasNext("(?i)JOBS")) {
                throw new InvalidInstanceException("FJSSP mode requires the keyword instance format (JOBS/MACHINES/CAPACITIES)");
            }
            return parseKeywordFormatFjssp(scanner);
        }
    }

    private FjsspProblem parseKeywordFormatFjssp(Scanner scanner) {
        expectKeyword(scanner, "JOBS");
        int jobCount = nextInt(scanner, "jobCount");
        expectKeyword(scanner, "MACHINES");
        int machineCount = nextInt(scanner, "machineCount");

        int[] capacities = parseCapacities(scanner, machineCount);
        boolean blocking = parseBlocking(scanner);

        int[] offsets = new int[machineCount];
        int offset = 0;
        for (int station = 0; station < machineCount; station++) {
            offsets[station] = offset;
            offset += capacities[station];
        }

        List<FjsspJob> jobs = new ArrayList<>();

        for (int jobId = 0; jobId < jobCount; jobId++) {
            int opCount = nextInt(scanner, "opCount");
            List<FjsspOperation> operations = new ArrayList<>();

            for (int operationId = 0; operationId < opCount; operationId++) {
                int stationId = nextInt(scanner, "machineId");
                int fullTime = nextInt(scanner, "processingTime");
                int maxDwellTime = nextMaxDwell(scanner, "maxDwellTime");
                validateOperation(jobId, stationId, fullTime, machineCount);
                validateDwell(jobId, operationId, fullTime, maxDwellTime);

                List<MachineOption> options = new ArrayList<>();
                for (int bath = offsets[stationId]; bath < offsets[stationId] + capacities[stationId]; bath++) {
                    options.add(new MachineOption(bath, fullTime));
                }
                operations.add(new FjsspOperation(operationId, jobId, stationId, options, maxDwellTime));
            }
            jobs.add(new FjsspJob(jobId, operations));
        }
        return new FjsspProblem(jobs, capacities, blocking);
    }

    private String stripComments(String filePath) throws IOException {
        List<String> lines = Files.readAllLines(Path.of(filePath), StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            sb.append(trimmed).append(' ');
        }
        return sb.toString();
    }

    private JsspProblem parseClassicFormat(Scanner scanner) {
        int jobCount = nextInt(scanner, "jobCount");
        int machineCount = nextInt(scanner, "machineCount");

        List<Machine> machines = buildMachines(machineCount, defaultCapacities(machineCount));
        List<Job> jobs = new ArrayList<>();

        for (int jobId = 0; jobId < jobCount; jobId++) {
            List<Operation> operations = new ArrayList<>();

            for (int operationId = 0; operationId < machineCount; operationId++) {
                int machineId = nextInt(scanner, "machineId");
                int processingTime = nextInt(scanner, "processingTime");
                validateOperation(jobId, machineId, processingTime, machineCount);
                operations.add(new Operation(operationId, jobId, machineId, processingTime));
            }
            jobs.add(new Job(jobId, operations));
        }
        return new JsspProblem(machines, jobs);
    }

    private JsspProblem parseKeywordFormat(Scanner scanner) {
        expectKeyword(scanner, "JOBS");
        int jobCount = nextInt(scanner, "jobCount");
        expectKeyword(scanner, "MACHINES");
        int machineCount = nextInt(scanner, "machineCount");

        int[] baths = parseCapacities(scanner, machineCount);
        boolean blocking = parseBlocking(scanner);

        // nur zum Dividieren
        int[] capacities = new int[machineCount];
        Arrays.fill(capacities, 1);

        List<Machine> machines = buildMachines(machineCount, capacities);
        List<Job> jobs = new ArrayList<>();

        for (int jobId = 0; jobId < jobCount; jobId++) {
            int opCount = nextInt(scanner, "opCount");
            List<Operation> operations = new ArrayList<>();

            for (int operationId = 0; operationId < opCount; operationId++) {
                int machineId = nextInt(scanner, "machineId");
                int nominalTime = nextInt(scanner, "processingTime");
                int maxDwellTime = nextMaxDwell(scanner, "maxDwellTime");
                validateOperation(jobId, machineId, nominalTime, machineCount);

                int processingTime = divideByBaths(nominalTime, baths[machineId]);

                validateDwell(jobId, operationId, processingTime, maxDwellTime);
                operations.add(new Operation(operationId, jobId, machineId, processingTime, maxDwellTime));
            }
            jobs.add(new Job(jobId, operations));
        }
        return new JsspProblem(machines, jobs, blocking);
    }

    // z.B. 3600 / 6 = 600, 2500 / 3 = 833
    private int divideByBaths(int processingTime, int bathCount) {
        if (bathCount <= 1) {
            return processingTime;
        }
        return (int) Math.round((double) processingTime / bathCount);
    }

    private boolean parseBlocking(Scanner scanner) {
        if (!scanner.hasNext("(?i)BLOCKING")) {
            return false;
        }
        scanner.next();
        if (!scanner.hasNext()) {
            throw new InvalidInstanceException("Expected true or false after BLOCKING");
        }
        String token = scanner.next();
        if (token.equalsIgnoreCase("true")) { return true; }
        if (token.equalsIgnoreCase("false")) { return false; }
        throw new InvalidInstanceException("Expected true or false after BLOCKING but got '" + token + "'");
    }

    private int[] parseCapacities(Scanner scanner, int machineCount) {
        int[] capacities = defaultCapacities(machineCount);
        if (!scanner.hasNext("(?i)CAPACITIES")) {
            return capacities;
        }

        scanner.next();
        for (int i = 0; i < machineCount; i++) {
            int capacity = nextInt(scanner, "capacity for machine " + i);
            if (capacity < 1) {
                throw new InvalidInstanceException("Machine " + i + ": capacity must be >= 1 but was " + capacity);
            }
            capacities[i] = capacity;
        }
        return capacities;
    }

    private int[] defaultCapacities(int machineCount) {
        int[] capacities = new int[machineCount];
        Arrays.fill(capacities, 1);
        return capacities;
    }

    private List<Machine> buildMachines(int machineCount, int[] capacities) {
        List<Machine> machines = new ArrayList<>();
        for (int machineId = 0; machineId < machineCount; machineId++) {
            machines.add(new Machine(machineId, capacities[machineId]));
        }
        return machines;
    }

    private void validateOperation(int jobId, int machineId, int processingTime, int machineCount) {
        if (machineId < 0 || machineId >= machineCount) {
            throw new InvalidInstanceException("Job " + jobId + ": machineId " + machineId + " not in 0.." + (machineCount - 1));
        }
        if (processingTime < 0) {
            throw new InvalidInstanceException("Job " + jobId + ": negative processingTime " + processingTime);
        }
    }

    private void validateDwell(int jobId, int operationId, int processingTime, int maxDwellTime) {
        if (maxDwellTime != Operation.NO_LIMIT && maxDwellTime < processingTime) {
            throw new InvalidInstanceException("Job " + jobId + " step " + operationId + ": maxDwellTime " + maxDwellTime + " is smaller than processingTime " + processingTime + " (max sec must include min sec)");
        }
    }

    private void expectKeyword(Scanner scanner, String keyword) {
        if (!scanner.hasNext("(?i)" + keyword)) {
            throw new InvalidInstanceException("Expected keyword " + keyword);
        }
        scanner.next();
    }

    private int nextInt(Scanner scanner, String field) {
        if (!scanner.hasNextInt()) {
            throw new InvalidInstanceException("Expected integer for " + field);
        }
        return scanner.nextInt();
    }

    private int nextMaxDwell(Scanner scanner, String field) {
        if (!scanner.hasNext()) {
            throw new InvalidInstanceException("Expected value for " + field);
        }
        String token = scanner.next();
        if (token.equalsIgnoreCase("inf")) {
            return Operation.NO_LIMIT;
        }
        try {
            int value = Integer.parseInt(token);
            if (value < 0) {
                throw new InvalidInstanceException("Expected a non negative integer or 'inf' for " + field + " but got '" + token + "'");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new InvalidInstanceException("Expected integer or 'inf' for " + field + " but got '" + token + "'");
        }
    }
}