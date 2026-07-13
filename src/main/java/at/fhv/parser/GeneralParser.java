package at.fhv.parser;

import at.fhv.model.exception.InvalidInstanceException;
import at.fhv.model.jssp.Job;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Machine;
import at.fhv.model.jssp.Operation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class GeneralParser {
    public JsspProblem parse(String filePath) throws IOException {
        try (Scanner scanner = new Scanner(new File(filePath))) {
            int jobCount = readLabelledInt(scanner, "JOBS");
            int machineCount = readLabelledInt(scanner, "MACHINES");

            List<Machine> machines = new ArrayList<>();
            for (int m = 0; m < machineCount; m++) {
                machines.add(new Machine(m));
            }

            List<Job> jobs = new ArrayList<>();
            for (int jobId = 0; jobId < jobCount; jobId++) {
                int opCount = nextInt(scanner, "opCount(job " + jobId + ")");
                List<Operation> operations = new ArrayList<>();
                for (int opIndex = 0; opIndex < opCount; opIndex++) {
                    int machineId = nextInt(scanner, "machineId");
                    int processingTime = nextInt(scanner, "processingTime");
                    int maxDwellTime = nextMaxDwell(scanner, "maxDwellTime");

                    if (machineId < 0 || machineId >= machineCount) {
                        throw new InvalidInstanceException("Job " + jobId + ": machineId " + machineId + " not in 0.." + (machineCount - 1));
                    }
                    if (processingTime < 0) {
                        throw new InvalidInstanceException("Job " + jobId + ": negative processingTime " + processingTime);
                    }
                    operations.add(new Operation(opIndex, jobId, machineId, processingTime, maxDwellTime));
                }
                jobs.add(new Job(jobId, operations));
            }
            return new JsspProblem(machines, jobs);
        }
    }

    private int readLabelledInt(Scanner scanner, String label) {
        String token = nextToken(scanner, label);
        if (token.equalsIgnoreCase(label)) {
            return nextInt(scanner, label);
        }
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            throw new InvalidInstanceException("Expected " + label + " but got '" + token + "'");
        }
    }

    private String nextToken(Scanner scanner, String field) {
        while (scanner.hasNext()) {
            String token = scanner.next();
            if (token.startsWith("#")) {
                if (scanner.hasNextLine()) {
                    scanner.nextLine();
                }
                continue;
            }
            return token;
        }
        throw new InvalidInstanceException("Unexpected end of input while reading " + field);
    }

    private int nextInt(Scanner scanner, String field) {
        String token = nextToken(scanner, field);
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            throw new InvalidInstanceException("Expected integer for " + field + " but got '" + token + "'");
        }
    }

    private int nextMaxDwell(Scanner scanner, String field) {
        String token = nextToken(scanner, field);
        if (token.equalsIgnoreCase("inf")) {
            return Operation.NO_LIMIT;
        }
        try {
            int value = Integer.parseInt(token);
            return value < 0 ? Operation.NO_LIMIT : value;
        } catch (NumberFormatException e) {
            throw new InvalidInstanceException("Expected integer or 'inf' for " + field + " but got '" + token + "'");
        }
    }
}
