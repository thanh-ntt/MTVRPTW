import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SolomonI1Algorithm implements SolutionConstructionAlgorithm {
    DataModel dataModel;
    static final Logger logger = Logger.getLogger(MTVRPTW.class.getName());

    @Override
    public List<Route> run(DataModel dataModel) {
        this.dataModel = dataModel;
        List<Route> solution = run();
        assert Utils.isValidSolution(dataModel, solution);
        return solution;
    }

    /**
     * I1 insertion heuristic proposed by Solomon, 1987.
     */
    public List<Route> run() {
        List<Node> customers = new ArrayList<>(dataModel.getDemandNodes());
        // TODO: make this an input configuration
//        customers.sort((a, b) -> Double.compare(dataModel.getDistanceFromDepot(b), dataModel.getDistanceFromDepot(a)));
        customers.sort(Comparator.comparingInt(a -> a.readyTime));
        return run(customers, 0, dataModel);
    }

    /**
     * Run I1 insertion heuristic to route the list of un-routed customers
     *
     * @param orderedCustomers list of un-routed customer, ordered by some criteria
     * @param departureTimeFromDepot
     * @return
     */
    public List<Route> run(List<Node> orderedCustomers, double departureTimeFromDepot, DataModel dataModel) {
        this.dataModel = dataModel;
        List<Node> unRoutedCustomers = new ArrayList<>(orderedCustomers);
        List<Route> routes = new ArrayList<>();
        // Apply Solomon's sequential insertion heuristic
        do {
            // Seed can be the customer with the furthest (geographically) from depot or the earliest ready time
            Node seed = unRoutedCustomers.remove(0);
            // Initialize the route to (depot, seed, depot)
            Route route = new Route(dataModel, seed, departureTimeFromDepot);
            NodePositionPair bestCustomerAndPosition = getBestCustomerAndPosition(route, unRoutedCustomers);
            while (bestCustomerAndPosition != null) {  // loop until infeasible to insert any more customers
                Node bestCustomer = bestCustomerAndPosition.node;
                int insertPosition = bestCustomerAndPosition.position;

                // Remove customer from un-routed set and insert into the route
                unRoutedCustomers.remove(bestCustomer);
                route.insertAtPosition(insertPosition, bestCustomer);

                bestCustomerAndPosition = getBestCustomerAndPosition(route, unRoutedCustomers);
            }
            routes.add(route);
        } while (!unRoutedCustomers.isEmpty());

        return routes;
    }


    /**
     * Get the best customer to be inserted in the route, and the position to be inserted
     */
    NodePositionPair getBestCustomerAndPosition(Route route, List<Node> unRoutedCustomers) {
        NodePositionPair result = null;
        Double minCost = null;
        for (Node customer : unRoutedCustomers) {
            CostPositionPair cur = getBestInsertionCostAndPosition(route, customer);
            if (cur != null && (minCost == null || cur.cost < minCost)) {
                minCost = cur.cost;
                result = new NodePositionPair(customer, cur.position);
            }
        }
        return result;
    }

    /**
     * Get the best feasible insertion cost of the customer u on the route.
     * Due to time limit, we only explore p-neighbourhood, value of p-neighbourhood is read from parameters.txt file.
     * @return insertion cost or null if it's not feasible to insert this customer into the route.
     */
     public CostPositionPair getBestInsertionCostAndPosition(Route route, Node u) {
        assert !route.routedPath.contains(u);
        CostPositionPair result = null;

        // to save time, only consider p-neighbourhood of u (new customer)
        // this is the set of p nodes on the route closest (distance/time) to u
        List<Integer> pNeighbourhood = new ArrayList<>();
        if (route.getLength() - 2 <= dataModel.pNeighbourhoodSize) {  // excluding the depot (depot appears twice in any routes)
            for (int i = 1; i < route.getLength(); i++)
                pNeighbourhood.add(i);
        } else {
            List<Integer> orderedPositions = IntStream.rangeClosed(1, route.getLength() - 1).boxed().collect(Collectors.toList());
            orderedPositions.sort(Comparator.comparingDouble(i -> dataModel.getTravelTime(route.routedPath.get(i), u)));
            pNeighbourhood.addAll(orderedPositions.subList(0, dataModel.pNeighbourhoodSize));
        }

        for (int p : pNeighbourhood) {
            Double cost = getInsertionCost(route, u, p);
            if (cost != null && (result == null || result.cost < cost)) {
                result = new CostPositionPair(cost, p);
            }
        }
        return result;
    }

    /**
     * Get the cost of inserting new customer u between i(p-1) and ip
     * -> Route before insertion: (i0, ..., i(p-1), ip, ..., i0)
     * -> Route after insertion: (i0, ..., i(p-1), u, ip, ..., i0)
     * @return insertion cost or null if it's not feasible to insert this customer into the position.
     */
    Double getInsertionCost(Route route, Node u, int p) {
        // Check capacity constraint and time constraint
        if (!route.canInsertAtPosition(p, u)) return null;

        // Route travel time increase, c11 in I1, Solomon, 1987
        double c11 = dataModel.getTravelTime(route.routedPath.get(p - 1), u) + dataModel.getTravelTime(u, route.routedPath.get(p)) - dataModel.getTravelTime(route.routedPath.get(p - 1), route.routedPath.get(p));
        // compute service push forward in service starting time at customer ip
        double c12 = route.getPushForwardTimeAtNextCustomer(u, p);
        // I1 insertion heuristic - Solomon, 1987
        return dataModel.alpha1 * c11 + dataModel.alpha2 * c12;
    }
}
