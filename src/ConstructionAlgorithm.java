import java.util.List;

/**
 * Interface for all solution construction algorithms.
 * Also include meta-heuristic since they return a solution from a test case.
 */
public interface ConstructionAlgorithm {
    /**
     * Run the algorithm to construct a valid solution for the MTVRPTW.
     *
     * @param dataModel the input test case, and input configuration
     * @return a list of routes as a solution for the MTVRPTW
     */
    List<Route> run(DataModel dataModel);
}
