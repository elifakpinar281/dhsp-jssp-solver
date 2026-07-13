package at.fhv;

import at.fhv.model.exception.InvalidInstanceException;
import at.fhv.model.jssp.Job;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Machine;
import at.fhv.model.jssp.Operation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

        List<Machine> machines = buildMachines(machineCount);
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

        List<Machine> machines = buildMachines(machineCount);
        List<Job> jobs = new ArrayList<>();

        for (int jobId = 0; jobId < jobCount; jobId++) {
            int opCount = nextInt(scanner, "opCount");
            List<Operation> operations = new ArrayList<>();

            for (int operationId = 0; operationId < opCount; operationId++) {
                int machineId = nextInt(scanner, "machineId");
                int processingTime = nextInt(scanner, "processingTime");
                validateOperation(jobId, machineId, processingTime, machineCount);
                operations.add(new Operation(operationId, jobId, machineId, processingTime));
            }
            jobs.add(new Job(jobId, operations));
        }
        return new JsspProblem(machines, jobs);
    }

    private List<Machine> buildMachines(int machineCount) {
        List<Machine> machines = new ArrayList<>();
        for (int mcount = 0; mcount < machineCount; mcount++) {
            machines.add(new Machine(mcount));
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
}