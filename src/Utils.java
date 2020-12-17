import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Utils {
    private final static double EPSILON = 0.00001;
    private final static DecimalFormat df = new DecimalFormat("0.00");

    public static String getSolutionStats(List<Route> routes, boolean showRouteStats) {
        StringBuilder sb = new StringBuilder();
        sb.append("Total # vehicles: " + routes.size() + "\n");
        if (showRouteStats) {
            for (int i = 0; i < routes.size(); i++) {
                sb.append("   Vehicle #" + (i + 1) + ":\n");
                sb.append(getRouteStats(routes.get(i)));
            }
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
        // Serve each customer exactly once
        for (Route route : routes) {
            for (Node c : route.routedPath) {
                // No customer is served more than once
                if (c != dataModel.getDepot() && !unServedCustomers.remove(c)) return false;
            }
        }
        // All customers are served
        if (!unServedCustomers.isEmpty()) return false;
        // Each route is valid (w.r.t capacity and time constraint)
        if (!routes.stream().allMatch(route -> Utils.isValidRoute(dataModel, route))) return false;

        return true;
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
        routedCustomers.remove(routes.get(0).depot);  // remove depot
        return routedCustomers;
    }

    /**
     * Check if we can exchange 2 nodes at position p1, p2 in route r1, r2
     */
    public static boolean checkExchangeOperator(DataModel dataModel, Route r1, int p1, Route r2, int p2) {
        Node u1 = r1.getCustomerAt(p1);
        Node u2 = r2.getCustomerAt(p2);
        int vehicleCapacity = dataModel.getVehicleCapacity();

        // Check capacity constraint
        if (!r1.checkCapacityConstraint(p1, u2.demand - u1.demand)
                || !r2.checkCapacityConstraint(p2, u1.demand - u2.demand)) {
            return false;
        }

        // Check time constraint
        Node prev1 = r1.getCustomerAt(p1 - 1), next1 = r1.getCustomerAt(p1 + 1);
        Node prev2 = r2.getCustomerAt(p2 - 1), next2 = r2.getCustomerAt(p2 + 1);

        // Check route r1
        double newArrivalTimeAtP1 = r1.getStartingServiceTimeAt(p1 - 1) + prev1.serviceTime + dataModel.getDistance(prev1, u2);
        double newServiceTimeAtP1 = Math.max(newArrivalTimeAtP1, u2.readyTime);
        if (Utils.greaterThan(newServiceTimeAtP1, u2.dueTime)) return false;
        double newServiceTimeAtNext1 = Math.max(newServiceTimeAtP1 + u2.serviceTime + dataModel.getDistance(u2, next1), next1.readyTime);
        double pushForwardAtNext1 = newServiceTimeAtNext1 - r1.getStartingServiceTimeAt(p1 + 1);
        if (!r1.checkPushForwardTimeFromPosition(pushForwardAtNext1, p1 + 1)) return false;

        // Check route r2
        double newArrivalTimeAtP2 = r2.getStartingServiceTimeAt(p2 - 1) + prev2.serviceTime + dataModel.getDistance(prev2, u1);
        double newServiceTimeAtP2 = Math.max(newArrivalTimeAtP2, u1.readyTime);
        if (Utils.greaterThan(newServiceTimeAtP2, u1.dueTime)) return false;
        double newServiceTimeAtNext2 = Math.max(newServiceTimeAtP2 + u1.serviceTime + dataModel.getDistance(u1, next2), next2.readyTime);
        double pushForwardAtNext2 = newServiceTimeAtNext2 - r2.getStartingServiceTimeAt(p2 + 1);
        if (!r2.checkPushForwardTimeFromPosition(pushForwardAtNext2, p2 + 1)) return false;

        return true;
    }

    /**
     * Get cost of an exchange operator.
     * This cost function is inspired by Solomon's I1 insertion heuristic,
     * where we take into account both the distance and push-forward time incurred by the exchange.
     */
    public static double getCostExchangeOperator(DataModel dataModel, Route r1, int p1, Route r2, int p2) {
        Node u1 = r1.getCustomerAt(p1), prev1 = r1.getCustomerAt(p1 - 1), next1 = r1.getCustomerAt(p1 + 1);
        Node u2 = r2.getCustomerAt(p2), prev2 = r2.getCustomerAt(p2 - 1), next2 = r2.getCustomerAt(p2 + 1);
        double distanceCost = (dataModel.getDistance(prev1, u2) + dataModel.getDistance(u2, next1) + dataModel.getDistance(prev2, u1) + dataModel.getDistance(u1, next2))
                - (dataModel.getDistance(prev1, u1) + dataModel.getDistance(u1, next1) + dataModel.getDistance(prev2, u2) + dataModel.getDistance(u2, next2));

        double newArrivalTimeAtP1 = r1.getStartingServiceTimeAt(p1 - 1) + prev1.serviceTime + dataModel.getDistance(prev1, u2);
        double newServiceTimeAtP1 = Math.max(newArrivalTimeAtP1, u2.readyTime);
        double newServiceTimeAtNext1 = Math.max(newServiceTimeAtP1 + u2.serviceTime + dataModel.getDistance(u2, next1), next1.readyTime);
        double pushForwardAtNext1 = newServiceTimeAtNext1 - r1.getStartingServiceTimeAt(p1 + 1);

        double newArrivalTimeAtP2 = r2.getStartingServiceTimeAt(p2 - 1) + prev2.serviceTime + dataModel.getDistance(prev2, u1);
        double newServiceTimeAtP2 = Math.max(newArrivalTimeAtP2, u1.readyTime);
        double newServiceTimeAtNext2 = Math.max(newServiceTimeAtP2 + u1.serviceTime + dataModel.getDistance(u1, next2), next2.readyTime);
        double pushForwardAtNext2 = newServiceTimeAtNext2 - r2.getStartingServiceTimeAt(p2 + 1);

        // Total push-forward in time
        double timeCost = pushForwardAtNext1 + pushForwardAtNext2;

        double cost = distanceCost * dataModel.configs.distanceRatio + timeCost * dataModel.configs.timeRatio;
        return cost;
    }

    /**
     * Gain of relocating (inserting) customer at index p1 of route r1
     * into position p2 of route r2.
     */
//    public static double getCostRelocateOperator(DataModel dataModel, Route r1, int p1, Route r2, int p2) {
//        Node u1 = r1.getCustomerAt(p1), prev1 = r1.getCustomerAt(p1 - 1), next1 = r1.getCustomerAt(p1 + 1);
//        Node next2 = r2.getCustomerAt(p2), prev2 = r2.getCustomerAt(p2 - 1);
//        double distanceCost = (dataModel.getTravelTime())
//    }

    /**
     * Optimize the current route: make the vehicle leave the depot as late as possible (primary objective),
     * but serves each customer as early as possible (secondary objective).
     * Steps:
     *  1. Start with a feasible route
     *  2. From end to start: make all arrival time of all nodes (including depot) as late as possible
     *  3. Calculate new (latest) time to leave depot
     *  4. From first customer (excluding depot) to end: make all arrival time of all customers as early as possible
     *
     * O(n) operation - expensive, should only run once for post-optimization,
     * should not include in the local search algorithm.
     */
    public static void optimizeRoute(DataModel dataModel, Route route) {
        // Make all arrival time of all customers (include the depot) as late as possible
        route.arrivalTimes.set(route.arrivalTimes.size() - 1, (double) route.depot.dueTime);
        for (int i = route.arrivalTimes.size() - 2; i >= 0; i--) {
            Node customer = route.routedPath.get(i);
            // latest arrival time at customer so that the following customer can be served no later than its current starting service time
            double latestArrivalTime = route.getStartingServiceTimeAt(i + 1) - dataModel.getDistance(customer, route.routedPath.get(i + 1)) - customer.serviceTime;
            // ensure that the route remains valid (no customer is served after time window ends)
            route.arrivalTimes.set(i, Math.min(customer.dueTime, latestArrivalTime));
        }

        for (int i = 1; i < route.arrivalTimes.size(); i++) {
            Node prevCustomer = route.routedPath.get(i - 1);
            Node customer = route.routedPath.get(i);
            route.arrivalTimes.set(i, route.getStartingServiceTimeAt(i - 1) + prevCustomer.serviceTime + dataModel.getDistance(prevCustomer, customer));
        }
        assert isValidRoute(dataModel, route);
    }

    public static boolean isValidRoute(DataModel dataModel, Route route) {
        if (route.routedPath.get(0) != route.depot || route.routedPath.get(route.routedPath.size() - 1) != route.depot) {
            return false;
        }

        // Check capacity and time constraint
        int curVehicleLoad = 0;
        for (int i = 0; i < route.routedPath.size() - 1; i++) {
            Node customer = route.routedPath.get(i);
            curVehicleLoad += customer.demand;
            if (curVehicleLoad > dataModel.getVehicleCapacity()) {
                return false;
            }
            if (customer == dataModel.getDepot()) curVehicleLoad = 0;
            // Use double comparison with epsilon to tackle rounding
            if (Utils.greaterThan(route.arrivalTimes.get(i), customer.dueTime)
                    || !Utils.equals(route.getStartingServiceTimeAt(i) + customer.serviceTime
                    + dataModel.getDistance(customer, route.routedPath.get(i + 1)), route.arrivalTimes.get(i + 1))
            ) {
                return false;
            }
        }
        // Arrives at depot on time
        if (Utils.greaterThan(route.getLatestArrivalTimeAtDepot(), dataModel.getDepot().dueTime)) {
            return false;
        }

        return true;
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

class ValueAndPosition {
    double value;
    int position;
    public ValueAndPosition(double c, int p) {
        value = c;
        position = p;
    }
}

class CustomerPosition {
    Node node;
    int position;
    public CustomerPosition(Node n, int p) {
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
