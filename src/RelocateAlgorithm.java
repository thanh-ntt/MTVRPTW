import java.util.*;

/**
 * Local search with built-in perturbation.
 * Perturbation: exchange operator
 * Local search: relocate operator
 */
public class RelocateAlgorithm {
    public static List<Route> run(List<Route> solution, DataModel dataModel) {
        List<List<Route>> neighbourSolutions = new ArrayList<>();
        List<Integer> selectedRouteIndices = selectRouteIndices(solution);
        for (int idx : selectedRouteIndices) {
            // Use deep copy so that we can modify routes without changing the original solution
            List<Route> curSolution = Utils.deepCopySolution(solution);
            Route r1 = curSolution.remove(idx);  // selected route

            // Try exchange operator
            // For each customer in r1
//            for (int p1 = 1; p1 < r1.getLength(); p1++) {
//                if (r1.getCustomerAt(p1) == dataModel.getDepot()) continue;  // skip depot
//                outerLoopExchange:
//                for (int j = 0; j < curSolution.size(); j++) {  // iterate all remaining routes
//                    Route r2 = curSolution.get(j);
//                    for (int p2 = 1; p2 < r2.getLength(); p2++) {  // iterate all demand nodes in r2
//                        if (r2.getCustomerAt(p2) == dataModel.getDepot()) continue;  // skip depot
//                        if (Utils.checkExchangeOperator(dataModel, r1, p1, r2, p2)) {
//                            Node u1 = r1.removeCustomerAtIndex(p1);
//                            Node u2 = r2.removeCustomerAtIndex(p2);
//                            r1.insertAtPosition(p1, u2);
//                            r2.insertAtPosition(p2, u1);
//                            break outerLoopExchange;
//                        }
//                    }
//                }
//            }

            // Try relocate operator
            // Relocate depending on the acceptance criterion: best-feasible
            int p1 = 1;
            while (p1 < r1.getLength()) {  // for each customer in r1
                Node u = r1.getCustomerAt(p1);
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
                        if ((p2 < r2.getLength() && r2.canInsertCustomerAt(p2, u))
                                || (p2 == r2.getLength() && r2.canAppendAtLastPosition(u))) {
                            double cost = Utils.getCostRelocateOperator(dataModel, r1, p1, r2, p2);
                            if (cost < minCost) {
                                minCost = cost;
                                r2Idx = j;
                                p2Idx = p2;
                            }
                        }
                    }
                }
                if (r2Idx != -1) {
                    Node u1 = r1.removeCustomerAtIndex(p1);
                    assert u1 == u;
                    Route r2 = curSolution.get(r2Idx);
                    if (p2Idx == r2.getLength()) {
                        r2.appendAtLastPosition(u1);
                        r2.appendAtLastPosition(dataModel.getDepot());
                    } else {
                        r2.insertAtPosition(p2Idx, u1);
                    }

                    assert Utils.isValidRoute(dataModel, r2);
                    // No need to increase p1 since already remove customer at p1
                } else {
                    p1++;
                }
            }

            // Add back selected route if needed
            if (!r1.getDemandNodes().isEmpty()) {
                curSolution.add(r1);
            }

            // Add to neighbourSolution
            neighbourSolutions.add(curSolution);
        }

        // Find best local move
        // TODO: find better heuristic to approximate search neighbor
        List<Route> bestNeighborhood = Collections.min(neighbourSolutions, (a, b) -> {
            if (a.size() != b.size()) return a.size() - b.size();
//            else return Utils.compareTotalDistance(dataModel, a, b);
            else {  // compare shortest route length
                // TODO: try to rationale this: why shorter route length works
                int shortestRouteLengthA = a.stream().min(Comparator.comparingInt(Route::getLength)).get().getLength();
                int shortestRouteLengthB = b.stream().min(Comparator.comparingInt(Route::getLength)).get().getLength();
                return shortestRouteLengthA - shortestRouteLengthB;
            }
        });

        assert Utils.isValidSolution(dataModel, bestNeighborhood);

        return bestNeighborhood;
    }

    public static List<Integer> selectRouteIndices(List<Route> solution) {
        // TODO: select more clever
//        double averageRouteLength = solution.stream().mapToInt(r -> r.getLength()).average().getAsDouble();
//        int minRouteLength = solution.stream().mapToInt(r -> r.getLength()).min().getAsInt();
        List<Integer> selectedRouteIndices = new ArrayList<>();
        for (int i = 0; i < solution.size(); i++) {
//            if (solution.get(i).getLength() <= averageRouteLength) {
            selectedRouteIndices.add(i);
//            }
        }
        return selectedRouteIndices;
    }
}
