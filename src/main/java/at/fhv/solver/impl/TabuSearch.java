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
        int makespanBest = currentResult.dwellFeasible() ? currentResult.makespan() : Integer.MAX_VALUE;

        TabuList tabu = new TabuList();

        int iteration = 0;
        int sinceImprovement = 0;


        while (sinceImprovement < maxIterationsWithoutImprovement) {
            iteration++;
            currentResult = scheduleEvaluator.evaluate(current);
            List<Move> neighbours = neighbourhood.generate(currentResult);
            if (neighbours.isEmpty()) {
                break;
            }
            Move bestMove = null;
            int bestMoveMakespan = Integer.MAX_VALUE;
            List<Move> feasibleNeighbours = new ArrayList<>();

            for (Move move : neighbours) {
                ScheduleEvaluator.EvaluationResult resultMove =
                        scheduleEvaluator.evaluateMove(current, move, currentResult.head(), currentResult.tail());

                if (!resultMove.dwellFeasible()) {
                    continue;
                }
                feasibleNeighbours.add(move);

                int makespanMove = resultMove.makespan();
                boolean isTabu = tabu.isTabu(move.getAttribute(), iteration);
                boolean aspiration = makespanMove < makespanBest;
                if ((!isTabu || aspiration) && makespanMove < bestMoveMakespan) {
                    bestMove = move;
                    bestMoveMakespan = makespanMove;
                }
            }

            if (currentResult.criticalPath().isEmpty()) {
                break;
            }

            if (feasibleNeighbours.isEmpty()) {
                break;
            }

            if (bestMove == null) {
                bestMove = feasibleNeighbours.get(random.nextInt(feasibleNeighbours.size()));
                bestMoveMakespan = scheduleEvaluator.evaluateMove(current, bestMove, currentResult.head(), currentResult.tail()).makespan();
            }

            current = current.swapped(bestMove);
            tabu.setTabu(bestMove.getAttribute(), iteration + tenure);

            if (bestMoveMakespan < makespanBest) {
                best = current;
                makespanBest = bestMoveMakespan;
                sinceImprovement = 0;
            } else {
                sinceImprovement++;
            }

            history.add(new IterationSnapshot(iteration, bestMoveMakespan, makespanBest));

            if (verbose) {
                System.out.println("Iteration: " + iteration + ", makespan: " + bestMoveMakespan);
            }
        }
        ScheduleEvaluator.EvaluationResult resultBest = scheduleEvaluator.evaluate(best);
        return scheduleEvaluator.toSchedule(resultBest);
    }
}