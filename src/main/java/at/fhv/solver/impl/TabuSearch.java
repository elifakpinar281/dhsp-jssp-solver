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

    @Override
    public Schedule solve(JsspProblem problem) {
        history.clear();

        MachineSequences current = startDecoder.decode(problem);
        ScheduleEvaluator.EvaluationResult currentResult = scheduleEvaluator.evaluate(current);

        MachineSequences best = current.copy();
        long bestScore = calculateScore(currentResult);
        int bestMakespan = currentResult.makespan();
        boolean bestFeasible = currentResult.dwellFeasible();

        MachineSequences bestFeasibleSequences = null;
        long bestFeasibleMakespan = Long.MAX_VALUE;
        if (currentResult.dwellFeasible()) {
            bestFeasibleSequences = current.copy();
            bestFeasibleMakespan = currentResult.makespan();
        }

        TabuList tabu = new TabuList();

        int iteration = 0;
        int noImprovement = 0;
        int restarts = 0;

        while (noImprovement < maxIterationsWithoutImprovement) {
            iteration++;

            List<Move> neighbours = neighbourhood.generate(currentResult, current);
            if (neighbours.isEmpty()) { break; }

            Move bestMove = null;
            ScheduleEvaluator.EvaluationResult bestMoveResult;
            long bestMoveScore = Long.MAX_VALUE;

            List<Move> feasibleMoves = new ArrayList<>();

            for (Move move : neighbours) {
                ScheduleEvaluator.QuickResult quick = scheduleEvaluator.quickEvaluate(current.swapped(move));
                if (!quick.feasible()) { continue; }
                feasibleMoves.add(move);

                boolean tabuMove = tabu.isTabu(move.getAttribute(), iteration);
                boolean aspiration = quick.makespan() < bestScore;

                if ((!tabuMove || aspiration) && quick.makespan() < bestMoveScore) {
                    bestMove = move;
                    bestMoveScore = quick.makespan();
                }
            }

            if (feasibleMoves.isEmpty()) { noImprovement++; continue; }

            if (bestMove == null) {
                bestMove = feasibleMoves.get(random.nextInt(feasibleMoves.size()));
            }

            bestMoveResult = scheduleEvaluator.evaluate(current.swapped(bestMove));
            bestMoveScore = calculateScore(bestMoveResult);

            current = current.swapped(bestMove);
            currentResult = bestMoveResult;
            tabu.setTabu(bestMove.getAttribute(), iteration + tenure);

            if (bestMoveResult.dwellFeasible() && bestMoveResult.makespan() < bestFeasibleMakespan) {
                bestFeasibleSequences = current.copy();
                bestFeasibleMakespan = bestMoveResult.makespan();
            }

            if (bestMoveScore < bestScore) {
                best = current.copy();
                bestScore = bestMoveScore;
                bestMakespan = bestMoveResult.makespan();
                bestFeasible = bestMoveResult.dwellFeasible();
                noImprovement = 0;
            } else {
                noImprovement++;
            }

            history.add(new IterationSnapshot(iteration, bestMoveResult.makespan(), bestMakespan));

            if (verbose) {
                System.out.println("Iteration " + iteration + " makespan=" + bestMoveResult.makespan() + " violations=" + bestMoveResult.violationCount() + " bestScore=" + bestScore);
            }

            if (!bestFeasible && noImprovement >= restartThreshold && restarts < maxRestarts) {
                restarts++;
                current = startDecoder.decode(problem);
                currentResult = scheduleEvaluator.evaluate(current);
                tabu = new TabuList();
                noImprovement = 0;

                if (verbose) {
                    System.out.println("Restart " + restarts + " (noch keine machbare Loesung gefunden)");
                }

                long score = calculateScore(currentResult);
                if (currentResult.dwellFeasible() && currentResult.makespan() < bestFeasibleMakespan) {
                    bestFeasibleSequences = current.copy();
                    bestFeasibleMakespan = currentResult.makespan();
                }
                if (score < bestScore) {
                    best = current.copy();
                    bestScore = score;
                    bestMakespan = currentResult.makespan();
                    bestFeasible = currentResult.dwellFeasible();
                }
            }
        }

        if (bestFeasibleSequences == null) { return null; }

        ScheduleEvaluator.EvaluationResult result = scheduleEvaluator.evaluate(bestFeasibleSequences);
        return scheduleEvaluator.toSchedule(result);
    }

    private long calculateScore(ScheduleEvaluator.EvaluationResult result) {
        long penalty = result.violationPenalty();
        return penalty * 100000L + result.makespan();
    }
}