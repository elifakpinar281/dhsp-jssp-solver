package at.fhv;

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

public class Parser {
    public JsspProblem parse(String filePath) throws IOException {
        try (Scanner scanner = new Scanner (new File(filePath))) {

            int jobCount = nextInt(scanner, "jobCount");
            int machineCount = nextInt(scanner, "machineCount");

            List<Machine> machines = new ArrayList<>();

            for (int mcount = 0; mcount < machineCount; mcount++) {
                machines.add(new Machine(mcount));
            }

            List<Job> jobs = new ArrayList<>();

            for (int jobId = 0; jobId < jobCount; jobId++) {
                List<Operation> operations = new ArrayList<>();

                for (int operationId = 0; operationId < machineCount; operationId++) {
                    int machineId = nextInt(scanner, "machineId");
                    int processingTime = nextInt(scanner, "processingTime");

                    if (machineId < 0 || machineId >= machineCount) {
                        throw new InvalidInstanceException("Job " + jobId + ": machineId " + machineId + " not in 0.." + (machineCount - 1));
                    }
                    if (processingTime < 0) {
                        throw new InvalidInstanceException("Job " + jobId + ": negative processingTime " + processingTime);
                    }

                    operations.add(new Operation(operationId, jobId, machineId, processingTime));
                }
                jobs.add(new Job(jobId, operations));
            }
            return new JsspProblem(machines, jobs);
        }
    }

    private int nextInt(Scanner scanner, String field) {
        if (!scanner.hasNextInt()) {
            throw new InvalidInstanceException("Expected integer for " + field);
        }
        return scanner.nextInt();
    }
}
