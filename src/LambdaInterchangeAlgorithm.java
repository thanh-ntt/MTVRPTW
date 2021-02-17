import java.util.*;

/**
 * Lambda-interchange algorithm.
 * Osman, 1993
 */

public class LambdaInterchangeAlgorithm {
    public static List<Route> run(List<Route> solution, DataModel dataModel) {
        // Search all pair of routes
        int numIterations = 100;
        for (int iter = 0; iter < numIterations; iter++) {
            boolean localOptimal = true;
            outerLoop:
            for (int i = 0; i < solution.size() - 1; i++) {
                for (int j = i + 1; j < solution.size(); j++) {
                    Route r1 = solution.get(i), r2 = solution.get(j);
                    int[] res = lambdaInterchange(r1, r2, dataModel);
                    if (res == null) {
                        continue;
                    };
                    int p1 = res[2], p2 = res[3];
                    Node u1 = r1.getCustomerAt(p1), u2 = r2.getCustomerAt(p2);
                    if (res[0] == 0 && res[1] == 1) {
                        r1.removeCustomerAtIndex(p1);
                        r2.insertAtPosition(p2, u1);
                    } else if (res[0] == 1 && res[1] == 0) {
                        r2.removeCustomerAtIndex(p2);
                        r1.insertAtPosition(p1, u2);
                    } else {  // (1, 1) interchange
                        r1.removeCustomerAtIndex(p1);
                        r2.removeCustomerAtIndex(p2);
                        r1.insertAtPosition(p1, u2);
                        r2.insertAtPosition(p2, u1);
                    }
                    if (r1.getNumDemandNodes() == 0) {
                        solution.remove(r1);
                        break outerLoop;
                    }
                    if (r2.getNumDemandNodes() == 0) {
                        solution.remove(r2);
                        break outerLoop;
                    }
                }
            }
            if (localOptimal) break;
        }
        return solution;
    }

    public static int[] lambdaInterchange(Route r1, Route r2, DataModel dataModel) {
        int[] bestInterchange = null;
        double minCost = 0;  // if no exchange produce cost < 0, then there is no improving moves
        // Shift process - (0, 1) operator
        for (int p1 = 1; p1 < r1.getLength() - 1; p1++) {
            Node u1 = r1.getCustomerAt(p1), prev1 = r1.getCustomerAt(p1 - 1), next1 = r1.getCustomerAt(p1 + 1);
            for (int p2 = 1; p2 < r2.getLength(); p2++) {
                // Try insert u1 into position p2 of r2
                if (r2.canInsertCustomerAt(p2, u1) && r1.canRemoveCustomerAt(p1)) {
                    Node next2 = r2.getCustomerAt(p2), prev2 = r2.getCustomerAt(p2 - 1);
                    double cost = (dataModel.dist(prev2, u1) + dataModel.dist(u1, next2) + dataModel.dist(prev1, next1))
                            - (dataModel.dist(prev1, u1) + dataModel.dist(u1, next1) + dataModel.dist(prev2, next2));
//                    double cost = Utils.getCostRelocateOperator(dataModel, r1, p1, r2, p2);
                    if (r1.getNumDemandNodes() == 1) cost = (int) -1e9;
                    if (cost < minCost) {  // saving
                        minCost = cost;
                        bestInterchange = new int[]{0, 1, p1, p2};
                    }
                }
            }
        }
        // Shift process - (1, 0) operator
        for (int p1 = 1; p1 < r1.getLength(); p1++) {
            Node prev1 = r1.getCustomerAt(p1 - 1), next1 = r1.getCustomerAt(p1);
            for (int p2 = 1; p2 < r2.getLength() - 1; p2++) {
                Node u2 = r2.getCustomerAt(p2), next2 = r2.getCustomerAt(p2 + 1), prev2 = r2.getCustomerAt(p2 - 1);
                // Try insert u2 into position p1 of r1
                if (r1.canInsertCustomerAt(p1, u2) && r2.canRemoveCustomerAt(p2)) {
//                    double distanceCost = (dataModel.getDistance(prev1, u2) + dataModel.getDistance(u2, next1) + dataModel.getDistance(prev2, next2))
//                            - (dataModel.getDistance(prev2, u2) + dataModel.getDistance(u2, next2) + dataModel.getDistance(prev1, next1));
                    double cost = Utils.getCostRelocateOperator(dataModel, r2, p2, r1, p1, new Parameter());
                    if (r1.getNumDemandNodes() == 1) cost = (int) -1e9;
                    if (cost < minCost) {  // saving
                        minCost = cost;
                        bestInterchange = new int[]{1, 0, p1, p2};
                    }
                }
            }
        }
        // Interchange process - (1, 1) operator
        for (int p1 = 1; p1 < r1.getLength() - 1; p1++) {
            for (int p2 = 1; p2 < r2.getLength() - 1; p2++) {
                if (Utils.checkExchangeOperator(dataModel, r1, p1, r2, p2)) {
                    Node u1 = r1.getCustomerAt(p1), prev1 = r1.getCustomerAt(p1 - 1), next1 = r1.getCustomerAt(p1 + 1);
                    Node u2 = r2.getCustomerAt(p2), prev2 = r2.getCustomerAt(p2 - 1), next2 = r2.getCustomerAt(p2 + 1);
                    double cost = (dataModel.dist(prev1, u2) + dataModel.dist(u2, next1) + dataModel.dist(prev2, u1) + dataModel.dist(u1, next2))
                            - (dataModel.dist(prev1, u1) + dataModel.dist(u1, next1) + dataModel.dist(prev2, u2) + dataModel.dist(u2, next2));
//                    double cost = Utils.getCostExchangeOperator(dataModel, r1, p1, r2, p2);
                    if (cost < minCost) {
                        minCost = cost;
                        bestInterchange = new int[]{1, 1, p1, p2};
                    }
                }
            }
        }
        return bestInterchange;
    }
}
