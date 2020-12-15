import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Algorithm: a multi-start strategy where we consider different threshold for the maximum # clusters.
 * For each number of cluster, we find the best solution by running a cluster-route-merge algorithm
 * then select the best result (solution) in all solutions.
 * The algorithm for each set of clusters is as follow:
 *      1. Cluster demand nodes
 *      2. Parallel construct a solution for each cluster
 *      3. Merge these solutions
 *
 */
public class ClusterRouting implements SolutionConstructionAlgorithm {
    DataModel dataModel;
    SolomonI1Algorithm solomonI1Algorithm;
    static final Logger logger = Logger.getLogger(MTVRPTW.class.getName());

    public ClusterRouting() {
        solomonI1Algorithm = new SolomonI1Algorithm();
    }

    @Override
    public List<Route> run(DataModel dataModel) {
        this.dataModel = dataModel;
        List<List<Route>> solutions = new ArrayList<>();
        // Try different # of clusters
        for (int numClusters = 1; numClusters <= dataModel.numClustersThreshold; numClusters++) {
            // Do 3 steps: cluster, parallel construction, merge
            List<Route> routes = run(numClusters);
            solutions.add(routes);
        }

        List<Route> finalSolution = Utils.getBestSolution(solutions);
        assert Utils.isValidSolution(dataModel, finalSolution);
        return finalSolution;
    }

    public List<Route> run(int numClusters) {
        List<List<Node>> clusters = constructClusters(numClusters);

        // Apply parallel construction: construct routes for each cluster separately
        List<List<Route>> subSolutions = new ArrayList<>();
        for (int i = 0; i < clusters.size(); i++) {
            List<Node> cluster = clusters.get(i);
            List<Route> constructedRoute = constructRoute(cluster);
            // from the second cluster onward, for each route (vehicle), we set the time to leave depot to be as late as possible
//            if (i > 0) constructedRoute.forEach(route -> Utils.optimizeRoute(dataModel, route));
            subSolutions.add(constructedRoute);
        }

        // Apply the merging approach (to reduce # vehicles needed)
        assert Utils.isParallelRoutesValid(dataModel, subSolutions);
        List<Route> mergedSolution = mergeRoutes(subSolutions);
        assert Utils.isValidSolution(dataModel, mergedSolution);
//        logger.info("Merge routes, # vehicles: " + mergedSolution.size());
        return mergedSolution;
    }

    /**
     * Construct (numClusters) clusters based on polar angle / distance from depot / due time.
     * This is for the vehicle to make multiple trips (same vehicle make multiple trips in different clusters).
     *
     * @param numClusters target number of clusters
     * @return a list of clusters, each one contains all customers with latest delivery time larger than all customers
     * in the previous clusters.
     */
    List<List<Node>> constructClusters(int numClusters) {
        List<List<Node>> clusters = new ArrayList<>();
        int idealDemandPerCluster = dataModel.getTotalDemands() / numClusters;

        // Order customer based on latest service time (due time)
        List<Node> orderedCustomers = new ArrayList<>(dataModel.getDemandNodes());
        orderedCustomers.sort(Comparator.comparingInt(a -> a.dueTime));
        Queue<Node> queue = new LinkedList<>(orderedCustomers);

        // Distribute the (sorted) demand nodes to the numClusters ordered clusters
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
                else queue.poll();

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
    List<Route> constructRoute(List<Node> cluster) {
        List<Node> orderedCustomers = new ArrayList<>(cluster);
        // Rank the demand nodes in decreasing order of travel time from depot
//        orderedCustomers.sort((a, b) -> Double.compare(dataModel.getDistanceFromDepot(b), dataModel.getDistanceFromDepot(a)));
        orderedCustomers.sort(Comparator.comparingInt(a -> a.readyTime));

        // Walk-around, TODO: make this a static method
        List<Route> bestRoutes = solomonI1Algorithm.run(orderedCustomers, 0, dataModel);
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
        CostPositionPair bestCostPositionPair = null;
        Route routeToInsert = null;

        for (Route route : routes) {
            CostPositionPair costPositionPair = solomonI1Algorithm.getBestInsertionCostAndPosition(route, u);
            if (costPositionPair != null
                    && (bestCostPositionPair == null || bestCostPositionPair.cost < costPositionPair.cost)) {
                bestCostPositionPair = costPositionPair;
                routeToInsert = route;
            }
        }
        return routeToInsert != null ? new RoutePositionPair(routeToInsert, bestCostPositionPair.position) : null;
    }

    /**
     * Merge routes between Si and K(i+1), where Si = S(i-1) U Ki.
     * Details can be found in the merging approach, Chang's paper.
     * @param parallelRoutes the list of all routes from the parallel construction method
     * @return the final route after merging all routes in parallelRoutes
     */
    List<Route> mergeRoutes(List<List<Route>> parallelRoutes) {
        // Copy, to keep the order of each list in parallelRoutes intact
        List<Route> curK = new ArrayList<>(parallelRoutes.get(0));
        for (int i = 0; i < parallelRoutes.size() - 1; i++) {  // if there is only 1 cluster, return the cluster immediately
            List<Route> nextK = new ArrayList<>(parallelRoutes.get(i + 1));

            // Sort routes in Ki in increasing order of the latest arrival time at the depot (equals to starting service time at last node - depot)
            curK.sort(Comparator.comparingDouble(a -> a.getStartingServiceTimeAt(a.getLength() - 1)));

            // Sort routes in K(i+1) in increasing order of the starting service time at the first customer
            nextK.sort(Comparator.comparingDouble(a -> a.getStartingServiceTimeAt(1)));
            // Step 3: merge the first route in Ki with the first feasible route in K(i+1), then continue
            // Implementation: for each route l in Ki, try to merge it with first feasible route m in K(i + 1)
            //          if can merge, remove m from K(i+1) and add the merged route (l + m) to S(k+1)
            //          else, add l to S(k+1)
            //      in both case, increase counter for Ki
            List<Route> nextS = new ArrayList<>();
            Iterator<Route> curKIterator = curK.iterator();
            while (curKIterator.hasNext()) {
                Route l = curKIterator.next();
                // Find first route in nextK that can be merged with l
                Route firstFeasibleMergeRoute = null;
                for (Route m : nextK) {
                    // Compute the push forward time at the last depot of l (first depot of m)
                    double pushForward = l.getStartingServiceTimeAt(l.getLength() - 1) - m.getStartingServiceTimeAt(0);
                    if (m.checkPushForwardTimeFromNode(pushForward, 0)) {
                        firstFeasibleMergeRoute = m;
                        break;
                    }
                }
                if (firstFeasibleMergeRoute != null) {  // feasible to merge
                    nextK.remove(firstFeasibleMergeRoute);  // remove the corresponding route from nextK
                    nextS.add(new Route(l, firstFeasibleMergeRoute));  // add the merged route to the result nextS
                } else {  // no feasible route in nextK to merge (including the case when nextK is empty)
                    nextS.add(l);
                }
                curKIterator.remove();  // in both cases, remove the route l from curK
            }
            // Add all remaining routes in nextK (if exist) to nextS
            nextS.addAll(nextK);

            curK = nextS;  // Assign curK to S(k+1)
        }
        return curK;
    }
}
