package at.fhv.visualization;

import java.io.File;
import java.io.IOException;

public class RenderCharts {
    private static final String DIR = "docs/assets";
    private static final String SUFFIX = "-samples.csv";

    public static void main(String[] args) throws IOException {
        File assets = new File(DIR);

        renderAllInFolder(assets);

        File[] entries = assets.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                if (entry.isDirectory()) {
                    renderAllInFolder(entry);
                }
            }
        }
    }

    private static void renderAllInFolder(File folder) throws IOException {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(SUFFIX)) {
                renderRun(file);
            }
        }
    }

    private static void renderRun(File csv) throws IOException {
        SearchStatistics statistics = SearchStatistics.fromCsv(csv.getPath());
        if (statistics.getSamples().isEmpty()) {
            System.out.println("No samples in " + csv.getPath());
            return;
        }

        String name = csv.getName().replace(SUFFIX, "");
        File folder = csv.getParentFile();

        String memoryPng = new File(folder, "memory-usage-" + name + ".png").getPath();
        String depthPng = new File(folder, "search-progress-" + name + ".png").getPath();

        SearchMetricsVisualizer.saveCharts(statistics, memoryPng, depthPng);
        System.out.println("Charts: " + memoryPng + " & " + depthPng + ", samples: " + statistics.getSamples().size());
    }
}