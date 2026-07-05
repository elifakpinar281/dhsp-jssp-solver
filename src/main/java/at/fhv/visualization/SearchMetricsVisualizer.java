package at.fhv.visualization;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

// will be updated later
public class SearchMetricsVisualizer extends JFrame {
    private final JFreeChart memoryChart;
    private final JFreeChart depthChart;

    public SearchMetricsVisualizer(String title, SearchStatistics statistics) {
        super(title);

        this.memoryChart = buildMemoryChart(statistics);
        this.depthChart = buildDepthChart(statistics);

        JPanel panel = new JPanel(new GridLayout(2, 1));
        panel.add(new ChartPanel(memoryChart));
        panel.add(new ChartPanel(depthChart));
        panel.setPreferredSize(new Dimension(900, 800));

        setContentPane(panel);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        pack();
    }

    private JFreeChart buildMemoryChart(SearchStatistics statistics) {
        XYSeries reached = new XYSeries("reached");
        XYSeries frontier = new XYSeries("frontier");

        for (SearchStatistics.Sample sample : statistics.getSamples()) {
            double x = sample.expansions() / 1000.0;
            reached.add(x, sample.reached() / 1_000_000.0);
            frontier.add(x, sample.frontier() / 1_000_000.0);
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(reached);
        dataset.addSeries(frontier);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Memory usage",
                "expansions (thousands)",
                "stored states (millions)",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false);

        stylePlot(chart);
        return chart;
    }

    private JFreeChart buildDepthChart(SearchStatistics statistics) {
        XYSeries depth = new XYSeries("max depth reached");

        for (SearchStatistics.Sample sample : statistics.getSamples()) {
            depth.add(sample.expansions() / 1000.0, sample.maxDepth());
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(depth);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Search progress",
                "expansions (thousands)",
                "search depth (scheduled operations)",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false);

        stylePlot(chart);

        int goalDepth = statistics.getTotalOperations();
        if (goalDepth > 0) {
            ValueMarker marker = new ValueMarker(goalDepth);
            marker.setPaint(Color.GRAY);
            marker.setStroke(new BasicStroke(1.5f));
            marker.setLabel("goal depth (" + goalDepth + ")");
            marker.setLabelAnchor(RectangleAnchor.TOP_RIGHT);
            marker.setLabelTextAnchor(TextAnchor.BOTTOM_RIGHT);
            chart.getXYPlot().addRangeMarker(marker);
        }
        return chart;
    }

    private void stylePlot(JFreeChart chart) {
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
    }

    public void saveAsPng(String memoryPngPath, String depthPngPath) throws IOException {
        ChartUtils.saveChartAsPNG(new File(memoryPngPath), memoryChart, 900, 500);
        ChartUtils.saveChartAsPNG(new File(depthPngPath), depthChart, 900, 500);
    }

    public static void show(String title, SearchStatistics statistics) {
        SwingUtilities.invokeLater(() -> {
            SearchMetricsVisualizer frame = new SearchMetricsVisualizer(title, statistics);
            frame.setVisible(true);
        });
    }
}
