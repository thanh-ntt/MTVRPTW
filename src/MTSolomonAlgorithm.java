import java.util.*;
import java.util.stream.Collectors;

/**
 * A sequential insertion heuristic for Multi-trip VRP with Time Window.
 * This algorithm is inspired by Solomon's I1 insertion heuristic (Solomon, 1987).
 */
public class MTSolomonAlgorithm implements ConstructionAlgorithm {
    // We use 6 sets of parameters, similar to Solomon, 1987
    // Each parameter set is used to compute cost function in I1 insertion heuristic
    static final Parameter[] PARAMETERS = {new Parameter(1, 1, 0), new Parameter(2, 1, 0), new Parameter(1, 0, 1),
            new Parameter(2, 0, 1), new Parameter(1, 0.5, 0.5), new Parameter(2, 0.5, 0.5)};

    /**
     * First we try different initialization criteria as suggested by Solomon:
     *  1. Farthest un-routed customer
     *  2. Un-routed customer with earliest deadline
     *  Then we take the best solution from these 2 initialization criteria.
     */
    @Override
    public List<Route> run(DataModel dataModel) {
        Set<Node> allCustomers = dataModel.getDemandNodes();

        List<Node> orderedByDistance = new ArrayList<>(allCustomers);
        orderedByDistance.sort((a, b) -> Double.compare(dataModel.distFromDepot(b), dataModel.distFromDepot(a)));

        List<Node> orderedByDeadline = new ArrayList<>(allCustomers);
        orderedByDeadline.sort(Comparator.comparingInt(a -> a.dueTime));

        List<List<Node>> orderedCustomerSets = new ArrayList<>(Arrays.asList(orderedByDistance, orderedByDeadline));
        List<List<Route>> solutions = orderedCustomerSets.stream()
                .map(orderedCustomerSet -> run(orderedCustomerSet, dataModel.getDepot().readyTime, dataModel))
                .collect(Collectors.toList());

        List<Route> bestSolution = null;
        for (List<Route> solution : solutions) {
            if (bestSolution == null || solution.size() < bestSolution.size()) bestSolution = solution;
        }
        assert Utils.isValidSolution(dataModel, bestSolution);
        return bestSolution;
    }

    /**
     * Run I1 sequential insertion heuristic to route the list of un-routed customers.
     * This method is made static so that other algorithms can use this as a sub-routine.
     *
     * Here we try 6 parameter choices as suggested in Solomon, 1987
     *
     * @param orderedCustomers list of un-routed customer, ordered by some criteria
     * @param departureTimeFromDepot to be used by other algorithms, set to 0 in MTSolomonAlgorithm
     * @return the best solution constructed from the parameter sets
     */
    public static List<Route> run(List<Node> orderedCustomers, double departureTimeFromDepot, DataModel dataModel) {
        return Arrays.stream(PARAMETERS)
                .map(parameter -> runWithParameter(orderedCustomers, departureTimeFromDepot, dataModel, parameter))
                .min(Comparator.comparingInt(List::size)).get();
    }

    /**
     * Run I1 sequential insertion heuristic for a specific parameter set.
     * @param orderedCustomers list of customers ordered by a specific criterion
     * @param departureTimeFromDepot to be used by other algorithms, default to 0
     * @param dataModel store all information related to the test case
     * @param parameter the parameter for cost function in I1 insertion heuristic
     * @return solution (list of route) constructed by the sequential insertion heuristic
     */
    public static List<Route> runWithParameter(List<Node> orderedCustomers, double departureTimeFromDepot,
                                               DataModel dataModel, Parameter parameter) {
        List<Node> unRoutedCustomers = new ArrayList<>(orderedCustomers);  // Avoid modifying the original list
        List<Route> solution = new ArrayList<>();
        do {
            // Seed node is the customer based on the ordering
            Node seed = unRoutedCustomers.remove(0);
            // Initialize the route to (depot, seed, depot)
            Route route = new Route(dataModel, seed, departureTimeFromDepot);

            CustomerPosition bestCustomerPosition = getBestCustomerAndPosition(route, unRoutedCustomers, dataModel, parameter);
            while (bestCustomerPosition != null) {  // loop until infeasible to insert any more customers
                Node bestCustomer = bestCustomerPosition.node;
                int insertPosition = bestCustomerPosition.position;

                // Remove customer from un-routed set and insert into the route
                unRoutedCustomers.remove(bestCustomer);
                route.insertAtPosition(insertPosition, bestCustomer);

                bestCustomerPosition = getBestCustomerAndPosition(route, unRoutedCustomers, dataModel, parameter);

                // Try to add dummy depot to make multiple trips if possible
                if (bestCustomerPosition == null) {
                    route.addDummyDepot();
                    bestCustomerPosition = getBestCustomerAndPosition(route, unRoutedCustomers, dataModel, parameter);
                    if (bestCustomerPosition == null) {  // Remove dummy depot if needed
                        route.removeDummyDepot();
                    }
                }
            }

            solution.add(route);
        } while (!unRoutedCustomers.isEmpty());

        return solution;
    }

    /**
     * Get the best customer to be inserted in the route, and the position to be inserted
     * based on evaluation function c2
     *
     * c2(i(u*), u*, j(u*)) = max[c2(i(u), u, j(u))], u un-routed and feasible
     * c2 = lambda * d0u - c1(i, u, j)
     *
     * is the benefit derived from servicing a customer on the partial route being constructed,
     * rather than on a direct route.
     *
     * @return the best customer to be inserted and its position in the route, or null if there is no feasible customer
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
//        assert !route.routedPath.contains(u);
        ValueAndPosition minC1 = null;

        for (int p = 1; p < route.getLength(); p++) {
            Double curCost = getC1InsertionCost(route, u, p, dataModel, parameter);
            if (curCost != null && (minC1 == null || curCost < minC1.value)) {
                minC1 = new ValueAndPosition(curCost, p);
            }
        }
        if (minC1 == null) return null;
        double d0u = dataModel.distFromDepot(u);
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
    static Double getC1InsertionCost(Route route, Node u, int p, DataModel dataModel, Parameter parameter) {
        // Check capacity constraint and time constraint
        if (!route.canInsertCustomerAt(p, u)) return null;
        Node i = route.get(p - 1), j = route.get(p);

        // Route travel time increase, c11 in I1, Solomon, 1987
        double c11 = dataModel.dist(i, u) + dataModel.dist(u, j) - dataModel.dist(i, j);
        // compute service push forward in service starting time at customer ip, this is same as (bju - bj)
        double c12 = route.getPushForwardTimeAfterInsertion(u, p);
        // I1 insertion heuristic - Solomon, 1987
        return parameter.alpha1 * c11 + parameter.alpha2 * c12;  // c1 cost function
    }
}
