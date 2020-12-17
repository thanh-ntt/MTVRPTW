import java.util.List;

public interface LocalSearchAlgorithm {
    /**
     * Run the algorithm to improve the input solution.
     *
     * @param solution input (valid) solution
     * @param dataModel
     * @return a equally good or better solution
     */
    List<Route> run(List<Route> solution, DataModel dataModel);
}
