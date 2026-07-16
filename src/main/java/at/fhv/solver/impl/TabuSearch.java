package at.fhv.solver.impl;

import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Schedule;
import at.fhv.solver.ISearchAlgorithm;
import at.fhv.solver.IStartDecoder;
import at.fhv.solver.impl.tabu.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TabuSearch implements ISearchAlgorithm {
    private static final int DEFAULT_MAX_RESTARTS = 5;

    public record IterationSnapshot(
            int iteration,
            int makespan,
            int bestMakespan
    ) {}

    private final ScheduleEvaluator scheduleEvaluator;
    private final INeighbourhood neighbourhood;
    private final int tenure;
    private final int maxIterationsWithoutImprovement;
    private final IStartDecoder startDecoder;
    private final Random random;
    private final int maxRestarts;
    private final int restartThreshold;

    private boolean verbose = true;
    private final List<IterationSnapshot> history = new ArrayList<>();

    public TabuSearch(ScheduleEvaluator scheduleEvaluator, INeighbourhood neighbourhood, int tenure, int maxIterationsWithoutImprovement, IStartDecoder startDecoder) {
        this(scheduleEvaluator, neighbourhood, tenure, maxIterationsWithoutImprovement, startDecoder, 42L);
    }

    public TabuSearch(ScheduleEvaluator scheduleEvaluator, INeighbourhood neighbourhood, int tenure, int maxIterationsWithoutImprovement, IStartDecoder startDecoder, long seed) {
        this(scheduleEvaluator, neighbourhood, tenure, maxIterationsWithoutImprovement, startDecoder, seed, DEFAULT_MAX_RESTARTS);
    }

    public TabuSearch(ScheduleEvaluator scheduleEvaluator, INeighbourhood neighbourhood, int tenure, int maxIterationsWithoutImprovement, IStartDecoder startDecoder, long seed, int maxRestarts) {
        this.scheduleEvaluator = scheduleEvaluator;
        this.neighbourhood = neighbourhood;
        this.tenure = tenure;
        this.maxIterationsWithoutImprovement = maxIterationsWithoutImprovement;
        this.startDecoder = startDecoder;
        this.random = new Random(seed);
        this.maxRestarts = maxRestarts;
        this.restartThreshold = Math.max(1, maxIterationsWithoutImprovement / 4);
    }

    public TabuSearch withVerbose(boolean verbose) {
        this.verbose = verbose;
        return this;
    }

    public List<IterationSnapshot> getHistory() {
        return history;
    }

    private static final class SearchState {
        MachineSequences current;
        ScheduleEvaluator.EvaluationResult currentResult;
        MachineSequences best;
        int bestMakespan;
        TabuList tabu;
        int noImprovement;
        int restarts;
    }

    @Override
    public Schedule solve(JsspProblem problem) {
        history.clear();

        SearchState s = new SearchState();
        s.current = startDecoder.decode(problem);
        s.currentResult = scheduleEvaluator.evaluate(s.current);

        s.best = null;
        s.bestMakespan = Integer.MAX_VALUE;
        s.tabu = new TabuList();
        s.noImprovement = 0;
        s.restarts = 0;

        remember(s);

        int iteration = 0;

        while (s.noImprovement < maxIterationsWithoutImprovement) {
            iteration++;

            List<Move> neighbours = neighbourhood.generate(s.currentResult, s.current);
            if (neighbours.isEmpty()) {
                s.noImprovement++;
                tryRestart(s, problem);
                continue;
            }

            List<Move> feasibleMoves = new ArrayList<>();
            List<Move> bestCandidates = new ArrayList<>();
            int bestCandidateMakespan = Integer.MAX_VALUE;

            for (Move move : neighbours) {
                ScheduleEvaluator.Result result = scheduleEvaluator.evaluateResult(s.current.applied(move));
                if (!result.valid()) { continue; }
                feasibleMoves.add(move);

                boolean tabuMove = s.tabu.isTabu(move.getAttribute(), iteration);
                boolean aspiration = result.makespan() < s.bestMakespan;

                if (tabuMove && !aspiration) { continue; }

                if (result.makespan() < bestCandidateMakespan) {
                    bestCandidateMakespan = result.makespan();
                    bestCandidates.clear();
                    bestCandidates.add(move);
                } else if (result.makespan() == bestCandidateMakespan) {
                    bestCandidates.add(move);
                }
            }

            if (feasibleMoves.isEmpty()) {
                s.noImprovement++;
                tryRestart(s, problem);
                continue;
            }

            Move chosenMove = bestCandidates.isEmpty() ? feasibleMoves.get(random.nextInt(feasibleMoves.size())) : bestCandidates.get(random.nextInt(bestCandidates.size()));

            s.current = s.current.applied(chosenMove);
            s.currentResult = scheduleEvaluator.evaluate(s.current);
            s.tabu.setTabu(chosenMove.getAttribute(), iteration + tenure);

            if (remember(s)) {
                s.noImprovement = 0;
            } else {
                s.noImprovement++;
            }

            history.add(new IterationSnapshot(iteration, s.currentResult.makespan(), s.bestMakespan));

            if (verbose) {
                System.out.println("Iteration " + iteration + " makespan=" + s.currentResult.makespan() + " bestMakespan=" + s.bestMakespan);
            }

            tryRestart(s, problem);
        }

        if (s.best == null) { return null; }

        ScheduleEvaluator.EvaluationResult result = scheduleEvaluator.evaluate(s.best);
        return scheduleEvaluator.toSchedule(result);
    }

    private boolean remember(SearchState s) {
        if (!s.currentResult.dwellValid()) { return false; }
        if (s.currentResult.makespan() >= s.bestMakespan) { return false; }

        s.best = s.current.copy();
        s.bestMakespan = s.currentResult.makespan();
        return true;
    }

    private boolean tryRestart(SearchState s, JsspProblem problem) {
        if (s.noImprovement < restartThreshold || s.restarts >= maxRestarts) { return false; }

        s.restarts++;
        s.current = startDecoder.decode(problem);
        s.currentResult = scheduleEvaluator.evaluate(s.current);
        s.tabu = new TabuList();
        s.noImprovement = 0;

        if (verbose) {
            String reason = s.best != null ? "Diversification after stagnation" : "no valid solution found yet";
            System.out.println("Restart " + s.restarts + " (" + reason + ")");
        }

        remember(s);
        return true;
    }
}