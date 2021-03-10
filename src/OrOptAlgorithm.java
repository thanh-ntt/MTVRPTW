import java.util.*;

/**
 * Or-opt algorithm.
 * Inspired by Potvin & Rousseau, 1995.
 * The length of or-opt segment ranges from 1 -> 3.
 */
public class OrOptAlgorithm {
    static final double EPSILON = 0.01;
    public static List<Route> run(List<Route> solution, DataModel dataModel) {
        // Use deep copy so that we can modify routes without changing the original solution
        List<Route> curSolution = Utils.deepCopySolution(solution);
        curSolution.forEach(r -> orOptBestImproving(r, dataModel));
        return curSolution;
    }

    public static void optimizeDistance(List<Route> s, DataModel dataModel) {
        s.forEach(r -> orOptFirstImproving(r, dataModel));
    }

    /**
     * Run Or-opt algorithm to optimize a route.
     * Acceptance criterion is set to best-improving move.
     * The cost function is defined based on travel distance.
     * @param route input route
     * @param dataModel
     */
    static void orOptBestImproving(Route route, DataModel dataModel) {
        int n = route.getLength();
        if (n <= 3) return;

        for (int segmentLength = 1; segmentLength <= 3; segmentLength++) {
            double minCost = 1e9;
            List<Node> bestPath = null;
            for (int i = 0; i < n - segmentLength - 1; i++) {
                Node x1 = route.get(i), x2 = route.get(i + 1);
                int j = i + segmentLength;
                Node y1 = route.get(j), y2 = route.get(j + 1);
                for (int k = 0; k < n - 1; k++) {
                    if (k >= i && k <= j) continue;
                    Node z1 = route.get(k), z2 = route.get(k + 1);
                    // same cost function calculation for both cases (below)
                    // minimize the cost -> compute f(after) - f(before)
                    double cost = dataModel.dist(x1, y2) + dataModel.dist(z1, x2) + dataModel.dist(y1, z2)
                            - (dataModel.dist(x1, x2) + dataModel.dist(y1, y2) + dataModel.dist(z1, z2));

                    if (cost < minCost) {  // only construct the new route if the cost is better than minCost
                        // We defer the re-constructing route operation to optimize performance
                        List<Node> oldPath = route.routedPath;
                        List<Node> newPath = new ArrayList<>();

                        // Customers from x2 to y1 would be moved from its current position, between x1 andy2,
                        // to position between customers z1 and z2.
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
                        if (Utils.checkRoutedPathFeasibility(dataModel, newPath)) {
                            minCost = cost;
                            bestPath = newPath;
                        }
                    }
                }
            }
            if (bestPath != null) {
                route.routedPath = bestPath;
                route.initializeVariables();
            }
        }
    }

    /**
     * Run Or-opt algorithm to optimize a route.
     * Acceptance criterion is set to first-improving move:
     *      Shortens the tour by repeating Segment Shift moves for segment
     *      length equal 1, 2, 3 until no improvement can by done:
     *      in every iteration immediately makes permanent the first move found that gives any length gain.
     * Only accept moves that gain more than EPSILON to save runtime.
     * @param route input route
     * @param dataModel
     */
    static void orOptFirstImproving(Route route, DataModel dataModel) {
        int n = route.getLength();
        if (n <= 3) return;
        boolean localOptimal = false;

        whileLoop:
        while (!localOptimal) {
            localOptimal = true;

            for (int segmentLength = 1; segmentLength <= 3; segmentLength++) {
                for (int i = 0; i < n - segmentLength - 1; i++) {
                    Node x1 = route.get(i), x2 = route.get(i + 1);
                    int j = i + segmentLength;
                    Node y1 = route.get(j), y2 = route.get(j + 1);
                    for (int k = 0; k < n - 1; k++) {
                        if (k >= i && k <= j) continue;
                        Node z1 = route.get(k), z2 = route.get(k + 1);
                        // same cost function calculation for both cases (below)
                        // maximize the gain = dist(before) - dist(after)
                        double gain = dataModel.dist(x1, x2) + dataModel.dist(y1, y2) + dataModel.dist(z1, z2)
                                - (dataModel.dist(x1, y2) + dataModel.dist(z1, x2) + dataModel.dist(y1, z2));
                        if (gain > EPSILON) {  // to reduce runtime, only accept move if its gain > EPSILON
                            List<Node> oldPath = route.routedPath;
                            List<Node> newPath = new ArrayList<>();

                            // Customers from x2 to y1 would be moved from its current position, between x1 andy2,
                            // to position between customers z1 and z2.
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
                            if (Utils.checkRoutedPathFeasibility(dataModel, newPath)) {
                                route.routedPath = newPath;
                                route.initializeVariables();
                                localOptimal = false;
                                continue whileLoop;
                            }
                        }
                    }
                }
            }
        }
    }
}
