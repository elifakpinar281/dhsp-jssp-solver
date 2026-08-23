package at.fhv.evaluation;

import at.fhv.model.jssp.Job;
import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Operation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// zeitliche Spielraum, den eine Operation oder Kette hat, bevor eine zeitliche Grenze (z. B. Max-Dwell-Time) verletzt wird
public class Slack {
    public static final int NO_BINDING = Integer.MAX_VALUE;

    private JsspProblem jsspProblem;
    private int[][] cache;

    public Slack(JsspProblem jsspProblem) {
        this.jsspProblem = jsspProblem;
        this.cache = new int[jsspProblem.getJobs().size()][];
        for (Job job : jsspProblem.getJobs()) {
            int length = jsspProblem.getJobs().get(job.jobId()).operations().size();
            cache[job.jobId()] = new int[length];
            Arrays.fill(cache[job.jobId()], -1); // -1 = noch nicht berechnet
        }
    }

    public int slackOf(int jobId, int i) {
        List<Operation> operations = jsspProblem.getJobs().get(jobId).operations();

        if (i >= operations.size()) { return NO_BINDING; }
        if (cache[jobId][i] != -1) { return cache[jobId][i]; }

        int result = computeSlack(jobId, i);
        cache[jobId][i] = result;
        return result;
    }

    private int computeSlack(int jobId, int index) {
        List<Operation> chain = jsspProblem.dwellChain(jobId, index);

        if (chain.size() <= 1) {return NO_BINDING; }
        int minSlack = NO_BINDING;

        for (int i = 0; i <= chain.size() - 2; i++) {
            Operation operation = chain.get(i);

            if (operation.hasDwellLimit() == false) { continue; }
            int temp = operation.maxDwellTime() - operation.processingTime();

            if (temp < 0) { return 0; }
            minSlack = Math.min(minSlack, temp);
        }
        return minSlack;
    }
}
