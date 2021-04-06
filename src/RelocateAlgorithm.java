import java.util.*;

/**
 * Local search move: Relocate operator.
 * For each route in the solution, try to relocate (un-route) all customers to other routes.
 * We use best-acceptance strategy for relocate algorithm (for each customer, find the best feasible relocate position).
 * Use push-forward (Solomon, 1997) as the cost function for the relocate operator.
 * Return only the best neighbor.
 */
public class RelocateAlgorithm {
    public static List<Route> run(List<Route> solution, DataModel dataModel) {
        List<List<Route>> neighbourSolutions = new ArrayList<>();
        for (int idx = 0; idx < solution.size(); idx++) {
            // Use deep copy so that we can modify routes without changing the original solution
            List<Route> curSolution = Utils.deepCopySolution(solution);
            Route r1 = curSolution.remove(idx);  // selected route

            // Relocate operator
            // Relocate based on the acceptance criterion: best-feasible
            int p1 = 1;
            while (p1 < r1.getLength()) {  // for each customer in r1
                Node u = r1.get(p1);
                if (u == dataModel.getDepot()) {  // skip depot
                    p1++;
                    continue;
                }
                // Try to insert u into the best position
                double minCost = 1e9;
                int r2Idx = -1, p2Idx = -1;  // index of the route and position of the customer to exchange
                for (int j = 0; j < curSolution.size(); j++) {
                    Route r2 = curSolution.get(j);
                    for (int p2 = 1; p2 <= r2.getLength(); p2++) {
                        // Check if u can be inserted into position p2 of r2
                        if ((p2 < r2.getLength() && r2.canInsertCustomerAt(p2, u))
                                || (p2 == r2.getLength() && r2.canAppendAtLastPosition(u))) {
                            double cost = Utils.getPushForwardAfterRelocation(dataModel, r1.get(p1), r2, p2);
                            if (cost < minCost) {
                                minCost = cost;
                                r2Idx = j;
                                p2Idx = p2;
                            }
                        }
                    }
                }
                if (r2Idx != -1) {
                    Node u1 = r1.removeCustomerAtIndex(p1);  // u1 == u, but still need to remove from r1
                    Route r2 = curSolution.get(r2Idx);
                    if (p2Idx == r2.getLength()) {  // make another trip
                        r2.appendAtLastPosition(u1);
                        r2.appendAtLastPosition(dataModel.getDepot());
                    } else {
                        r2.insertAtPosition(p2Idx, u1);
                    }
                    // No need to increase p1 since already remove customer at p1
                } else {
                    p1++;
                }
            }
            if (!r1.isEmptyRoute()) {  // Add back selected route if needed
                curSolution.add(r1);
            }

            neighbourSolutions.add(curSolution);  // Add to neighbourSolutions list
        }

        // Find best neighbor, using shortest route's length to break tie (if 2 solutions have same vehicle number)
        List<Route> bestNeighbor = Collections.min(neighbourSolutions, (a, b) -> {
            if (a.size() != b.size()) return a.size() - b.size();
            else {  // compare shortest route's length
                int shortestRouteLengthA = a.stream().min(Comparator.comparingInt(Route::getLength)).get().getLength();
                int shortestRouteLengthB = b.stream().min(Comparator.comparingInt(Route::getLength)).get().getLength();
                return shortestRouteLengthA - shortestRouteLengthB;
            }
        });

        return bestNeighbor;
    }
}
