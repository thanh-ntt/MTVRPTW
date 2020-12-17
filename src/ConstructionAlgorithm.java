import java.util.List;

public interface ConstructionAlgorithm {
    /**
     * Run the algorithm to construct a valid solution for the MTVRPTW.
     *
     * @param dataModel the input test case, and input configuration
     * @return a list of routes as a solution for the MTVRPTW
     */
    List<Route> run(DataModel dataModel);
}
