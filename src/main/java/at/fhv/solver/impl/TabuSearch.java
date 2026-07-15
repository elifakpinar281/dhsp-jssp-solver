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
    public record IterationSnapshot(int iteration, int makespan, int bestMakespan) {}

    private ScheduleEvaluator scheduleEvaluator;
    private INeighbourhood neighbourhood;
    private int tenure;
    private int maxIterationsWithoutImprovement;
    private IStartDecoder startDecoder;
    private final Random random;
    private boolean verbose = true;
    private final List<IterationSnapshot> history = new ArrayList<>();

    public TabuSearch(ScheduleEvaluator scheduleEvaluator, INeighbourhood neighbourhood, int tenure, int maxIterationsWithoutImprovement, IStartDecoder startDecoder) {
        this(scheduleEvaluator, neighbourhood, tenure, maxIterationsWithoutImprovement, startDecoder, 42L);
    }

    public TabuSearch(ScheduleEvaluator scheduleEvaluator, INeighbourhood neighbourhood, int tenure, int maxIterationsWithoutImprovement, IStartDecoder startDecoder, long tieBreakSeed) {
        this.scheduleEvaluator = scheduleEvaluator;
        this.neighbourhood = neighbourhood;
        this.tenure = tenure;
        this.maxIterationsWithoutImprovement = maxIterationsWithoutImprovement;
        this.startDecoder = startDecoder;
        this.random = new Random(tieBreakSeed);
    }

    public TabuSearch withVerbose(boolean verbose) {
        this.verbose = verbose;
        return this;
    }

    public List<IterationSnapshot> getHistory() {
        return history;
    }

    @Override
    public Schedule solve(JsspProblem jsspProblem) {
        history.clear();
        MachineSequences current = startDecoder.decode(jsspProblem);
        MachineSequences best = current;
        ScheduleEvaluator.EvaluationResult currentResult = scheduleEvaluator.evaluate(current);
        double bestScore = calculateScore(currentResult);

        TabuList tabu = new TabuList();

        int iteration = 0;
        int sinceImprovement = 0;


        while (sinceImprovement < maxIterationsWithoutImprovement) {
            iteration++;
            currentResult = scheduleEvaluator.evaluate(current);
            List<Move> neighbours = neighbourhood.generate(currentResult, current);
            if (neighbours.isEmpty()) {
                break;
            }
            Move bestMove = null;
            double bestMoveScore = Double.MAX_VALUE;

            for (Move move : neighbours) {
                ScheduleEvaluator.EvaluationResult resultMove = scheduleEvaluator.evaluateMove(current, move, currentResult.head(), currentResult.tail());
                double moveScore = calculateScore(resultMove);
                boolean isTabu = tabu.isTabu(move.getAttribute(), iteration);
                boolean aspiration = moveScore < bestScore;

                if ((!isTabu || aspiration) && moveScore < bestMoveScore) {
                    bestMove = move;
                    bestMoveScore = moveScore;
                }
            }

            if (bestMove == null) {
                bestMove = neighbours.get(random.nextInt(neighbours.size()));
                bestMoveScore = calculateScore(scheduleEvaluator.evaluateMove(current, bestMove, currentResult.head(), currentResult.tail()));
            }

            current = current.swapped(bestMove);
            tabu.setTabu(bestMove.getAttribute(), iteration + tenure);

            if (bestMoveScore < bestScore) {
                best = current;
                bestScore = bestMoveScore;
                sinceImprovement = 0;
            } else {
                sinceImprovement++;
            }

            ScheduleEvaluator.EvaluationResult currentEvaluation = scheduleEvaluator.evaluate(current);
            history.add(new IterationSnapshot(iteration, currentEvaluation.makespan(), scheduleEvaluator.evaluate(best).makespan()));

            if (verbose) {
                System.out.println("Iteration: " + iteration + ", score: " + bestMoveScore + ", makespan: " + currentEvaluation.makespan() + ", dwell violations: " + currentEvaluation.dwellViolations()
                );
            }

        }
        ScheduleEvaluator.EvaluationResult resultBest = scheduleEvaluator.evaluate(best);
        return scheduleEvaluator.toSchedule(resultBest);
    }

    private double calculateScore(ScheduleEvaluator.EvaluationResult result) {
        int violations = result.dwellViolations();
        return violations * 100000 + result.makespan();
    }
}