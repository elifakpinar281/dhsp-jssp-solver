package at.fhv.evaluation.implementation;

import at.fhv.evaluation.IHeuristic;
import at.fhv.solver.State;

import java.util.Random;

public class RandomizedWrapperHeuristic implements IHeuristic {
    private final IHeuristic heuristic;
    private final double randomness;
    private final Random random;

    public RandomizedWrapperHeuristic(IHeuristic heuristic, double randomness, Random random) {
        this.heuristic = heuristic;
        this.randomness = randomness;
        this.random = random;
    }

    @Override
    public double evaluate(State state) {
        double value = heuristic.evaluate(state);

        if (randomness == 0.0) { return value; }

        double eps = (random.nextDouble() // zwischen 0 und 1
                * 2.0 - 1.0) // -> wird daraus -1 bis +1
                * randomness; // mit randomness multiplizieren

        return value + eps * Math.abs(value); // zB 100 + 0.07 * 100 = 107
    }
}
