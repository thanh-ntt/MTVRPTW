import java.util.*;

/**
 * This is "Solution" in paper, but we called it VehicleRouting to make it clear that
 * while the algorithm is running, this will just be an un-finished vehicle routing,
 * not an actual solution.
 */
public class VehicleRouting {
    DataModel dataModel;

    public VehicleRouting(DataModel dataModel) {
        this.dataModel = dataModel;
    }

    public void initialConstruction(int numClusters) {
        List<List<Node>> clusters = constructClusters(numClusters);

        // Apply parallel construction: construct routes for each cluster separately
        List<List<Route>> parallelRoutes = new ArrayList<>();
        for (List<Node> cluster : clusters) {
            List<Route> constructedRoute = constructRoute(cluster);
            parallelRoutes.add(constructedRoute);
        }
    }

    List<List<Node>> constructClusters(int numClusters) {
        List<List<Node>> clusters = new ArrayList<>();
        // 3.1.1 step 2: estimate ideal # vehicles needed in each cluster
        double averageDemandPerCluster = 1.0 * dataModel.getTotalDemands() / numClusters;
        int vehicleCapacity = dataModel.getVehicleCapacity();
        int idealNumVehicles = (int) Math.ceil(averageDemandPerCluster / vehicleCapacity);
        int idealDemandPerCluster = idealNumVehicles * vehicleCapacity;

        // 3.1.1 step 3: distribute demand nodes to clusters
        // Priority Queue ordered by latest service time
        PriorityQueue<Node> priorityQueue = new PriorityQueue<>(Comparator.comparingInt(a -> a.dueTime));
        priorityQueue.addAll(dataModel.getDemandNodes());
        while (!priorityQueue.isEmpty()) {
            List<Node> curCluster = new ArrayList<>();
            int curClusterDemand = 0;
            do {
                Node node = priorityQueue.poll();
                curCluster.add(node);
                curClusterDemand += node.demand;

                // If adding next node to the cluster results in exceeding average demand per cluster,
                // move the node to the next cluster
                if (!priorityQueue.isEmpty()
                        && curClusterDemand + priorityQueue.peek().demand > idealDemandPerCluster) {
                    break;
                }
            } while (!priorityQueue.isEmpty());

            // if this is the last cluster, just add everything
            if (clusters.size() == numClusters) {
                while (!priorityQueue.isEmpty())
                    curCluster.add(priorityQueue.poll());
            }

            assert !curCluster.isEmpty();
            clusters.add(curCluster);
        }

        return clusters;
    }

    /**
     * Construct the route for a cluster.
     * The objective is to minimize the number of vehicles needed.
     * Solomon's sequential insertion heuristic (I1):
     *      1. Initialize a route with seed
     *      2. Loop until cannot insert any more customers:
     *          a. For each un-routed customer, find best(*) insertion place in the route
     *          b. Find the best customer to be inserted (based on 2), insert to the route
     *      (*) "best" is defined in paper.
     * @return a list of routes corresponding to each vehicles
     */
    List<Route> constructRoute(List<Node> cluster) {
        List<Route> routes = new ArrayList<>();
        // un-routed customers are ordered by geographically distance from depot
        // TODO: try to order by lowest allowed starting time for service
        TreeSet<Node> unRoutedCustomers = new TreeSet<>(Comparator.comparingDouble(a -> dataModel.getDistanceFromDepot(a)));
        // First apply Solomon's sequential insertion heuristic
        do {
            // get the furthest (geographically) un-routed customer from depot
            Node seed = unRoutedCustomers.pollLast();
            // Initialize the route to (depot, seed, depot)
            Route route = new Route(dataModel, seed);
            NodePositionPair bestCustomerAndPosition = getBestCustomerAndPosition(route, unRoutedCustomers);
            while (bestCustomerAndPosition != null) {  // loop until infeasible to insert any more customers
                Node bestCustomer = bestCustomerAndPosition.node;
                int insertPosition = bestCustomerAndPosition.position;

                // Remove customer from un-routed set and insert into the route
                unRoutedCustomers.remove(bestCustomer);
                route.insertAtPosition(insertPosition, bestCustomer);

                bestCustomerAndPosition = getBestCustomerAndPosition(route, unRoutedCustomers);
            }
        } while (!unRoutedCustomers.isEmpty());

        return routes;
    }

    /**
     * Get the best customer to be inserted in the route, and the position to be inserted
     */
    NodePositionPair getBestCustomerAndPosition(Route route, Set<Node> unRoutedCustomers) {
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
     * Get the best feasible insertion cost of the customer on the route.
     * Due to time limit, we only explore p-neighbourhood
     * @return insertion cost or null if it's not feasible to insert this customer into the route.
     */
    CostPositionPair getBestInsertionCostAndPosition(Route route, Node customer) {
        assert !route.routedPath.contains(customer);
        CostPositionPair result = null;
        List<Integer> pNeighbourhood = new ArrayList<>();
        // TODO: construct p-neighbourhood
        for (int i = 1; i < route.getLength(); i++)
            pNeighbourhood.add(i);

        for (int p : pNeighbourhood) {
            Double cost = getInsertionCost(route, customer, p);
            if (cost != null && (result == null || result.cost < cost)) {
                result = new CostPositionPair(cost, p);
            }
        }
        return result;
    }

    /**
     * Get the cost of inserting new customer between customers i(p - 1) and ip in the route
     * -> Route before insertion: (i0, ..., i(p-1), ip, ..., i0)
     * -> Route after insertion: (i0, ..., i(p-1), customer, ip, ..., i0)
     * @return insertion cost or null if it's not feasible to insert this customer into the position.
     */
    Double getInsertionCost(Route route, Node customer, int p) {
        // Check capacity constraint and time constraint
        if (!route.canInsertAtPosition(p, customer)) return null;
        // TODO: implement "best" insertion
        return null;
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
