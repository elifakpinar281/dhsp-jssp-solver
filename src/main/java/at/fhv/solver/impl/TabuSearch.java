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
        long bestScore;
        int bestMakespan;
        boolean bestFeasible;
        MachineSequences bestFeasibleSequences;
        long bestFeasibleMakespan;
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

        s.best = s.current.copy();
        s.bestScore = calculateScore(s.currentResult);
        s.bestMakespan = s.currentResult.makespan();
        s.bestFeasible = s.currentResult.dwellFeasible();

        s.bestFeasibleSequences = null;
        s.bestFeasibleMakespan = Long.MAX_VALUE;
        if (s.currentResult.dwellFeasible()) {
            s.bestFeasibleSequences = s.current.copy();
            s.bestFeasibleMakespan = s.currentResult.makespan();
        }

        s.tabu = new TabuList();

        int iteration = 0;
        s.noImprovement = 0;
        s.restarts = 0;

        while (s.noImprovement < maxIterationsWithoutImprovement) {
            iteration++;

            List<Move> neighbours = neighbourhood.generate(s.currentResult, s.current);
            if (neighbours.isEmpty()) {
                s.noImprovement++;
                tryRestart(s, problem);
                continue;
            }

            List<Move> bestCandidates = new ArrayList<>();
            ScheduleEvaluator.EvaluationResult bestMoveResult;
            long bestMoveScore = Long.MAX_VALUE;

            List<Move> feasibleMoves = new ArrayList<>();

            for (Move move : neighbours) {
                ScheduleEvaluator.QuickResult quick = scheduleEvaluator.quickEvaluate(s.current.swapped(move));
                if (!quick.feasible()) { continue; }
                feasibleMoves.add(move);

                boolean tabuMove = s.tabu.isTabu(move.getAttribute(), iteration);
                boolean aspiration = quick.makespan() < s.bestScore;

                if (!tabuMove || aspiration) {
                    if (quick.makespan() < bestMoveScore) {
                        bestMoveScore = quick.makespan();
                        bestCandidates.clear();
                        bestCandidates.add(move);
                    } else if (quick.makespan() == bestMoveScore) {
                        bestCandidates.add(move);
                    }
                }
            }

            if (feasibleMoves.isEmpty()) {
                s.noImprovement++;
                tryRestart(s, problem);
                continue;
            }

            Move bestMove = bestCandidates.isEmpty()
                    ? feasibleMoves.get(random.nextInt(feasibleMoves.size()))
                    : bestCandidates.get(random.nextInt(bestCandidates.size()));

            bestMoveResult = scheduleEvaluator.evaluate(s.current.swapped(bestMove));
            bestMoveScore = calculateScore(bestMoveResult);

            s.current = s.current.swapped(bestMove);
            s.currentResult = bestMoveResult;
            s.tabu.setTabu(bestMove.getAttribute(), iteration + tenure);

            if (bestMoveResult.dwellFeasible() && bestMoveResult.makespan() < s.bestFeasibleMakespan) {
                s.bestFeasibleSequences = s.current.copy();
                s.bestFeasibleMakespan = bestMoveResult.makespan();
            }

            if (bestMoveScore < s.bestScore) {
                s.best = s.current.copy();
                s.bestScore = bestMoveScore;
                s.bestMakespan = bestMoveResult.makespan();
                s.bestFeasible = bestMoveResult.dwellFeasible();
                s.noImprovement = 0;
            } else {
                s.noImprovement++;
            }

            history.add(new IterationSnapshot(iteration, bestMoveResult.makespan(), s.bestMakespan));

            if (verbose) {
                System.out.println("Iteration " + iteration + " makespan=" + bestMoveResult.makespan() + " violations=" + bestMoveResult.violationCount() + " bestScore=" + s.bestScore);
            }

            tryRestart(s, problem);
        }

        if (s.bestFeasibleSequences == null) { return null; }

        ScheduleEvaluator.EvaluationResult result = scheduleEvaluator.evaluate(s.bestFeasibleSequences);
        return scheduleEvaluator.toSchedule(result);
    }

    private boolean tryRestart(SearchState s, JsspProblem problem) {
        if (s.noImprovement < restartThreshold || s.restarts >= maxRestarts) { return false; }

        s.restarts++;
        s.current = startDecoder.decode(problem);
        s.currentResult = scheduleEvaluator.evaluate(s.current);
        s.tabu = new TabuList();
        s.noImprovement = 0;

        if (verbose) {
            String reason = s.bestFeasible ? "Diversifikation nach Stagnation" : "noch keine machbare Loesung gefunden";
            System.out.println("Restart " + s.restarts + " (" + reason + ")");
        }

        long score = calculateScore(s.currentResult);
        if (s.currentResult.dwellFeasible() && s.currentResult.makespan() < s.bestFeasibleMakespan) {
            s.bestFeasibleSequences = s.current.copy();
            s.bestFeasibleMakespan = s.currentResult.makespan();
        }
        if (score < s.bestScore) {
            s.best = s.current.copy();
            s.bestScore = score;
            s.bestMakespan = s.currentResult.makespan();
            s.bestFeasible = s.currentResult.dwellFeasible();
        }

        return true;
    }

    private long calculateScore(ScheduleEvaluator.EvaluationResult result) {
        long penalty = result.violationPenalty();
        return penalty * 100000L + result.makespan();
    }
}