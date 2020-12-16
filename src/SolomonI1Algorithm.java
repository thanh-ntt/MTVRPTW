import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SolomonI1Algorithm implements SolutionConstructionAlgorithm {
    static Parameter[] parameters = {new Parameter(1, 1, 1, 0), new Parameter(1, 2, 1, 0),
        new Parameter(1, 1, 0, 1), new Parameter(1, 2, 0, 1)};
//    static final Logger logger = Logger.getLogger(MTVRPTW.class.getName());

    @Override
    public List<Route> run(DataModel dataModel) {
        List<Route> solution = runAllInitializationCriteria(dataModel);
        assert Utils.isValidSolution(dataModel, solution);
        return solution;
    }

    /**
     * I1 insertion heuristic proposed by Solomon, 1987.
     * Here we try different initialization criteria as suggested by Solomon:
     *  1. Farthest un-routed customer
     *  2. Un-routed customer with earliest deadline
     *
     */
    List<Route> runAllInitializationCriteria(DataModel dataModel) {
        List<Node> firstOrderedCustomers = new ArrayList<>(dataModel.getDemandNodes());
        firstOrderedCustomers.sort((a, b) -> Double.compare(dataModel.getDistanceFromDepot(b), dataModel.getDistanceFromDepot(a)));
        List<Route> firstSolution = run(firstOrderedCustomers, 0, dataModel);

        List<Node> secondOrderedCustomers = new ArrayList<>(dataModel.getDemandNodes());
        secondOrderedCustomers.sort(Comparator.comparingInt(a -> a.dueTime));
        List<Route> secondSolution = run(secondOrderedCustomers, 0, dataModel);

        List<Route> bestSolution = (firstSolution.size() < secondSolution.size()) ? firstSolution : secondSolution;
        return bestSolution;
    }

    /**
     * Run I1 insertion heuristic to route the list of un-routed customers.
     * <p>
     * This method is made static so that other algorithms can use this as a sub-routine.
     *
     * Here we try all parameter choices as suggested in Solomon, 1987
     *
     * @param orderedCustomers list of un-routed customer, ordered by some criteria
     * @param departureTimeFromDepot
     * @return
     */
    public static List<Route> run(List<Node> orderedCustomers, double departureTimeFromDepot, DataModel dataModel) {
        List<Route> bestSolution = null;
        for (Parameter parameter : parameters) {
            List<Route> solution = runWithParameter(orderedCustomers, departureTimeFromDepot, dataModel, parameter);
            if (bestSolution == null || solution.size() < bestSolution.size()) {
                bestSolution = solution;
            }
        }
        return bestSolution;
    }

    public static List<Route> runWithParameter(List<Node> orderedCustomers, double departureTimeFromDepot,
                                               DataModel dataModel, Parameter parameter) {
        List<Node> unRoutedCustomers = new ArrayList<>(orderedCustomers);
        List<Route> routes = new ArrayList<>();
        // Apply Solomon's sequential insertion heuristic
        do {
            // Seed can be the customer based on the ordering
            Node seed = unRoutedCustomers.remove(0);
            // Initialize the route to (depot, seed, depot)
            Route route = new Route(dataModel, seed, departureTimeFromDepot);
            NodePositionPair bestCustomerAndPosition = getBestCustomerAndPosition(route, unRoutedCustomers, dataModel, parameter);
            while (bestCustomerAndPosition != null) {  // loop until infeasible to insert any more customers
                Node bestCustomer = bestCustomerAndPosition.node;
                int insertPosition = bestCustomerAndPosition.position;

                // Remove customer from un-routed set and insert into the route
                unRoutedCustomers.remove(bestCustomer);
                route.insertAtPosition(insertPosition, bestCustomer);

                bestCustomerAndPosition = getBestCustomerAndPosition(route, unRoutedCustomers, dataModel, parameter);
            }
            routes.add(route);
        } while (!unRoutedCustomers.isEmpty());

        return routes;
    }

    /**
     * Get the best customer to be inserted in the route, and the position to be inserted.
     * Details in Solomon, 1987
     * <p>
     * c2(i(u*), u*, j(u*)) = max[c2(i(u), u, j(u))], u un-routed and feasible
     * <p>
     * c2 = lambda * d0u - c1(i, u, j)
     * is the benefit derived from servicing a customer on the partial route being constructed,
     * rather than on a direct route.
     */
    static NodePositionPair getBestCustomerAndPosition(Route route, List<Node> unRoutedCustomers,
                                                       DataModel dataModel, Parameter parameter) {
        NodePositionPair result = null;
        Double maxC2 = null;
        for (Node customer : unRoutedCustomers) {
            c2AndPosition cur = getC2ValueAndPosition(route, customer, dataModel, parameter);
            if (cur != null && (maxC2 == null || cur.value > maxC2)) {
                maxC2 = cur.value;
                result = new NodePositionPair(customer, cur.position);
            }
        }
        return result;
    }

    /**
     * Get the best feasible insertion position of the customer u on the route.
     *
     * @return insertion cost or null if it's not feasible to insert this customer into the route.
     */
    public static c2AndPosition getC2ValueAndPosition(Route route, Node u, DataModel dataModel, Parameter parameter) {
        assert !route.routedPath.contains(u);
        c2AndPosition minInsertionPosition = null;

        for (int p = 1; p < route.getLength(); p++) {
            Double curCost = computeC1InsertionCost(route, u, p, dataModel, parameter);
            if (curCost != null && (minInsertionPosition == null || curCost < minInsertionPosition.value)) {
                minInsertionPosition = new c2AndPosition(curCost, p);
            }
        }
        if (minInsertionPosition == null) return null;
        double d0u = dataModel.getDistanceFromDepot(u);
        double c2 = parameter.lambda * d0u - minInsertionPosition.value;
        return new c2AndPosition(c2, minInsertionPosition.position);
    }

    /**
     * Get the cost of inserting new customer u between i(p-1) and ip, or u between i(u) and j(u)
     * -> Route before insertion: (i0, ..., i(p-1), ip, ..., i0)
     * -> Route after insertion: (i0, ..., i(p-1), u, ip, ..., i0)
     *
     * @return insertion cost or null if it's not feasible to insert this customer into the position.
     */
    static Double computeC1InsertionCost(Route route, Node u, int p, DataModel dataModel, Parameter parameter) {
        // Check capacity constraint and time constraint
        if (!route.canInsertAtPosition(p, u)) return null;

        double diu = dataModel.getTravelTime(route.routedPath.get(p - 1), u);
        double duj = dataModel.getTravelTime(u, route.routedPath.get(p));
        double dij = dataModel.getTravelTime(route.routedPath.get(p - 1), route.routedPath.get(p));

        // Route travel time increase, c11 in I1, Solomon, 1987
        double c11 = diu + duj - parameter.mu * dij;
        // compute service push forward in service starting time at customer ip, this is same as (bju - bj)
        double c12 = route.getPushForwardTimeAtNextCustomer(u, p);
        // I1 insertion heuristic - Solomon, 1987
        double c1 = parameter.alpha1 * c11 + parameter.alpha2 * c12;
        return c1;
    }
}

class Parameter {
    double mu, lambda, alpha1, alpha2;
    public Parameter(double a, double b, double c, double d) {
        mu = a;
        lambda = b;
        alpha1 = c;
        alpha2 = d;
    }

    // Default parameters
    public Parameter() {
        mu = 1;
        lambda = 2;
        alpha1 = 0;
        alpha2 = 1;
    }
}
