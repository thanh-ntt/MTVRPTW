import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Iterated local search for the MTVRPTW.
 */
public class ILS implements ConstructionAlgorithm {
    DataModel dataModel;

    @Override
    public List<Route> run(DataModel dataModel) {
        this.dataModel = dataModel;
        List<Route> initialSolution = new SolomonI1Algorithm().run(dataModel);
        List<Route> curSolution = initialSolution;
        // While termination condition not satisfied
        int countIterations = 0, numIterationThreshold = 10000, numIntensificationThreshold = 100;
        int num2OptRun = 0, numPerturbations = 0;
        outerWhile:
        while (countIterations < numIterationThreshold) {
            int i = 0;
            while (i++ < numIntensificationThreshold && countIterations++ < numIterationThreshold) {
                curSolution = OrOptAlgorithm.run(curSolution, dataModel);
                List<Route> nextSolution = RelocateAlgorithm.run(curSolution, dataModel);
                if (nextSolution.size() < curSolution.size()) {  // reduce # vehicle, restart algorithm
                    curSolution = nextSolution;
                    countIterations = 0;  // running numIterationThreshold again
                    continue outerWhile;
                } else {  // move to local solution
                    exchangeAlgorithm(nextSolution);
                    curSolution = nextSolution;  // accept all
                }
            }
            // Perturbation
            num2OptRun += perturb(curSolution);
            numPerturbations++;
        }
//        System.out.println("Average # 2-opt: " + 1.0 * num2OptRun / numPerturbations);
        assert Utils.isValidSolution(dataModel, curSolution);
        return curSolution;
    }

    void exchangeAlgorithm(List<Route> s) {
        int n = s.size();
        Random random = new Random(0);
        int numExchangeThreshold = 25;
        int numIterations = 0;
        int count = 0;
        while (count < numExchangeThreshold && numIterations < 100000) {
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
                count++;
            }
        }
    }

    int perturb(List<Route> s) {
        int count = 0;
        // 2-opt* move for inter-route exchanges of nodes
        loop1:
        for (int i = 0; i < s.size() - 1; i++) {
            int l1 = s.get(i).getLength();
            List<Integer> indicesR1 = new ArrayList<>();
            indicesR1.add(l1 / 2);
            for (int k = 1; k < l1 / 2 - 2; k++) {
                indicesR1.add(l1 / 2 - k);
                indicesR1.add(l1 / 2 + k);
            }
            for (int p1 : indicesR1) {
                for (int j = i + 1; j < s.size(); j++) {
                    if (twoOptStar(s.get(i), p1, s.get(j))) {
                        count++;
                        continue loop1;
                    }
                }
            }
        }
        return count;
    }

    void perturb2OptStar(List<Route> s) {
        Node depot = dataModel.getDepot();
        for (int r1Idx = 0; r1Idx < s.size() - 1; r1Idx++) {
            Route r1 = s.get(r1Idx);
            for (int r2Idx = 0; r2Idx < s.size(); r2Idx++) {
                Route r2 = s.get(r2Idx);
                // Find the best 2-opt* exchange
                for (int p1 = 0; p1 < r1.getLength() - 1; p1++) {
                    for (int p2 = 0; p2 < r2.getLength() - 1; p2++) {
                        Node x1 = r1.getCustomerAt(p1), y1 = r1.getCustomerAt(p1 + 1);
                        Node x2 = r2.getCustomerAt(p2), y2 = r2.getCustomerAt(p2 + 1);
                        if (x1 == depot && x2 == depot) continue;

                    }
                }
            }
        }
    }

    boolean twoOptStar(Route r1, int p1, Route r2) {
        Node depot = dataModel.getDepot();
        int capacity = dataModel.getVehicleCapacity();
        int l1 = r1.getLength(), l2 = r2.getLength();
        List<Integer> indicesR1 = new ArrayList<>();
        indicesR1.add(l1 / 2);
        for (int i = 1; i < l1 / 2 - 2; i++) {  // candidate from 3rd node -> second last node
            indicesR1.add(l1 / 2 - i);
            indicesR1.add(l1 / 2 + i);
        }
        Node a1 = r1.getCustomerAt(p1), b1 = r1.getCustomerAt(p1 + 1);
        // Compute r1 load up to p1
        int r1Load = 0;
        for (int i = p1; i >= 0 && r1.getCustomerAt(i) != depot; i--) {
            r1Load += r1.getCustomerAt(i).demand;
        }
        int r2Load = 0;
        for (int p2 = 0; p2 < l2 - 1; p2++) {
            Node a2 = r2.getCustomerAt(p2), b2 = r2.getCustomerAt(p2 + 1);
            r2Load = a2 == depot ? 0 : r2Load + a2.demand;

            // Check feasibility
            // check vehicle capacity
            boolean checkCapacity = (r1Load + (r2.getVehicleLoadCurTrip(p2 + 1) - r2Load) <= capacity)
                    && (r2Load + (r1.getVehicleLoadCurTrip(p1 + 1) - r1Load) <= capacity);
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

            // now we do 2-opt* exchange
            Utils.exchangeTwoOptStar(dataModel, r1, p1, r2, p2);
            return true;
        }
        return false;
    }

    List<Route> getBetterSolution(List<Route> s1, List<Route> s2) {
        if (s1.size() < s2.size()) return s1;
        else if (s1.size() > s2.size()) return s2;
        else {
            int shortestRouteS1 = s1.stream().mapToInt(Route::getLength).min().getAsInt();
            int shortestRouteS2 = s2.stream().mapToInt(Route::getLength).min().getAsInt();
            if (shortestRouteS1 < shortestRouteS2) return s1;
            else return s2;
        }
    }
}
