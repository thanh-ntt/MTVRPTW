import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * The algorithm is inspired by the greedy nearest neighbor algorithm for VRP.
 * In this case, a new customer is inserted into the route based on
 * the ready time of the vehicle after serving the customer.
 *
 * If no feasible customer exists, the vehicle will go back to the depot,
 * then try to find a new customer for the new trip.
 *
 * If there is still no feasible customer, initialize a new vehicle (a new route).
 *
 */
public class Greedy implements ConstructionAlgorithm {
    DataModel dataModel;

    @Override
    public List<Route> run(DataModel dataModel) {
        this.dataModel = dataModel;
        List<Route> solution = run();
        assert Utils.isValidSolution(dataModel, solution);
        return solution;
    }

    public List<Route> run() {
        List<Route> solution = new ArrayList<>();
        Set<Node> unRoutedCustomers = dataModel.getDemandNodes();
        while (!unRoutedCustomers.isEmpty()) {
            Route route = new Route(dataModel);
            recursivelyAddCustomer(unRoutedCustomers, route);
            assert route.getLength() > 1;
            solution.add(route);
        }
        return solution;
    }

    /**
     * Recursively add new trip to the end of the route.
     * Each trip is constructed by iteratively adding best (greedy) customer to the end of the route.
     * @param unRoutedCustomers
     * @param route
     */
    void recursivelyAddCustomer(Set<Node> unRoutedCustomers, Route route) {
        Node u = findBestFeasibleCustomer(unRoutedCustomers, route);
        if (u == null) return;  // can no longer add customer to this route
        while (u != null) {
            route.appendAtLastPosition(u);  // insert u at the end of the current route
            unRoutedCustomers.remove(u);

            u = findBestFeasibleCustomer(unRoutedCustomers, route);
        }
        // Come back to depot to close the trip
        route.appendAtLastPosition(dataModel.getDepot());
        // Try to add new trip
        recursivelyAddCustomer(unRoutedCustomers, route);
    }

    public Node findBestFeasibleCustomer(Set<Node> unRoutedCustomers, Route route) {
        Node bestCustomer = null;
        double earliestTime = 1e9;  // Earliest time the vehicle can continue the trip, initially set to infinity
        for (Node u : unRoutedCustomers) {
            if (route.checkCapacityConstraint(route.getLength() - 1, u.demand)) {
                Node prevCustomer = route.get(route.getLength() - 1);
                double arrivalTimeAtCustomer = route.getStartingServiceTimeAt(route.getLength() - 1)
                        + prevCustomer.serviceTime + dataModel.dist(prevCustomer, u);
                double startingServiceTime = Math.max(arrivalTimeAtCustomer, u.readyTime);
                double endingServiceTime = startingServiceTime + u.serviceTime;
                // Also need to check if the vehicle could return to depot in time
                double arrivalTimeAtDepot = endingServiceTime + dataModel.distFromDepot(u);
                if (arrivalTimeAtCustomer <= u.dueTime
                        && arrivalTimeAtDepot <= dataModel.getDepot().dueTime
                        && endingServiceTime < earliestTime) {
                    earliestTime = arrivalTimeAtCustomer;
                    bestCustomer = u;
                }
            }
        }
        return bestCustomer;
    }
}
