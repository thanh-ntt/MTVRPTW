import java.util.*;

/**
 * Or-opt algorithm.
 * Inspired by Potvin & Rousseau, 1995
 */
public class OrOptAlgorithm {
    private static final int EPSILON = 1;
    public static List<Route> run(List<Route> solution, DataModel dataModel) {
        // Use deep copy so that we can modify routes without changing the original solution
        List<Route> curSolution = Utils.deepCopySolution(solution);
        for (Route r : curSolution) {
//            orOpt(r, dataModel);
            orOptBestImproving(r, dataModel);
        }
        return curSolution;
    }

    static void orOptBestImproving(Route route, DataModel dataModel) {
        if (route.getLength() <= 3) return;
        int n = route.getLength();

        for (int segmentLength = 1; segmentLength <= 3; segmentLength++) {
            double minCost = 1e9;
            List<Node> bestPath = null;
            for (int i = 0; i < n - segmentLength - 1; i++) {
                Node x1 = route.getCustomerAt(i), x2 = route.getCustomerAt(i + 1);
                int j = i + segmentLength;
                Node y1 = route.getCustomerAt(j), y2 = route.getCustomerAt(j + 1);
                for (int k = 0; k < n - 1; k++) {
                    if (k >= i && k <= j) continue;
                    Node z1 = route.getCustomerAt(k), z2 = route.getCustomerAt(k + 1);

                    // compute cost function, check time feasibility
                    // first construct the new route
                    List<Node> oldPath = route.routedPath;
                    List<Node> newPath = new ArrayList<>();
                    if (k < i) {
                        newPath.addAll(oldPath.subList(0, k + 1));  // [0, z1]
                        newPath.addAll(oldPath.subList(i + 1, j + 1));  // [x2, y1]
                        newPath.addAll(oldPath.subList(k + 1, i + 1));  // [z2, x1]
                        newPath.addAll(oldPath.subList(j + 1, n));  // [y2, 0]
                    } else {
                        newPath.addAll(oldPath.subList(0, i + 1));  // [0, x1]
                        newPath.addAll(oldPath.subList(j + 1, k + 1));  // [y2, z1]
                        newPath.addAll(oldPath.subList(i + 1, j + 1));  // [x2, y1]
                        newPath.addAll(oldPath.subList(k + 1, n));  // [z2, 0]
                    }

                    // same cost function calculation for both cases
                    // minimize the cost -> compute f(after) - f(before)
                    double distanceCost = dataModel.dist(x1, y2) + dataModel.dist(z1, x2) + dataModel.dist(y1, z2)
                            - (dataModel.dist(x1, x2) + dataModel.dist(y1, y2) + dataModel.dist(z1, z2));
                    // Incorporate the time aspect into the cost function (additional to original cost function)
//                    double waitingTimeCost = Utils.getRoutedPathWaitingTime(dataModel, newPath) - Utils.getRoutedPathWaitingTime(dataModel, oldPath);

//                    double cost = distanceCost + waitingTimeCost;
                    double cost = distanceCost;
                    // we put the check route feasibility here to optimize the check (only check if the cost < minCost)
                    if (cost < minCost && Utils.checkRoutedPathFeasibility(dataModel, newPath)) {
                        minCost = cost;
                        bestPath = newPath;
                    }
                }
            }
            if (bestPath != null) {
                route.routedPath = bestPath;
                route.initializeVariables();
                assert Utils.isValidRoute(dataModel, route);
            }
        }
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
                    Node x1 = route.getCustomerAt(i), x2 = route.getCustomerAt(i + 1);
                    int j = i + segmentLength;
                    Node y1 = route.getCustomerAt(j), y2 = route.getCustomerAt(j + 1);
                    for (int shift = segmentLength + 1; i + shift + 1 < n; shift++) {
                        int k = i + shift;
//                    for (int k = j + 1; k < n - 1; k++) {
                        Node z1 = route.getCustomerAt(k), z2 = route.getCustomerAt(k + 1);

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
                        if (cost + EPSILON < 0) {  // gain
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
}
