import java.util.*;

public class ExchangeAlgorithm {
    static final double EPSILON = 0.01;
    /**
     * Exchange with first-improving move.
     * Loop until reach local optimal.
     *
     * For each route in the solution, we iterate through all customers and try to
     * exchange with another customer from another route, accept immediately any improving exchange (move).
     * @param solution input current solution, modify directly this solution
     * @param dataModel
     */
    public static void optimizeDistance(List<Route> solution, DataModel dataModel) {
        boolean localOptimal = false;
        whileLoop:
        while (!localOptimal) {
            localOptimal = true;

            for (int r1Idx = 0; r1Idx < solution.size() - 1; r1Idx++) {
                for (int r2Idx = r1Idx + 1; r2Idx < solution.size(); r2Idx++) {
                    Route r1 = solution.get(r1Idx), r2 = solution.get(r2Idx);
                    for (int p1 = 1; p1 < r1.getLength() - 1; p1++) {
                        Node x1 = r1.get(p1 - 1), y1 = r1.get(p1), z1 = r1.get(p1 + 1);
                        if (y1 == dataModel.getDepot()) continue;
                        for (int p2 = 1; p2 < r2.getLength() - 1; p2++) {
                            Node x2 = r2.get(p2 - 1), y2 = r2.get(p2), z2 = r2.get(p2 + 1);
                            if (y2 == dataModel.getDepot()) continue;
                            // gain = dist(before) - dist(after)
                            double gain = dataModel.dist(x1, y1) + dataModel.dist(y1, z1) + dataModel.dist(x2, y2) + dataModel.dist(y2, z2)
                                    - (dataModel.dist(x1, y2) + dataModel.dist(y2, z1) + dataModel.dist(x2, y1) + dataModel.dist(y1, z2));
                            // Only exchange if gaining & feasible
                            if (gain > EPSILON && Utils.checkExchangeOperator(dataModel, r1, p1, r2, p2)) {
                                Node u1 = r1.removeCustomerAtIndex(p1);  // u1 == y1
                                Node u2 = r2.removeCustomerAtIndex(p2);  // u2 == y2
                                r1.insertAtPosition(p1, u2);
                                r2.insertAtPosition(p2, u1);

                                assert Utils.isValidRoute(dataModel, r1);
                                assert Utils.isValidRoute(dataModel, r2);
                                localOptimal = false;
                                continue whileLoop;
                            }
                        }
                    }
                }
            }
        }
    }
}
