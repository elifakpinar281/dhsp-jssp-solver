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
    private boolean verbose = true;
    private final List<IterationSnapshot> history = new ArrayList<>();


    public TabuSearch(ScheduleEvaluator scheduleEvaluator, INeighbourhood neighbourhood, int tenure, int maxIterationsWithoutImprovement, IStartDecoder startDecoder) {
        this(scheduleEvaluator, neighbourhood, tenure, maxIterationsWithoutImprovement, startDecoder, 42L);
    }


    public TabuSearch(ScheduleEvaluator scheduleEvaluator, INeighbourhood neighbourhood, int tenure, int maxIterationsWithoutImprovement, IStartDecoder startDecoder, long seed) {
        this.scheduleEvaluator = scheduleEvaluator;
        this.neighbourhood = neighbourhood;
        this.tenure = tenure;
        this.maxIterationsWithoutImprovement = maxIterationsWithoutImprovement;
        this.startDecoder = startDecoder;
        this.random = new Random(seed);
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
        int bestScore = calculateScore(currentResult);
        int bestMakespan = currentResult.makespan();

        TabuList tabu = new TabuList();

        int iteration = 0;
        int noImprovement = 0;

        while(noImprovement < maxIterationsWithoutImprovement) {
            iteration++;
            currentResult = scheduleEvaluator.evaluate(current);
            List<Move> neighbours = neighbourhood.generate(currentResult, current);

            if(neighbours.isEmpty()) { break; }

            Move bestMove = null;
            ScheduleEvaluator.EvaluationResult bestMoveResult = null;
            int bestMoveScore = Integer.MAX_VALUE;

            for (Move move : neighbours) {
                MachineSequences candidate = current.swapped(move);
                ScheduleEvaluator.EvaluationResult result = scheduleEvaluator.evaluate(candidate);
                int score = calculateScore(result);
                boolean tabuMove = tabu.isTabu(move.getAttribute(), iteration);
                boolean aspiration = score < bestScore;

                if((!tabuMove || aspiration) && score < bestMoveScore) {
                    bestMove = move;
                    bestMoveResult = result;
                    bestMoveScore = score;
                }
            }

            if(bestMove == null) {
                bestMove = neighbours.get(random.nextInt(neighbours.size()));
                bestMoveResult = scheduleEvaluator.evaluate(current.swapped(bestMove));
                bestMoveScore = calculateScore(bestMoveResult);
            }

            current = current.swapped(bestMove);
            tabu.setTabu(bestMove.getAttribute(), iteration + tenure);

            if (bestMoveScore < bestScore) {
                best = current.copy();
                bestScore = bestMoveScore;
                bestMakespan = bestMoveResult.makespan();
                noImprovement = 0;
            } else {
                noImprovement++;
            }

            history.add(new IterationSnapshot(iteration, bestMoveResult.makespan(), bestMakespan));

            if(verbose) {
                System.out.println("Iteration " + iteration + " makespan=" + bestMoveResult.makespan() + " violations=" + bestMoveResult.violationCount() + " bestScore=" + bestScore);
            }

        }

        ScheduleEvaluator.EvaluationResult result = scheduleEvaluator.evaluate(best);
        return scheduleEvaluator.toSchedule(result);
    }

    private int calculateScore(ScheduleEvaluator.EvaluationResult result) {
        long penalty = 0;
        for (ScheduleEvaluator.DwellViolation violation : result.dwellViolations()) {
            penalty += violation.excess() * 100000L;
        }

        long score = penalty * 100000L + result.makespan();
        return score > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) score;
    }
}