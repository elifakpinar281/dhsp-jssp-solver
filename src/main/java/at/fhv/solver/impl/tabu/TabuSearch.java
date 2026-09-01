package at.fhv.solver.impl.tabu;

import at.fhv.model.jssp.JsspProblem;
import at.fhv.model.jssp.Schedule;
import at.fhv.solver.ISearchAlgorithm;
import at.fhv.solver.IStartDecoder;
import at.fhv.solver.SchedulingProblem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class TabuSearch implements ISearchAlgorithm {
    private static final int DEFAULT_MAX_RESTARTS = 100;
    private static final int DEFAULT_KICK_THRESHOLD = 2;
    private static final int KICK_STRENGTH = 6;
    private static final int KICK_ATTEMPTS = 15;
    private static final int FULL_RESTART_EVERY = 4;
    private static final int MAX_HISTORY = 50_000;

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
    private final long seed;
    private final Random random;
    private final int maxRestarts;
    private final int kickThreshold;
    private final long timeBudgetMs;

    private boolean verbose = false;
    private final List<IterationSnapshot> history = new ArrayList<>();

    public TabuSearch(ScheduleEvaluator scheduleEvaluator, INeighbourhood neighbourhood, int tenure, int maxIterationsWithoutImprovement, IStartDecoder startDecoder) {
        this(scheduleEvaluator, neighbourhood, tenure, maxIterationsWithoutImprovement, startDecoder, 42L);
    }

    public TabuSearch(ScheduleEvaluator scheduleEvaluator, INeighbourhood neighbourhood, int tenure, int maxIterationsWithoutImprovement, IStartDecoder startDecoder, long seed) {
        this(scheduleEvaluator, neighbourhood, tenure, maxIterationsWithoutImprovement, startDecoder, seed, DEFAULT_MAX_RESTARTS);
    }

    public TabuSearch(ScheduleEvaluator scheduleEvaluator, INeighbourhood neighbourhood, int tenure, int maxIterationsWithoutImprovement, IStartDecoder startDecoder, long seed, int maxRestarts) {
        this(scheduleEvaluator, neighbourhood, tenure, maxIterationsWithoutImprovement, startDecoder, seed, maxRestarts, 0L);
    }

    public TabuSearch(ScheduleEvaluator scheduleEvaluator, INeighbourhood neighbourhood, int tenure, int maxIterationsWithoutImprovement, IStartDecoder startDecoder, long seed, int maxRestarts, long timeBudgetMs) {
        this(scheduleEvaluator, neighbourhood, tenure, maxIterationsWithoutImprovement, startDecoder, seed, maxRestarts, timeBudgetMs, DEFAULT_KICK_THRESHOLD);
    }

    public TabuSearch(ScheduleEvaluator scheduleEvaluator, INeighbourhood neighbourhood, int tenure, int maxIterationsWithoutImprovement, IStartDecoder startDecoder, long seed, int maxRestarts, long timeBudgetMs, int kickThreshold) {
        this.scheduleEvaluator = scheduleEvaluator;
        this.neighbourhood = neighbourhood;
        this.tenure = tenure;
        this.maxIterationsWithoutImprovement = maxIterationsWithoutImprovement;
        this.startDecoder = startDecoder;
        this.seed = seed;
        this.random = new Random(seed);
        this.maxRestarts = maxRestarts;
        this.kickThreshold = Math.max(1, kickThreshold);
        this.timeBudgetMs = timeBudgetMs;
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
        Map<Move.MoveAttribute, Integer> frequency;
    }

    @Override
    public Schedule solve(SchedulingProblem schedulingProblem) {
        if (!(schedulingProblem instanceof JsspProblem problem)) {
            throw new UnsupportedOperationException("TabuSearch supports only JSSP mode");
        }
        history.clear();
        random.setSeed(seed);

        SearchState s = new SearchState();
        s.current = startDecoder.decode(problem);
        s.currentResult = scheduleEvaluator.evaluate(s.current);

        s.best = null;
        s.bestMakespan = Integer.MAX_VALUE;
        s.tabu = new TabuList();
        s.noImprovement = 0;
        s.restarts = 0;
        s.frequency = new HashMap<>();
        remember(s);

        long deadline = timeBudgetMs > 0 ? System.currentTimeMillis() + timeBudgetMs : Long.MAX_VALUE;
        int iteration = 0;

        while (!isFinished(s, deadline)) {
            iteration++;
            step(s, iteration);

            if (s.noImprovement >= kickThreshold) {
                destroy(s, problem);
            }
        }

        if (s.best == null) { return null; }

        ScheduleEvaluator.EvaluationResult result = scheduleEvaluator.evaluate(s.best);
        if (!result.valid()) { return null; }
        return scheduleEvaluator.toSchedule(result);
    }

    private boolean isFinished(SearchState s, long deadline) {
        if (timeBudgetMs > 0) {
            return System.currentTimeMillis() >= deadline;
        }
        return s.restarts >= maxRestarts && s.noImprovement >= maxIterationsWithoutImprovement;
    }

    private void step(SearchState s, int iteration) {
        List<Move> neighbours = neighbourhood.generate(s.currentResult, s.current);
        if (neighbours.isEmpty()) {
            s.noImprovement++;
            return;
        }

        List<Move> bestCandidates = new ArrayList<>();
        int bestCandidateMakespan = Integer.MAX_VALUE;
        List<Move> bestValid = new ArrayList<>();
        int bestValidMakespan = Integer.MAX_VALUE;

        boolean anyValid = false;

        for (Move move : neighbours) {
            ScheduleEvaluator.Result result = scheduleEvaluator.evaluateResult(s.current.applied(move));
            if (!result.valid()) { continue; }
            anyValid = true;

            if (result.makespan() < bestValidMakespan) {
                bestValidMakespan = result.makespan();
                bestValid.clear();
                bestValid.add(move);
            } else if (result.makespan() == bestValidMakespan) {
                bestValid.add(move);
            }

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

        if (!anyValid) {
            s.noImprovement++;
            return;
        }

        List<Move> pool = bestCandidates.isEmpty() ? bestValid : bestCandidates;
        Move chosenMove = leastUsed(pool, s.frequency);

        s.current = s.current.applied(chosenMove);
        s.currentResult = scheduleEvaluator.evaluate(s.current);
        s.tabu.setTabu(chosenMove.getAttribute(), iteration + dynamicTenure());
        s.frequency.merge(chosenMove.getAttribute(), 1, Integer::sum);

        if (remember(s)) {
            s.noImprovement = 0;
        } else {
            s.noImprovement++;
        }

        if (history.size() < MAX_HISTORY) {
            history.add(new IterationSnapshot(iteration, s.currentResult.makespan(), s.bestMakespan));
        }

        if (verbose) {
            System.out.println("Iteration " + iteration + " makespan=" + s.currentResult.makespan() + " bestMakespan=" + s.bestMakespan);
        }
    }

    private Move leastUsed(List<Move> candidates, Map<Move.MoveAttribute, Integer> frequency) {
        int lowest = Integer.MAX_VALUE;
        List<Move> rarest = new ArrayList<>();

        for (Move move : candidates) {
            int used = frequency.getOrDefault(move.getAttribute(), 0);
            if (used < lowest) {
                lowest = used;
                rarest.clear();
                rarest.add(move);
            } else if (used == lowest) {
                rarest.add(move);
            }
        }
        return rarest.get(random.nextInt(rarest.size()));
    }

    private int dynamicTenure() {
        int spread = Math.max(1, tenure / 2);
        return Math.max(1, tenure - spread + random.nextInt(2 * spread + 1));
    }

    private boolean remember(SearchState s) {
        if (!s.currentResult.valid()) { return false; }
        if (s.currentResult.makespan() >= s.bestMakespan) { return false; }

        s.best = s.current.copy();
        s.bestMakespan = s.currentResult.makespan();
        return true;
    }

    private void destroy(SearchState s, JsspProblem problem) {
        if (timeBudgetMs <= 0 && s.restarts >= maxRestarts) { return; }
        s.restarts++;
        boolean fullRestart = s.best == null || s.restarts % FULL_RESTART_EVERY == 0;

        if (fullRestart) {
            s.current = startDecoder.decode(problem);
        } else {
            s.current = s.best.copy();
            kick(s);
        }

        s.currentResult = scheduleEvaluator.evaluate(s.current);
        s.tabu = new TabuList();
        s.noImprovement = 0;

        if (verbose) {
            String reason = fullRestart ? "full restart from start decoder" : "kick from elite solution";
            System.out.println("Destroy " + s.restarts + " (" + reason + ")");
        }
        remember(s);
    }

    private void kick(SearchState s) {
        ScheduleEvaluator.EvaluationResult result = scheduleEvaluator.evaluate(s.current);

        for (int i = 0; i < KICK_STRENGTH; i++) {
            if (!result.valid()) { return; }

            List<Move> moves = neighbourhood.generate(result, s.current);
            if (moves.isEmpty()) { return; }

            boolean applied = false;
            for (int attempt = 0; attempt < KICK_ATTEMPTS && !applied; attempt++) {
                Move move = moves.get(random.nextInt(moves.size()));
                MachineSequences candidate = s.current.applied(move);

                if (!scheduleEvaluator.evaluateResult(candidate).valid()) { continue; }
                s.current = candidate;
                applied = true;
            }
            if (!applied) { return; }
            result = scheduleEvaluator.evaluate(s.current);
        }
    }
}