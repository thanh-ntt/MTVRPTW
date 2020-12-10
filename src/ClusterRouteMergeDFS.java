import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This is "Solution" in paper, but we called it ClusterRouting to make it clear that
 * while the algorithm is running, this will just be one of the solutions,
 * not the final solution.
 */
public class ClusterRouteMergeDFS {
    DataModel dataModel;
    static final Logger logger = Logger.getLogger(MTVRPTW.class.getName());

    public ClusterRouteMergeDFS(DataModel dataModel) {
        this.dataModel = dataModel;
    }

    public List<Route> run(int numClusters) {
        List<List<Node>> clusters = constructClusters(numClusters);

        // route-merge-improve in depth-first order
        List<List<Route>> solutions = new ArrayList<>();
        dfs(clusters, solutions, new ArrayList<>(), Collections.singletonList(0.0), dataModel.getDemandNodes(), 0, numClusters);

        List<Route> bestSolution = Utils.getBestSolution(solutions);
//        logger.info("DFS solution, # vehicles: " + (bestSolution == null ? "-1" : bestSolution.size()));
        return bestSolution;
    }

    void dfs(List<List<Node>> clusters, List<List<Route>> solutions, List<Route> prevMergedSolution,
             List<Double> departureTimes, Set<Node> unRoutedCustomers, int i, int numClusters) {
        // TODO: check back, we need deep copy, not shallow copies
        if (i < numClusters) {
            for (double departureTime : departureTimes) {
                // Construct a solution for the current sub-MTVRPTW
                List<Route> routedCluster = constructRoute(clusters.get(i), departureTime);
                // Remove the newly routed customers from list of un-routed
                Set<Node> newlyRoutedCustomers = Utils.getRoutedCustomers(routedCluster);
                unRoutedCustomers.removeAll(newlyRoutedCustomers);
                // Merged Ki and K(i+1)
                List<Route> curMergedSolution = mergeRoutes(prevMergedSolution, routedCluster);
                // TODO: apply improvement method for curMergedSolution
                // Get the list of possible departure times (from depot) for the next cluster
                List<Double> nextDepartureTimes = selectDepartureTimes(routedCluster, unRoutedCustomers);
                // Continue DFS route-merge-improve
                dfs(clusters, solutions, curMergedSolution, nextDepartureTimes, unRoutedCustomers, i + 1, numClusters);
                // Add back the newly routed customers to list of un-routed
                unRoutedCustomers.addAll(newlyRoutedCustomers);
            }
        } else {
            solutions.add(prevMergedSolution);
        }
    }

    /**
     * Select m arrival times (at the depot) from the previous sub-MTVRPTW (cluster) plus the setup time at depot
     * as m departure times for the next sub-MTVRPTW.
     *
     * TODO: refine the mechanism to select m departure times
     *
     * @param routes the solution to the previous sub-MTVRPTW (previous cluster)
     * @param unRoutedCustomers the list of remaining un-routed customers
     * @return list of m departure times
     */
    List<Double> selectDepartureTimes(List<Route> routes, Set<Node> unRoutedCustomers) {
        // TODO: select based on "some" mechanism
        List<Double> departureTimes = new ArrayList<>();
        routes.forEach(route -> {
            departureTimes.add(route.getLatestArrivalTimeAtDepot() + dataModel.getDepot().serviceTime);
        });
        double latestDepartureTime = dataModel.getLatestDepartureTime(unRoutedCustomers);
        return departureTimes.stream().filter(t -> !Utils.greaterThan(t, latestDepartureTime)).collect(Collectors.toList());
    }

    /**
     * Construct (numClusters) clusters based on customer's latest delivery time (dueTime).
     * This is for the vehicle to make multiple trips (same vehicle make multiple trips in different clusters).
     *
     * @param numClusters target number of clusters
     * @return a list of clusters, each one contains all customers with latest delivery time larger than all customers
     * in the previous clusters.
     */
    List<List<Node>> constructClusters(int numClusters) {
        List<List<Node>> clusters = new ArrayList<>();
        // 3.1.1 step 2: estimate ideal # vehicles needed in each cluster
        double averageDemandPerCluster = 1.0 * dataModel.getTotalDemands() / numClusters;
        int vehicleCapacity = dataModel.getVehicleCapacity();
        int idealNumVehicles = (int) Math.ceil(averageDemandPerCluster / vehicleCapacity);
        int idealDemandPerCluster = idealNumVehicles * vehicleCapacity;

        // 3.1.1 step 3: distribute demand nodes to clusters
        // Priority Queue ordered by latest service time
        List<Node> orderedCustomers = new ArrayList<>(dataModel.getDemandNodes());
        // Rank demand nodes in increasing order of latest service time
        orderedCustomers.sort(Comparator.comparingInt(a -> a.dueTime));
        Queue<Node> queue = new LinkedList<>(orderedCustomers);

        // Step 3: distribute the (sorted) demand nodes to the numClusters ordered clusters
        while (!queue.isEmpty()) {
            List<Node> curCluster = new ArrayList<>();
            // if this is the last cluster, just add everything
            if (clusters.size() == numClusters - 1) {
                curCluster.addAll(queue);
                queue.clear();
            }

            int clusterDemand = 0;
            while (!queue.isEmpty()) {
                Node customer = queue.peek();
                // If adding next customer to the curCluster results in exceeding average demand per cluster,
                // move the customer to the next cluster
                if (clusterDemand + customer.demand > idealDemandPerCluster) break;

                queue.poll();
                curCluster.add(customer);
                clusterDemand += customer.demand;
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
    List<Route> constructRoute(List<Node> cluster, double departureTimeFromDepot) {
        List<Node> orderedCustomers = new ArrayList<>(cluster);
        // Step 2: rank the demand nodes in decreasing order of travel time from depot
        orderedCustomers.sort((a, b) -> Double.compare(dataModel.getDistanceFromDepot(b), dataModel.getDistanceFromDepot(a)));

        List<Route> bestRoutes = runI1InsertionHeuristic(orderedCustomers, departureTimeFromDepot);

        // Step 4, 5: try to reduce # vehicles needed
        int targetedNumVehicles = bestRoutes.size() - 1;
        while (targetedNumVehicles > 0) {
            RoutesOptimizationResult routesOptimizationResult = optimizeNumVehicles(orderedCustomers, targetedNumVehicles);
            if (routesOptimizationResult.unRoutedCustomers.isEmpty()) {  // can serve all customers
                bestRoutes = routesOptimizationResult.routes;  // update bestRoutes (this ensures that 'bestRoutes' is always a valid solution)
                targetedNumVehicles--;  // try to route with 1 less vehicle
            } else {  // there are customers remained un-routed, output (previous) bestRoutes with (targetedNumVehicles + 1) vehicles used
                break;
            }
        }
        return bestRoutes;
    }

    /**
     * I1 insertion heuristic proposed by Solomon, 1987.
     *
     * @param orderedCustomers customers are ordered (decreasing) by geographically distance from depot
     * @return a feasible solution that serves all customers
     */
    List<Route> runI1InsertionHeuristic(List<Node> orderedCustomers, double departureTimeFromDepot) {
        List<Node> unRoutedCustomers = new ArrayList<>(orderedCustomers);
        // TODO: try to order by lowest allowed starting time for service, suggested in Solomon 1987
        List<Route> routes = new ArrayList<>();
        // Apply Solomon's sequential insertion heuristic
        do {
            // get the furthest (geographically) un-routed customer from depot
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
     * Try to reduce the number of vehicles needed.
     *  1. Select m demand nodes (customers) as seed points, initialize m routes at once.
     *  2. Insert the remaining un-routed demand nodes (customers) into their best feasible positions of m routes.
     *
     * @param inputOrderedCustomers customers are ordered (decreasing) by geographically distance from depot
     * @param m targeted number of vehicles
     * @return
     */
    RoutesOptimizationResult optimizeNumVehicles(List<Node> inputOrderedCustomers, int m) {
        // Copy to avoid modifying the original list
        List<Node> orderedCustomers = new ArrayList<>(inputOrderedCustomers);
        // Step 4: select m furthest demand nodes as seed points, initialize m routes at once
        List<Route> routes = orderedCustomers.subList(0, m).stream()
                .map(c -> new Route(dataModel, c)).collect(Collectors.toList());
        routes.forEach(Route::addDummyDepot);  // Step 5.1: add a dummy depot to each route
        orderedCustomers.subList(0, m).clear();  // remove first m customers from the list

        // Step 5: insert the remaining un-routed demand nodes (customers) into their best feasible positions of m routes
        // For each un-routed customers, we try to find the best route to insert this customer into
        // Note: this is different from Solomon's I1 insertion heuristic where the route is fixed
        //          and we try to find the best customers to insert to the route
        Iterator<Node> iterator = orderedCustomers.iterator();
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

        routes.forEach(Route::removeDummyDepot);  // Remove dummy depots in all routes
        return new RoutesOptimizationResult(routes, orderedCustomers);
    }

    /**
     * Find the best feasible route to insert the customer u into.
     *
     * @param routes the list of candidate routes that the customer u can be inserted into
     * @param u the new customer to be inserted
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

    /**
     * Merge routes of the 2 clusters Ki and K(i+1), and return S(i+1) = Ki U K(i+1).
     * Details can be found in the merging approach, Chang's paper.
     *
     * The routes in firstCluster can be a merged route (since the merging is iterative).
     *
     * @param firstCluster the routes (solution) of the first cluster
     * @param secondCluster the routes (solution) of the second cluster
     * @return the solution (routes) after merging the 2 solutions
     */
    List<Route> mergeRoutes(List<Route> firstCluster, List<Route> secondCluster) {
        // Step 1: input related data
        // Copy, to keep the order of the lists intact
        List<Route> Ki = new ArrayList<>(firstCluster);
        List<Route> Kip1 = new ArrayList<>(secondCluster);

        // Step 2: rank the routes
        // Sort routes in Ki in increasing order of latest arrival time at depot
        Ki.sort(Comparator.comparingDouble(Route::getLatestArrivalTimeAtDepot));
        // Sort routes in Kip1 in increasing order of starting service time at the first customer
        Kip1.sort(Comparator.comparingDouble(a -> a.getStartingServiceTimeAt(1)));

        // Step 3: merge the first route in Ki with the first feasible route in Kip1
        // Implementation: for each route l in Ki, try to merge it with first feasible route m in K(i + 1)
        //          if can merge, remove m from K(i+1) and add the merged route (l + m) to S(k+1)
        //          else, add l to S(k+1)
        //      in both case, increase counter for Ki
        List<Route> result = new ArrayList<>();
        Iterator<Route> iterator = Ki.iterator();
        while (iterator.hasNext()) {
            Route l = iterator.next();
            // Find first route in Kip1 that can be merged with l
            Route firstFeasibleMergeRoute = null;
            for (Route m : Kip1) {
                // Compute the push forward time at the last depot of l (first depot of m)
                double pushForward = l.getStartingServiceTimeAt(l.getLength() - 1) - m.getStartingServiceTimeAt(0);
                if (m.checkPushForwardTimeFromNode(pushForward, 0)) {
                    firstFeasibleMergeRoute = m;
                    break;
                }
            }
            if (firstFeasibleMergeRoute != null) {  // feasible to merge
                Kip1.remove(firstFeasibleMergeRoute);  // remove the corresponding route from nextK
                result.add(new Route(l, firstFeasibleMergeRoute));  // add the merged route to the result result
            } else {  // no feasible route in nextK to merge (including the case when nextK is empty)
                result.add(l);
            }
            iterator.remove();  // in both cases, remove the route l from curK
        }
        // Add all remaining routes in nextK (if exist) to result
        result.addAll(Kip1);
        return result;
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
     * TODO: move this to Route class
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
