package at.fhv.evaluation.implementation.fjssp;

import at.fhv.model.fjssp.FjsspJob;
import at.fhv.model.fjssp.FjsspOperation;
import at.fhv.model.fjssp.FjsspProblem;

import java.util.Arrays;
import java.util.List;

public class FjsspSlack {
    public static final int NO_BINDING = Integer.MAX_VALUE;
    private final FjsspProblem problem;
    private final int[][] temp; // cache e.g. temp[2][3] = 15 -> for job 2, operation 3 has a calculated slack of 15

    public FjsspSlack(FjsspProblem problem) {
        // creates the cache
        this.problem = problem;
        this.temp = new int[problem.getJobs().size()][];
        for (FjsspJob job : problem.getJobs()) {
            int length = job.operations().size();
            temp[job.jobId()] = new int[length];
            Arrays.fill(temp[job.jobId()], -1);
        }
    }

    public int slackOf(int jobId, int i) {
        List<FjsspOperation> operations = problem.getJobs().get(jobId).operations();
        // Get all operations of the job. If the operation does not exist, return Integer.MAX_VALUE
        if (i >= operations.size()) { return NO_BINDING; }
        if (temp[jobId][i] != -1) { return temp[jobId][i]; } // return slack if already calculated

        int result = calculateSlack(jobId, i); // calculate and then store to cache
        temp[jobId][i] = result;
        return result;
    }

    private int calculateSlack(int jobId, int index) { // for whole chain - smallest is the most restrictive
        List<FjsspOperation> chain = problem.dwellChain(jobId, index);

        if (chain.size() <= 1) { return NO_BINDING; }
        int minSlack = NO_BINDING;

        for (int i = 0; i <= chain.size() - 2; i++) {
            FjsspOperation operation = chain.get(i);

            if (operation.hasDwellLimit() == false) { continue; }
            // Slack = maximum allowed dwell time - minimum processing time
            int temp = operation.maxDwellTime() - operation.minimalProcessingTime();

            if (temp < 0) { return 0; }
            minSlack = Math.min(minSlack, temp);
        }
        return minSlack;
    }
}
