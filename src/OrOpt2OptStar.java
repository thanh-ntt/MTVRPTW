import java.util.*;

/**
 * 2-opt* and or-opt algorithm.
 * Inspired by Potvin & Rousseau, 1995
 */
public class OrOpt2OptStar {
    private static final int EPSILON = 2;
    public static List<Route> run(List<Route> solution, DataModel dataModel) {
        // Use deep copy so that we can modify routes without changing the original solution
        List<Route> curSolution = Utils.deepCopySolution(solution);
        curSolution = orOpt(curSolution, dataModel);
        List<Route> result = run2OptStarExchange(curSolution, dataModel);
        assert Utils.isValidSolution(dataModel, result);
        return result;
    }

    static List<Route> orOpt(List<Route> solution, DataModel dataModel) {
        for (Route r : solution) {
            orOpt(r, dataModel);
        }
        return solution;
    }

    static void orOpt(Route route, DataModel dataModel) {
        int n = route.getLength();
        if (route.getLength() <= 3) return;
        boolean locallyOptimal = false;

        while (!locallyOptimal) {
            locallyOptimal = true;

            for (int segmentLength = 3; segmentLength >= 1; segmentLength--) {
                outerLoop:
                for (int i = 0; i < n - segmentLength - 1; i++) {
                    Node x1 = route.get(i), x2 = route.get(i + 1);
                    int j = i + segmentLength;
                    Node y1 = route.get(j), y2 = route.get(j + 1);
                    for (int shift = segmentLength + 1; i + shift + 1 < n; shift++) {
                        int k = i + shift;
//                    for (int k = j + 1; k < n - 1; k++) {
                        Node z1 = route.get(k), z2 = route.get(k + 1);

                        // compute cost function, check time feasibility
                        // first construct the new route
                        List<Node> oldPath = route.routedPath;
                        List<Node> newPath = new ArrayList<>();
                        newPath.addAll(oldPath.subList(0, i + 1));  // [0, x1]
                        newPath.addAll(oldPath.subList(j + 1, k + 1));  // [y2, z1]
                        newPath.addAll(oldPath.subList(i + 1, j + 1));  // [x2, y1]
                        newPath.addAll(oldPath.subList(k + 1, n));  // [z2, 0]

                        if (!Utils.checkRoutedPathFeasibility(dataModel, newPath)) continue;

                        // same cost function calculation for both cases
                        // minimize the cost -> compute f(after) - f(before)
                        double distanceCost = dataModel.dist(x1, y2) + dataModel.dist(z1, x2) + dataModel.dist(y1, z2)
                                - (dataModel.dist(x1, x2) + dataModel.dist(y1, y2) + dataModel.dist(z1, z2));
                        // Incorporate the time aspect into the cost function (additional to original cost function)
                        double waitingTimeCost = Utils.getRoutedPathWaitingTime(dataModel, newPath) - Utils.getRoutedPathWaitingTime(dataModel, oldPath);

                        double cost = distanceCost + waitingTimeCost;
                        if (cost < 0) {  // gain
                            route.routedPath = newPath;
                            route.initializeVariables();
                            assert Utils.isValidRoute(dataModel, route);
                            break outerLoop;
                        }
                    }
                }
            }
        }
    }

    /**
     * Run 2-opt* until local optima
     * @param solution
     * @param dataModel
     */
    static List<Route> run2OptStarExchange(List<Route> solution, DataModel dataModel) {
        for (int r1Idx = 0; r1Idx < solution.size() - 1; r1Idx++) {
            Route r1 = solution.get(r1Idx);
            int r1Load = 0;
            for (int i = 0; i < r1.getLength() - 1; i++) {
                Node a1 = r1.get(i), b1 = r1.get(i + 1);
                r1Load = a1 == dataModel.getDepot() ? 0 : r1Load + a1.demand;
                for (int r2Idx = r1Idx + 1; r2Idx < solution.size(); r2Idx++) {
                    Route r2 = solution.get(r2Idx);
                    int r2Load = 0;
                    for (int j = 0; j < r2.getLength() - 1; j++) {
                        Node a2 = r2.get(j), b2 = r2.get(j + 1);
                        r2Load = a2 == dataModel.getDepot() ? 0 : r2Load + a2.demand;

                        // Check feasibility and compute cost
                        // check cost (later can change to a function of time and distance)
                        double a1b1 = dataModel.dist(a1, b1), a2b2 = dataModel.dist(a2, b2),
                                a1b2 = dataModel.dist(a1, b2), a2b1 = dataModel.dist(a2, b1);
                        double saving = a1b2 + a2b1 - (a1b1 + a2b2);
                        if (saving <= EPSILON) continue;

                        // check vehicle capacity
                        boolean checkCapacity = (r1Load + (r2.getVehicleLoadCurTrip(j + 1) - r2Load) <= dataModel.getVehicleCapacity())
                                && (r2Load + (r1.getVehicleLoadCurTrip(i + 1) - r1Load) <= dataModel.getVehicleCapacity());
                        if (!checkCapacity) continue;

                        // check time feasibility
                        // Compute new arrival time at b1 and b2
                        double arrivalTimeB1 = r2.getStartingServiceTimeAt(j) + a2.serviceTime + a2b1;
                        double arrivalTimeB2 = r1.getStartingServiceTimeAt(i) + a1.serviceTime + a1b2;
                        double pushForwardB1 = Math.max(arrivalTimeB1, b1.readyTime) - r1.getStartingServiceTimeAt(i + 1);
                        double pushForwardB2 = Math.max(arrivalTimeB2, b2.readyTime) - r2.getStartingServiceTimeAt(j + 1);
                        boolean checkTime = r1.checkPushForwardTimeFromPosition(pushForwardB1, i + 1)
                                && r2.checkPushForwardTimeFromPosition(pushForwardB2, j + 1);
                        if (!checkTime) continue;

                        // now we do 2-opt* exchange
                        Utils.exchangeTwoOptStar(dataModel, r1, i, r2, j);
                        if (r1.getNumDemandNodes() == 0) {  // not empty route (if empty -> we just reduce # routes)
                            solution.remove(r1);
                        }
                        if (r2.getNumDemandNodes() == 0) {  // not empty route (if empty -> we just reduce # routes)
                            solution.remove(r2);
                        }

                        return run2OptStarExchange(solution, dataModel);
                    }
                }
            }
        }
        assert Utils.isValidSolution(dataModel, solution);
        return solution;
    }
}
