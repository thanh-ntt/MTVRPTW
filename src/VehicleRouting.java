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

        mergeRoutes(parallelRoutes);
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
        List<Route> routes = runI1InsertionHeuristic(cluster);

        // Step 4, 5: try to reduce # vehicles needed
        int targetedNumVehicles = routes.size() - 1;
        while (targetedNumVehicles > 0) {
            RoutesOptimizationResult routesOptimizationResult1 = optimizeNumVehicles1(cluster, targetedNumVehicles);
            if (routesOptimizationResult1.unRoutedCustomers.isEmpty()) {  // can serve all customers
                routes = routesOptimizationResult1.routes;  // update routes (this ensures that 'routes' is always a valid solution)
                targetedNumVehicles--;  // try to route with 1 less vehicle
            } else {  // there are customers remained un-routed, move to step 5.1, 5.2
                RoutesOptimizationResult routesOptimizationResult2 = optimizeNumVehicles2(routesOptimizationResult1.unRoutedCustomers, routesOptimizationResult1.routes);
                if (routesOptimizationResult2.unRoutedCustomers.isEmpty()) {  // can serve all customers
                    return routesOptimizationResult2.routes;
                } else {  // fail to serve all customers, return the last valid solution (routes)
                    return routes;
                }
            }
        }
        return routes;
    }

    /**
     * I1 insertion heuristic proposed by Solomon, 1987.
     *
     * @param cluster list of all nodes (customers) in the current cluster
     * @return a feasible solution that serves all customers
     */
    List<Route> runI1InsertionHeuristic(List<Node> cluster) {
        List<Route> routes = new ArrayList<>();
        // un-routed customers are ordered by geographically distance from depot
        // TODO: try to order by lowest allowed starting time for service, suggested in Solomon 1987
        TreeSet<Node> unRoutedCustomers = new TreeSet<>(Comparator.comparingDouble(a -> dataModel.getDistanceFromDepot(a)));
        unRoutedCustomers.addAll(cluster);
        // Apply Solomon's sequential insertion heuristic
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
            routes.add(route);
        } while (!unRoutedCustomers.isEmpty());

        return routes;
    }

    /**
     * Try to reduce the number of vehicles needed.
     *  1. Select m demand nodes (customers) as seed points, initialize m routes at once.
     *  2. Insert the remaining un-routed demand nodes (customers) into their best feasible positions of m routes.
     *
     * @param cluster all demand nodes (customers)
     * @param m targeted number of vehicles
     * @return
     */
    RoutesOptimizationResult optimizeNumVehicles1(List<Node> cluster, int m) {
        // Order the demand nodes (customers) based on distance from depot
        TreeSet<Node> orderedCustomers = new TreeSet<>(Comparator.comparingDouble(a -> dataModel.getDistanceFromDepot(a)));
        orderedCustomers.addAll(cluster);
        List<Route> routes = new ArrayList<>();

        // Step 4: select m furthest demand nodes as seed points, initialize m routes at once
        for (int i = 0; i < m; i++) {
            Route route = new Route(dataModel, orderedCustomers.pollLast());
            routes.add(route);
        }

        List<Node> unRoutedCustomers = new ArrayList<>();  // list of customers that cannot be routed

        // Step 5: insert the remaining un-routed demand nodes (customers) into their best feasible positions of m routes
        Iterator<Node> iterator = orderedCustomers.iterator();
        while (iterator.hasNext()) {
            Node customer = iterator.next();
            RoutePositionPair bestRoutePositionPair = getBestRouteAndPosition(routes, customer);
            if (bestRoutePositionPair != null) {  // can insert this customer
                bestRoutePositionPair.route.insertAtPosition(bestRoutePositionPair.position, customer);
                iterator.remove();  // remove this customer from list of un-routed customers
            } else {  // cannot route this customer
                unRoutedCustomers.add(customer);
            }
        }

        return new RoutesOptimizationResult(routes, unRoutedCustomers);
    }

    /**
     * Try to insert the list of un-routed customers into the list of existing routes.
     *
     * @param unRoutedCustomers
     * @param routes the existing routes
     * @return a RoutesOptimizationResult
     */
    RoutesOptimizationResult optimizeNumVehicles2(List<Node> unRoutedCustomers, List<Route> routes) {
        // Step 5.1: add a dummy depot to each route
        for (Route route : routes) {
            route.addDummyDepot();
        }
        // Step 5.2: insert the remaining demand nodes - un-routed customers
        // For each un-routed customers, we try to find the best route to insert this customer into
        // Note: this is different from Solomon's I1 insertion heuristic where the route is fixed
        //          and we try to find the best customers to insert to the route
        Iterator<Node> iterator = unRoutedCustomers.iterator();
        while (iterator.hasNext()) {
            Node customer = iterator.next();
            RoutePositionPair bestRoutePositionPair = getBestRouteAndPosition(routes, customer);
            if (bestRoutePositionPair != null) {  // can insert the customer into 1 of the routes
                Route bestRoute = bestRoutePositionPair.route;
                int bestPosition = bestRoutePositionPair.position;
                // do this since the length of the bestRoute might change after inserting the new customer
                boolean isInsertedLastPosition = bestPosition == bestRoute.getLength() - 1;
                bestRoute.insertAtPosition(bestPosition, customer);
                if (isInsertedLastPosition) {
                    // If a node is inserted into the place between the dummy depot closest to the destination
                    // depot and the destination depot, then add a new dummy depot after the node
                    bestRoute.addDummyDepot();
                }
                iterator.remove();  // remove this customer from list of un-routed customers
            } else {  // cannot route this customer
                break;  // early terminate, return remaining un-routed customers
            }
        }
        routes.forEach(Route::removeDummyDepot);
        return new RoutesOptimizationResult(routes, unRoutedCustomers);
    }

    /**
     * Find the best feasible route to insert the customer u into.
     *
     * @param routes the list of candidate routes that the customer u can be inserted into
     * @param u customer to be inserted
     * @return a RoutePositionPair representing the best feasible route and position, or null if no feasible route found
     */
    RoutePositionPair getBestRouteAndPosition(List<Route> routes, Node u) {
        CostPositionPair bestCostPositionPair = null;
        Route routeToInsert = null;

        for (Route route : routes) {
            CostPositionPair costPositionPair = getBestInsertionCostAndPosition(route, u);
            if (costPositionPair != null
                    && (bestCostPositionPair == null || bestCostPositionPair.cost < costPositionPair.cost)) {
                bestCostPositionPair = costPositionPair;
                routeToInsert = route;
            }
        }
        return routeToInsert != null ? new RoutePositionPair(routeToInsert, bestCostPositionPair.position) : null;
    }

    void mergeRoutes(List<List<Route>> parallelRoutes) {

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
     * Get the best feasible insertion cost of the customer u on the route.
     * Due to time limit, we only explore p-neighbourhood
     * @return insertion cost or null if it's not feasible to insert this customer into the route.
     */
    CostPositionPair getBestInsertionCostAndPosition(Route route, Node u) {
        assert !route.routedPath.contains(u);
        CostPositionPair result = null;

        // to save time, only consider p-neighbourhood of u (new customer)
        // this is the set of p nodes on the route closest (distance/time) to u
        List<Integer> pNeighbourhood = new ArrayList<>();
        if (route.getLength() - 2 <= dataModel.pNeighbourhoodSize) {  // excluding the depot (depot appears twice in any routes)
            for (int i = 1; i < route.getLength(); i++)
                pNeighbourhood.add(i);
        } else {
            PriorityQueue<Integer> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> dataModel.getTravelTime(route.routedPath.get(a), u)));
            for (int i = 1; i < route.getLength(); i++)
                pq.offer(i);
            for (int i = 0; i < dataModel.pNeighbourhoodSize; i++)
                pNeighbourhood.add(pq.poll());
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
     * Get the cost of inserting new customer u between i(p-1) = m and ip = n
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
    List<Node> unRoutedCustomers;
    public RoutesOptimizationResult(List<Route> routes, List<Node> unRoutedCustomers) {
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
