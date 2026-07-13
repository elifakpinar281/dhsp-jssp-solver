package at.fhv.solver.implementation.tabu;

import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Schedule;
import at.fhv.solver.ISearchAlgorithm;
import at.fhv.solver.IStartDecoder;

import java.util.List;
import java.util.Random;

public class TabuSearch implements ISearchAlgorithm {
    private ScheduleEvaluator scheduleEvaluator;
    private INeighbourhood neighbourhood;
    private int tenure;
    private int maxIterationsWithoutImprovement;
    private IStartDecoder startDecoder;
    private final Random random;

    public TabuSearch(ScheduleEvaluator scheduleEvaluator, INeighbourhood neighbourhood, int tenure, int maxIterationsWithoutImprovement, IStartDecoder startDecoder) {
        this.scheduleEvaluator = scheduleEvaluator;
        this.neighbourhood = neighbourhood;
        this.tenure = tenure;
        this.maxIterationsWithoutImprovement = maxIterationsWithoutImprovement;
        this.startDecoder = startDecoder;
        this.random = new Random(42);
    }

    @Override
    public Schedule solve(JsspProblem jsspProblem) {
        MachineSequences current = startDecoder.decode(jsspProblem);
        MachineSequences best = current;
        ScheduleEvaluator.EvaluationResult currentResult = scheduleEvaluator.evaluate(current);
        int makespanBest = currentResult.makespan();

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

            for (Move move : neighbours) {
                ScheduleEvaluator.EvaluationResult resultMove =
                        scheduleEvaluator.evaluateMove(current, move, currentResult.head(), currentResult.tail());

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

            if (bestMove == null) {
                bestMove = neighbours.get(random.nextInt(neighbours.size()));
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

            System.out.println("Iteration: " + iteration + ", makespan: " + bestMoveMakespan);
        }
        ScheduleEvaluator.EvaluationResult resultBest = scheduleEvaluator.evaluate(best);
        return scheduleEvaluator.toSchedule(resultBest);
    }
}