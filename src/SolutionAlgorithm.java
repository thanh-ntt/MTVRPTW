import java.util.*;

/**
 * Solution algorithm for the MTVRPTW.
 *  1. Initial solution construction (MT-Solomon)
 *  2. While termination condition not met:
 *      While strong-perturbation condition not met:
 *          Local search: Or-opt, Relocate
 *          Weak-perturbation: Random exchange moves
 *      Strong-perturbation: 2-opt*
 *  3. Optimize the travel distance (post-optimization step)
 *     We use a multi-start strategy to optimize the total distance travelled:
 *     select the local optimal solutions (as initial solution for distance improvement phase) from the previous phase,
 *     all having number of vehicles equivalent to the best found solution.
 *
 *  Some of the aforementioned algorithms are modified to adapt for the multi-trip nature of MTVRPTW.
 */
public class SolutionAlgorithm implements ConstructionAlgorithm {
    DataModel dataModel;

    // Set parameters and constants
    static final List<Integer> numExchanges = new ArrayList<>(Arrays.asList(10, 100));  // use different # exchanges
    static final int iterationThreshold = 10000, weakPerturbationThreshold = 100;
    static final int numAttemptExchangeThreshold = 100000;

    @Override
    public List<Route> run(DataModel dataModel) {
        this.dataModel = dataModel;
        List<Route> initialSolution = new MTSolomonAlgorithm().run(dataModel);

        List<List<Route>> localOptima = new ArrayList<>();  // solutions found with ILS
        // Run the ILS algorithm with different number of exchanges - vehicle # optimization phase
        numExchanges.forEach(nE -> localOptima.addAll(runWithNumExchanges(dataModel, initialSolution, nE)));

        // Different configurations might give different # vehicles, only keep solutions with least # vehicles
        int bestNumberOfVehicles = localOptima.stream().min(Comparator.comparingInt(List::size)).get().size();
        localOptima.removeIf(s -> s.size() > bestNumberOfVehicles);

        localOptima.forEach(s -> optimizeDistance(s, dataModel));  // Distance improvement phase

        // Get solution with lowest total distance
        List<Route> bestSolution = localOptima.stream().min(Comparator.comparingDouble(s -> Utils.getTotalDistance(dataModel, s))).get();
        bestSolution.forEach(Route::removeDuplicatedDepot);  // Remove dummy depots (not affect final result)
        return bestSolution;
    }

    /**
     * The iterated local search algorithm
     * Local search move: Or-opt algorithm to optimize the routes, then Relocate algorithm to reduce # vehicles
     * Acceptance criteria: best neighborhood
     * Perturbation scheme: random exchange moves (once read intensificationThreshold, do perturbation)
     * If the algorithm is able to reduce the # vehicle by 1, reset the total iterationThreshold
     *
     * @param dataModel the problem test data
     * @param initialSolution
     * @param numExchanges number of random exchange operators in weak-perturbation move
     * @return list of local optima solutions with same minimum number of vehicles
     */
    public List<List<Route>> runWithNumExchanges(DataModel dataModel, List<Route> initialSolution, int numExchanges) {
        List<List<Route>> localOptima = new ArrayList<>();
        List<Route> solution = Utils.deepCopySolution(initialSolution);
        // Termination conditions
        int numIteration = 0;
        outerWhile:
        while (numIteration < iterationThreshold) {
            int numWeakPerturbations = 0;
            while (numWeakPerturbations++ < weakPerturbationThreshold && numIteration++ < iterationThreshold) {
                // Subsidiary local search
                solution = OrOptAlgorithm.run(solution, dataModel);
                localOptima.add(Utils.deepCopySolution(solution));  // Add to local optima list
                List<Route> nextSolution = RelocateAlgorithm.run(solution, dataModel);

                if (nextSolution.size() < solution.size()) {  // reduce # vehicle, restart algorithm
                    solution = nextSolution;
                    numIteration = 0;  // running up to iterationThreshold again
                    localOptima.clear();  // all previously stored local optima has higher # vehicles, discard
                    continue outerWhile;
                } else {  // same # vehicles, perform weak-perturbation
                    weakPerturb(nextSolution, numExchanges);
                    solution = nextSolution;  // accept all
                }
            }
            // Perform strong-perturbation
            strongPerturb(solution);
        }

        return localOptima;
    }

    /**
     * Optimize the total travelled distance of the solution.
     * We use a combination of Or-opt algorithm and Exchange algorithm.
     * Both algorithms use first-feasible move strategy.
     * @param solution
     * @param dataModel
     */
    void optimizeDistance(List<Route> solution, DataModel dataModel) {
        double prevDistance = Utils.getTotalDistance(dataModel, solution);
        boolean localOptimal = false;
        while (!localOptimal) {
            OrOptAlgorithm.optimizeDistance(solution, dataModel);
            ExchangeAlgorithm.optimizeDistance(solution, dataModel);
            double curDistance = Utils.getTotalDistance(dataModel, solution);
            if (curDistance < prevDistance) {
                prevDistance = curDistance;
            } else {
                localOptimal = true;
            }
        }
    }

    /**
     * Weak-perturbation move: run a number of random exchange operators
     * to move from a solution to its neighbor.
     * @param s current solution
     * @param numExchanges number of random exchanges
     */
    void weakPerturb(List<Route> s, int numExchanges) {
        int n = s.size();
        Random random = new Random(0);
        int countIterations = 0, countExchanges = 0;
        while (countExchanges < numExchanges && countIterations < numAttemptExchangeThreshold) {
            countIterations++;
            int r1Idx = random.nextInt(n), r2Idx = random.nextInt(n);
            if (r1Idx == r2Idx) continue;
            Route r1 = s.get(r1Idx), r2 = s.get(r2Idx);
            int p1 = random.nextInt(r1.getLength()), p2 = random.nextInt(r2.getLength());
            Node u1 = r1.get(p1), u2 = r2.get(p2);
            if (u1 == dataModel.getDepot() || u2 == dataModel.getDepot()) continue;
            if (Utils.checkExchangeOperator(dataModel, r1, p1, r2, p2)) {
                r1.removeCustomerAtIndex(p1);
                r2.removeCustomerAtIndex(p2);
                r1.insertAtPosition(p1, u2);
                r2.insertAtPosition(p2, u1);
                countExchanges++;
            }
        }
    }

    /**
     * Perturbation.
     * Here we use 2-opt* algorithm as the perturbation.
     * @param s current solution
     */
    void strongPerturb(List<Route> s) {
        for (int r1Idx = 0; r1Idx < s.size() - 1; r1Idx++) {
            for (int r2Idx = r1Idx + 1; r2Idx < s.size(); r2Idx++) {
                twoOptStar(s.get(r1Idx), s.get(r2Idx));
            }
        }
    }

    /**
     * Run 2-opt* exchange with the best-feasible scheme on 2 routes.
     */
    void twoOptStar(Route r1, Route r2) {
        double minCost = 1e9;
        int bestP1 = -1, bestP2 = -1;
        int r1Load = 0, r2Load = 0;
        // Find the best 2-opt* exchange
        for (int p1 = 0; p1 < r1.getLength() - 1; p1++) {
            Node a1 = r1.get(p1), b1 = r1.get(p1 + 1);
            r1Load = a1 == dataModel.getDepot() ? 0 : r1Load + a1.demand;
            for (int p2 = 0; p2 < r2.getLength() - 1; p2++) {
                Node a2 = r2.get(p2), b2 = r2.get(p2 + 1);
                if (a1 == dataModel.getDepot() && a2 == dataModel.getDepot()) continue;
                r2Load = a2 == dataModel.getDepot() ? 0 : r2Load + a2.demand;

                // check vehicle capacity
                boolean checkCapacity = (r1Load + (r2.getVehicleLoadCurTrip(p2 + 1) - r2Load) <= dataModel.getVehicleCapacity())
                        && (r2Load + (r1.getVehicleLoadCurTrip(p1 + 1) - r1Load) <= dataModel.getVehicleCapacity());
                if (!checkCapacity) continue;

                // check time feasibility
                // Compute new arrival time at b1 and b2
                double arrivalTimeB1 = r2.getStartingServiceTimeAt(p2) + a2.serviceTime + dataModel.dist(a2, b1);
                double arrivalTimeB2 = r1.getStartingServiceTimeAt(p1) + a1.serviceTime + dataModel.dist(a1, b2);
                double pushForwardB1 = Math.max(arrivalTimeB1, b1.readyTime) - r1.getStartingServiceTimeAt(p1 + 1);
                double pushForwardB2 = Math.max(arrivalTimeB2, b2.readyTime) - r2.getStartingServiceTimeAt(p2 + 1);
                boolean checkTime = r1.checkPushForwardTimeFromPosition(pushForwardB1, p1 + 1)
                        && r2.checkPushForwardTimeFromPosition(pushForwardB2, p2 + 1);
                if (!checkTime) continue;

                double cost = pushForwardB1 + pushForwardB2;
                if (cost < minCost) {
                    minCost = cost;
                    bestP1 = p1;
                    bestP2 = p2;
                }
            }
        }
        if (bestP1 != -1) {
            Utils.exchangeTwoOptStar(dataModel, r1, bestP1, r2, bestP2);
        }
    }
}
