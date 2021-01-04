import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * This is an extended version of the cluster-route-merge algorithm proposed by Chang, 2020.
 * The main difference is that the cluster-route-merge is performed in a DFS manner,
 * and there is a solution improvement phase (tabu search) after the initial solution construction.
 *
 * Algorithm: a multi-start strategy where we consider different threshold for the maximum # clusters.
 * For each number of cluster, we find the best solution by running initial construction and improvement phase,
 * then select the best result (solution) in all solutions.
 * The algorithm for each # cluster is as follow:
 *      1. Initial construction:
 *          1.1. Cluster demand nodes by time window
 *          1.2. Parallel construct a solution for each cluster with Solomon's sequential insertion heuristic
 *          1.3. Merge these solutions iteratively
 *      2. Improvement phase
 *
 */
public class ChangsAlgorithm implements ConstructionAlgorithm {
    DataModel dataModel;
    static final Logger logger = Logger.getLogger(MTVRPTW.class.getName());

    @Override
    public List<Route> run(DataModel dataModel) {
        this.dataModel = dataModel;
        List<List<Route>> solutions = new ArrayList<>();
        // Try different # of clusters
        for (int numClusters = 1; numClusters <= dataModel.configs.numClustersThreshold; numClusters++) {
            // Do 3 steps: cluster, parallel construction, merge
            List<Route> solution = run(numClusters);
            if (solution != null) solutions.add(solution);
        }

        List<Route> finalSolution = Utils.getBestSolution(solutions);
        assert Utils.isValidSolution(dataModel, finalSolution);
        return finalSolution;
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
                List<Route> routedCluster = constructRoutesParallel(clusters.get(i), departureTime);
                // Remove the newly routed customers from list of un-routed
                Set<Node> newlyRoutedCustomers = Utils.getRoutedCustomers(routedCluster);
                unRoutedCustomers.removeAll(newlyRoutedCustomers);
                // Merged Ki and K(i+1)
                List<Route> curMergedSolution = mergeRoutes(prevMergedSolution, routedCluster);
                // Apply improvement method for curMergedSolution
                curMergedSolution = runSolutionImprovement(curMergedSolution);
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
        return departureTimes.stream().filter(t -> t <= latestDepartureTime).collect(Collectors.toList());
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
     * Construct the routes for a cluster.
     * The objective is to minimize the number of vehicles needed.
     * Solomon's sequential insertion heuristic (I1):
     *      1. Initialize a route with seed
     *      2. Loop until cannot insert any more customers:
     *          a. For each un-routed customer, find best(*) insertion place in the route
     *          b. Find the best customer to be inserted (based on 2), insert to the route
     *      (*) "best" is defined in paper.
     * @return a list of routes corresponding to each vehicles
     */
    List<Route> constructRoutesParallel(List<Node> cluster, double departureTimeFromDepot) {
        List<Node> orderedCustomers = new ArrayList<>(cluster);
        // Step 2: rank the demand nodes in decreasing order of travel time from depot
        orderedCustomers.sort((a, b) -> Double.compare(dataModel.getDistanceFromDepot(b), dataModel.getDistanceFromDepot(a)));
        List<Route> bestRoutes = SolomonI1Algorithm.run(orderedCustomers, departureTimeFromDepot, dataModel);

        orderedCustomers.sort(Comparator.comparingDouble(a -> a.dueTime));
        List<Route> bestRoutes2 = SolomonI1Algorithm.run(orderedCustomers, departureTimeFromDepot, dataModel);

        bestRoutes = bestRoutes.size() < bestRoutes2.size() ? bestRoutes : bestRoutes2;

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
        ValueAndPosition bestValueAndPosition = null;
        Route routeToInsert = null;

        for (Route route : routes) {
            ValueAndPosition valueAndPosition = SolomonI1Algorithm.getC2ValueAndPosition(route, u, dataModel, new Parameter());
            if (valueAndPosition != null
                    && (bestValueAndPosition == null || bestValueAndPosition.value < valueAndPosition.value)) {
                bestValueAndPosition = valueAndPosition;
                routeToInsert = route;
            }
        }
        return routeToInsert != null ? new RoutePositionPair(routeToInsert, bestValueAndPosition.position) : null;
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
                if (m.checkPushForwardTimeFromPosition(pushForward, 0)) {
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
     * Apply Tabu search algorithm described in Chang's paper.
     *
     * @param solution the current solution
     * @return an equally good or better solution
     */
    List<Route> runSolutionImprovement(List<Route> solution) {
        List<Route> improvedSolution = runVehicleNumberReduction(solution);
        return improvedSolution;
    }

    /**
     * Simple procedure to reduce the number of needed vehicles.
     *
     * @param solution
     * @return
     */
    List<Route> runVehicleNumberReduction(List<Route> solution) {
        // Step 2: un-route the routes with # demand nodes less than threshold
        List<Route> shortRoutes = solution.stream().filter(r -> r.getNumDemandNodes() < dataModel.getDeltaThreshold()).collect(Collectors.toList());
        if (shortRoutes.isEmpty()) return solution;
        List<Node> unRoutedCustomers = new ArrayList<>();
        shortRoutes.forEach(r -> unRoutedCustomers.addAll(r.getDemandNodes()));

        // Step 3: apply parallel construction method to insert the un-routed demand nodes
        List<Route> reRoutedShortRoutes = constructRoutesParallel(unRoutedCustomers, 0);  // set departure time to 0?

        // Construct the new solution
        List<Route> newSolution = new ArrayList<>(solution);
        newSolution.removeAll(shortRoutes);
        newSolution.addAll(reRoutedShortRoutes);

        if (newSolution.size() > solution.size()) return solution;  // worsening move, discard
        else if (newSolution.size() < solution.size()) return newSolution;  // improving move, take
        else {  // check # of routes with # demand nodes less than threshold
            List<Route> newShortRoutes = newSolution.stream().filter(r -> r.getNumDemandNodes() < dataModel.getDeltaThreshold()).collect(Collectors.toList());
            if (newShortRoutes.size() < shortRoutes.size()) return newSolution;
            else return solution;
        }
    }

    /**
     * The local search algorithm - lambda-interchange, described in Osman (1993).
     * Here we modified it so that it can solve MTVRPTW.
     *
     * Osman (1993) use the evaluation function (the cost of the tour) to be an approximation of
     * the length of optimal TSP tour (the problem in Osman, 1993 paper is VRP).
     * In this case, for MTVRPTW, we use the total travel time of the route as the evaluation function.
     * The
     *
     * We also utilize the concept of push-forward concept in Solomon (1987) to speed up the process.
     * Note that, for insertion, we need to check for capacity and time feasibility.
     *
     * @param solution
     * @return
     */
//    List<Route> runLambdaInterchange(List<Route> solution) {
//        List<Route> bestSolution = Utils.deepCopySolution(solution);
//
//    }

}
