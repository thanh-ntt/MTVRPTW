import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Utils {
    private final static double EPSILON = 0.00001;
    private final static DecimalFormat df = new DecimalFormat("0.0");

    public static String getSolutionStats(List<Route> routes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Total # vehicles: " + routes.size() + "\n");
        for (int i = 0; i < routes.size(); i++) {
            sb.append("   Vehicle #" + (i + 1) + ":\n");
            sb.append(getRouteStats(routes.get(i)));
        }
        return sb.toString();
    }

    public static String getRouteStats(Route route) {
        StringBuilder sb = new StringBuilder();
        sb.append("     Path: " + Arrays.toString(route.routedPath.toArray()) + "\n");
        sb.append("     Arrival time: " + Arrays.toString(route.arrivalTimes.stream().map(df::format).toArray()) + "\n");
        return sb.toString();
    }

    /**
     * Get a deep copy of a solution where modification of routes in the newly created solution does not
     * affect the original solution.
     *
     * @param solution
     * @return
     */
    public static List<Route> deepCopySolution(List<Route> solution) {
        return solution.stream().map(Route::new).collect(Collectors.toList());
    }

    public static boolean isValidSolution(DataModel dataModel, List<Route> routes) {
        Set<Node> unServedCustomers = new HashSet<>(dataModel.getDemandNodes());
        for (Route route : routes) {
            for (Node c : route.routedPath) {
                if (c != dataModel.getDepot() && !unServedCustomers.remove(c)) return false;
            }
        }
        return unServedCustomers.isEmpty();
    }

    public static boolean isParallelRoutesValid(DataModel dataModel, List<List<Route>> parallelRoutes) {
        Set<Node> unServedCustomers = new HashSet<>(dataModel.getDemandNodes());
        for (List<Route> routes : parallelRoutes) {
            for (Route route : routes) {
                for (Node c : route.routedPath) {
                    if (c != dataModel.getDepot() && !unServedCustomers.remove(c)) return false;
                }
            }
        }
        return unServedCustomers.isEmpty();
    }

    public static Set<Node> getRoutedCustomers(List<Route> routes) {
        Set<Node> routedCustomers = new HashSet<>();
        for (Route route : routes)
            routedCustomers.addAll(route.routedPath);
        routedCustomers.remove(routes.get(0).routedPath.get(0));  // remove depot
        return routedCustomers;
    }

    /**
     * Returns true if two doubles are considered equal.  Tests if the absolute
     * difference between two doubles has a difference less then .00001.   This
     * should be fine when comparing prices, because prices have a precision of
     * .001.
     *
     * @param a double to compare.
     * @param b double to compare.
     * @return true true if two doubles are considered equal.
     */
    public static boolean equals(double a, double b){
        return a == b ? true : Math.abs(a - b) < EPSILON;
    }


    /**
     * Returns true if two doubles are considered equal. Tests if the absolute
     * difference between the two doubles has a difference less then a given
     * double (epsilon). Determining the given epsilon is highly dependant on the
     * precision of the doubles that are being compared.
     *
     * @param a double to compare.
     * @param b double to compare
     * @param epsilon double which is compared to the absolute difference of two
     * doubles to determine if they are equal.
     * @return true if a is considered equal to b.
     */
    public static boolean equals(double a, double b, double epsilon){
        return a == b ? true : Math.abs(a - b) < epsilon;
    }


    /**
     * Returns true if the first double is considered greater than the second
     * double.  Test if the difference of first minus second is greater then
     * .00001.  This should be fine when comparing prices, because prices have a
     * precision of .001.
     *
     * @param a first double
     * @param b second double
     * @return true if the first double is considered greater than the second
     *              double
     */
    public static boolean greaterThan(double a, double b){
        return greaterThan(a, b, EPSILON);
    }

    public static List<Route> getBestSolution(List<List<Route>> solutions) {
        List<Route> bestSolution = null;
        for (List<Route> solution : solutions)
            if (bestSolution == null || solution.size() < bestSolution.size()) bestSolution = solution;
        return bestSolution;
    }


    /**
     * Returns true if the first double is considered greater than the second
     * double.  Test if the difference of first minus second is greater then
     * a given double (epsilon).  Determining the given epsilon is highly
     * dependant on the precision of the doubles that are being compared.
     *
     * @param a first double
     * @param b second double
     * @return true if the first double is considered greater than the second
     *              double
     */
    public static boolean greaterThan(double a, double b, double epsilon){
        return a - b > epsilon;
    }


    /**
     * Returns true if the first double is considered less than the second
     * double.  Test if the difference of second minus first is greater then
     * .00001.  This should be fine when comparing prices, because prices have a
     * precision of .001.
     *
     * @param a first double
     * @param b second double
     * @return true if the first double is considered less than the second
     *              double
     */
    public static boolean lessThan(double a, double b){
        return lessThan(a, b, EPSILON);
    }


    /**
     * Returns true if the first double is considered less than the second
     * double.  Test if the difference of second minus first is greater then
     * a given double (epsilon).  Determining the given epsilon is highly
     * dependant on the precision of the doubles that are being compared.
     *
     * @param a first double
     * @param b second double
     * @return true if the first double is considered less than the second
     *              double
     */
    public static boolean lessThan(double a, double b, double epsilon){
        return b - a > epsilon;
    }
}

class CostPositionPair {
    double cost;
    int position;
    public CostPositionPair(double c, int p) {
        cost = c;
        position = p;
    }
}

class NodePositionPair {
    Node node;
    int position;
    public NodePositionPair(Node n, int p) {
        node = n;
        position = p;
    }
}

class RoutesOptimizationResult {
    List<Route> routes;
    Collection<Node> unRoutedCustomers;
    public RoutesOptimizationResult(List<Route> routes, Collection<Node> unRoutedCustomers) {
        this.routes = routes;
        this.unRoutedCustomers = unRoutedCustomers;
    }
}

class RoutePositionPair {
    Route route;
    int position;
    public RoutePositionPair(Route route, int p) {
        this.route = route;
        position = p;
    }
}
