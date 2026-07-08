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

// https://github.com/jfree/jfreechart/releases/tag/v1.5.6
// TODO: More Visualizations for thesis
public class SearchMetricsVisualizer {
    public static void saveCharts(SearchStatistics statistics, String memoryPath, String depthPath) throws IOException {
        JFreeChart memoryChart = buildMemoryChart(statistics);
        JFreeChart depthChart = buildDepthChart(statistics);
        ChartUtils.saveChartAsPNG(new File(memoryPath), memoryChart, 900, 500);
        ChartUtils.saveChartAsPNG(new File(depthPath), depthChart, 900, 500);
    }

    public static void show(String title, SearchStatistics statistics) {
        JFreeChart memoryChart = buildMemoryChart(statistics);
        JFreeChart depthChart = buildDepthChart(statistics);

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(title);
            JPanel panel = new JPanel(new GridLayout(2, 1));
            panel.add(new ChartPanel(memoryChart));
            panel.add(new ChartPanel(depthChart));
            panel.setPreferredSize(new Dimension(900, 800));
            frame.setContentPane(panel);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.pack();
            frame.setVisible(true);
        });
    }

    private static JFreeChart buildMemoryChart(SearchStatistics statistics) {
        XYSeries reached = new XYSeries("reached");
        XYSeries frontier = new XYSeries("frontier");
        for (Sample sample : statistics.getSamples()) {
            double x = sample.expansions();
            reached.add(x, sample.reached());
            frontier.add(x, sample.frontier());
        }

        XYSeriesCollection data = new XYSeriesCollection();
        data.addSeries(reached);
        data.addSeries(frontier);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Memory usage",
                "expansions",
                "stored states",
                data,
                PlotOrientation.VERTICAL,
                true, true, false);

        stylePlot(chart);
        return chart;
    }

    private static JFreeChart buildDepthChart(SearchStatistics statistics) {
        XYSeries depth = new XYSeries("max depth reached");

        for (Sample sample : statistics.getSamples()) {
            depth.add(sample.expansions(), sample.maxDepth());
        }

        XYSeriesCollection data = new XYSeriesCollection();
        data.addSeries(depth);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Search progress",
                "expansions",
                "search depth (scheduled operations)",
                data,
                PlotOrientation.VERTICAL,
                true, true, false);

        stylePlot(chart);
        int goalDepth = statistics.getTotalOperations();
        if (goalDepth > 0) {
            ValueMarker marker = new ValueMarker(goalDepth);
            marker.setPaint(Color.GRAY);
            marker.setStroke(new BasicStroke(1.5f));
            marker.setLabel("goal depth: " + goalDepth);
            marker.setLabelAnchor(RectangleAnchor.TOP_RIGHT);
            marker.setLabelTextAnchor(TextAnchor.BOTTOM_RIGHT);
            chart.getXYPlot().addRangeMarker(marker);
        }
        return chart;
    }

    private static void stylePlot(JFreeChart chart) {
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
    }
}