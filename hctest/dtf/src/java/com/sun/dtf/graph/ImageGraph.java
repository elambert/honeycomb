package com.sun.dtf.graph;

import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.ui.RectangleInsets;

import com.sun.dtf.exception.GraphingException;
import com.sun.dtf.exception.ParseException;

public abstract class ImageGraph { 

    protected static JFreeChart createChart(ArrayList series,
                                     String title,
                                     String xAxisName)
            throws ParseException, GraphingException {

        JFreeChart chart = DTFGraphUtil.createCombinedTimeSeriesChart(
            title,
            xAxisName,
            series, 
            true,           // create legend?
            true,           // generate tooltips?
            false           // generate URLs?
        );
        
        chart.setBackgroundPaint(Color.white);

        XYPlot plot = (XYPlot) chart.getPlot();
        plot.setBackgroundPaint(Color.lightGray);
        plot.setDomainGridlinePaint(Color.white);
        plot.setRangeGridlinePaint(Color.white);
        plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
        plot.setDomainCrosshairVisible(true);
        plot.setRangeCrosshairVisible(true);
        
        XYItemRenderer r = plot.getRenderer();
        if (r instanceof XYLineAndShapeRenderer) {
            XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) r;
            renderer.setBaseShapesVisible(true);
            renderer.setBaseShapesFilled(true);
        }
       
        DateAxis axis = (DateAxis) plot.getDomainAxis();
        axis.setDateFormatOverride(new SimpleDateFormat("dd-MM hh:mm:ss.SSS"));
        axis.setVerticalTickLabels(true);
        return chart;
    }
}
