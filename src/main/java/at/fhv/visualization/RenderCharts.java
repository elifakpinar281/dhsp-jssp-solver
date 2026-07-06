package at.fhv.visualization;

import java.io.IOException;

public class RenderCharts {
    private static final String CSV = "docs/assets/gbfs-ft02-samples.csv";
    private static final String MEMORY_PNG = "docs/assets/memory-usage-gbfs.png";
    private static final String DEPTH_PNG = "docs/assets/search-progress-gbfs.png";

    public static void main(String[] args) throws IOException {
        String csvPath = CSV;
        if (args.length > 0) {
            csvPath = args[0];
        }

        SearchStatistics searchStatistics = SearchStatistics.fromCsv(csvPath);
        if (searchStatistics.getSamples().isEmpty()) {
            System.out.println("No samples found");
            return;
        }

        SearchMetricsVisualizer visualizer = new SearchMetricsVisualizer("GBFS", searchStatistics);
        visualizer.saveAsPng(MEMORY_PNG, DEPTH_PNG);
        System.out.println("Charts written: " + MEMORY_PNG + " & " + DEPTH_PNG);
        System.out.println("Samples: " + searchStatistics.getSamples().size());
        SearchMetricsVisualizer.show("GBFS", searchStatistics);
    }
}
