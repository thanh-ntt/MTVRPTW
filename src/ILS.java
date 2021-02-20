import java.util.*;

/**
 * Iterated local search for the MTVRPTW.
 */
public class ILS implements ConstructionAlgorithm {
    DataModel dataModel;

    @Override
    public List<Route> run(DataModel dataModel) {
        this.dataModel = dataModel;
        List<Route> initialSolution = new MTSolomonAlgorithm().run(dataModel);
        List<Integer> numExchanges = new ArrayList<>(Arrays.asList(10, 100));
        List<Route> bestSolution = numExchanges.stream().map(n -> runWithParameter(dataModel, initialSolution, n)).min(Comparator.comparingInt(List::size)).get();
        return bestSolution;
    }

    public List<Route> runWithParameter(DataModel dataModel, List<Route> initialSolution, int numExchanges) {
        List<Route> curSolution = Utils.deepCopySolution(initialSolution);
        // While termination condition not satisfied
        int numIteration = 0, iterationThreshold = 5000, intensificationThreshold = 100;
        outerWhile:
        while (numIteration < iterationThreshold) {
            int numIntensification = 0;
            while (numIntensification++ < intensificationThreshold && numIteration++ < iterationThreshold) {
                curSolution = OrOptAlgorithm.run(curSolution, dataModel);
                List<Route> nextSolution = RelocateAlgorithm.run(curSolution, dataModel);
                if (nextSolution.size() < curSolution.size()) {  // reduce # vehicle, restart algorithm
                    curSolution = nextSolution;
                    numIteration = 0;  // running up to iterationThreshold again
                    continue outerWhile;
                } else {  // same # vehicles, move to a neighbourhood solution
                    neighbourhoodMove(nextSolution, numExchanges);
                    curSolution = nextSolution;  // accept all
                }
            }
            // Perturbation
            perturb2OptStar(curSolution);
        }
        assert Utils.isValidSolution(dataModel, curSolution);
        return curSolution;
    }

    /**
     * Run a number of random exchange operators to move from a solution to a neighbourhood
     * @param s current solution
     * @param numExchanges number of random exchanges
     */
    void neighbourhoodMove(List<Route> s, int numExchanges) {
        int n = s.size();
        Random random = new Random(0);
        int numIterations = 0;
        int countExchanges = 0;
        while (countExchanges < numExchanges && numIterations < 100000) {
            numIterations++;
            int r1Idx = random.nextInt(n), r2Idx = random.nextInt(n);
            if (r1Idx == r2Idx) continue;
            Route r1 = s.get(r1Idx), r2 = s.get(r2Idx);
            int p1 = random.nextInt(r1.getLength()), p2 = random.nextInt(r2.getLength());
            Node u1 = r1.getCustomerAt(p1), u2 = r2.getCustomerAt(p2);
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

    void perturb2OptStar(List<Route> s) {
        for (int r1Idx = 0; r1Idx < s.size() - 1; r1Idx++) {
            for (int r2Idx = r1Idx + 1; r2Idx < s.size(); r2Idx++) {
                twoOptStar(s.get(r1Idx), s.get(r2Idx));
            }
        }
    }

    void twoOptStar(Route r1, Route r2) {
        double minCost = 1e9;
        int bestP1 = -1, bestP2 = -1;
        int r1Load = 0, r2Load = 0;
        // Find the best 2-opt* exchange
        for (int p1 = 0; p1 < r1.getLength() - 1; p1++) {
            Node a1 = r1.getCustomerAt(p1), b1 = r1.getCustomerAt(p1 + 1);
            r1Load = a1 == dataModel.getDepot() ? 0 : r1Load + a1.demand;
            for (int p2 = 0; p2 < r2.getLength() - 1; p2++) {
                Node a2 = r2.getCustomerAt(p2), b2 = r2.getCustomerAt(p2 + 1);
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
