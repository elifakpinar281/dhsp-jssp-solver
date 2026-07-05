package at.fhv;

import at.fhv.model.jssp.Job;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Machine;
import at.fhv.model.jssp.Operation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

// count as ids
public class Parser {
    public JsspProblem parse(String filePath) throws IOException {
        Scanner scanner = new Scanner (new File(filePath));

        int jobCount = scanner.nextInt();
        int machineCount = scanner.nextInt();

        List<Machine> machines = new ArrayList<>();

        for (int mcount = 0; mcount < machineCount; mcount++) {
            machines.add(new Machine(mcount));
        }

        List<Job> jobs = new ArrayList<>();

        for (int jobId = 0; jobId < jobCount; jobId++) {
           List<Operation> operations = new ArrayList<>();

           for (int operationId = 0; operationId < machineCount; operationId++) {
               int machineId = scanner.nextInt();
               int processingTime = scanner.nextInt();

               operations.add(new Operation(operationId, jobId, machineId, processingTime));
           }
           jobs.add(new Job(jobId, operations));
        }

        scanner.close();
        return new JsspProblem(machines, jobs);
    }

}
