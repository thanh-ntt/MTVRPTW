import java.util.*;
import java.util.stream.Collectors;

/**
 * A modification of the Solomon's I1 insertion heuristic (Solomon, 1987).
 * Here, we modify the insertion heuristic to take advantage of the multi-trip nature of the MTVRPTW.
 */
public class SolomonI1Algorithm implements SolutionConstructionAlgorithm {
    static final Parameter[] PARAMETERS = {new Parameter(1, 1, 1, 0), new Parameter(1, 2, 1, 0),
        new Parameter(1, 1, 0, 1), new Parameter(1, 2, 0, 1)};
//    static final Logger logger = Logger.getLogger(MTVRPTW.class.getName());

    @Override
    public List<Route> run(DataModel dataModel) {
        List<Route> solution = runAllInitializationCriteria(dataModel);
        assert Utils.isValidSolution(dataModel, solution);
        return solution;
    }

    /**
     * Here we try different initialization criteria as suggested by Solomon:
     *  1. Farthest un-routed customer
     *  2. Un-routed customer with earliest deadline
     */
    List<Route> runAllInitializationCriteria(DataModel dataModel) {
        List<Node> firstOrderedCustomers = new ArrayList<>(dataModel.getDemandNodes());
        firstOrderedCustomers.sort((a, b) -> Double.compare(dataModel.getDistanceFromDepot(b), dataModel.getDistanceFromDepot(a)));

        List<Node> secondOrderedCustomers = new ArrayList<>(dataModel.getDemandNodes());
        secondOrderedCustomers.sort(Comparator.comparingInt(a -> a.dueTime));

//        List<Node> thirdOrderedCustomers = new ArrayList<>(dataModel.getDemandNodes());
//        Collections.shuffle(thirdOrderedCustomers);

        List<List<Node>> customerSets = new ArrayList<>(Arrays.asList(firstOrderedCustomers, secondOrderedCustomers));
        List<List<Route>> solutions = customerSets.stream().map(orderedCustomers -> run(orderedCustomers, dataModel.getDepot().readyTime, dataModel)).collect(Collectors.toList());

        List<Route> bestSolution = null;
        for (List<Route> solution : solutions) {
            if (bestSolution == null || solution.size() < bestSolution.size())bestSolution = solution;
        }
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
        for (Parameter parameter : PARAMETERS) {
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

            route.addDummyDepot();  // enable making multiple trips

            CustomerPosition bestCustomerPosition = getBestCustomerAndPosition(route, unRoutedCustomers, dataModel, parameter);
            while (bestCustomerPosition != null) {  // loop until infeasible to insert any more customers
                Node bestCustomer = bestCustomerPosition.node;
                int insertPosition = bestCustomerPosition.position;

                // Remove customer from un-routed set and insert into the route
                unRoutedCustomers.remove(bestCustomer);
                route.insertAtPosition(insertPosition, bestCustomer);

                bestCustomerPosition = getBestCustomerAndPosition(route, unRoutedCustomers, dataModel, parameter);
            }

            route.removeDummyDepot();

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
    static CustomerPosition getBestCustomerAndPosition(Route route, List<Node> unRoutedCustomers,
                                                       DataModel dataModel, Parameter parameter) {
        CustomerPosition result = null;
        Double maxC2 = null;
        for (Node customer : unRoutedCustomers) {
            ValueAndPosition cur = getC2ValueAndPosition(route, customer, dataModel, parameter);
            if (cur != null && (maxC2 == null || cur.value > maxC2)) {
                maxC2 = cur.value;
                result = new CustomerPosition(customer, cur.position);
            }
        }
        return result;
    }

    /**
     * Get the best feasible insertion position of the customer u on the route.
     *
     * @return insertion cost or null if it's not feasible to insert this customer into the route.
     */
    public static ValueAndPosition getC2ValueAndPosition(Route route, Node u, DataModel dataModel, Parameter parameter) {
        assert !route.routedPath.contains(u);
        ValueAndPosition minC1 = null;

        for (int p = 1; p < route.getLength(); p++) {
            Double curCost = computeC1InsertionCost(route, u, p, dataModel, parameter);
            if (curCost != null && (minC1 == null || curCost < minC1.value)) {
                minC1 = new ValueAndPosition(curCost, p);
            }
        }
        if (minC1 == null) return null;
        double d0u = dataModel.getDistanceFromDepot(u);
        double c2 = parameter.lambda * d0u - minC1.value;
        return new ValueAndPosition(c2, minC1.position);
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
