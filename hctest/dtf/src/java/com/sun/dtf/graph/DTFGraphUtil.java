package com.sun.dtf.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.labels.XYToolTipGenerator;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.urls.StandardXYURLGenerator;
import org.jfree.chart.urls.XYURLGenerator;
import org.jfree.data.statistics.SimpleHistogramBin;
import org.jfree.data.statistics.SimpleHistogramDataset;
import org.jfree.data.xy.XYDataset;

import com.sun.dtf.actions.Action;
import com.sun.dtf.actions.graph.Series;
import com.sun.dtf.exception.GraphingException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.query.Cursor;

public class DTFGraphUtil {
    
    private static XYPlot[] createHistogramPlot(ValueAxis xAxis,
                                                Series serie,
                                                XYDataset dataset,
                                                boolean legend, 
                                                boolean tooltips, 
                                                boolean urls,
                                                long binsize) 
            throws ParseException {
        /*
         * If we didn't separate on a specific element then the series in here 
         * are to be aggregated by the event they were thrown in and just one
         * generic histogram for all of the events is generated.
         */
        int count = 0;
        if (serie.getSeparateBy() == null)
            count = 1;
        else
            count = dataset.getSeriesCount();

        XYPlot[] xyplots = new XYPlot[count];
        for (int s = 0; s < count; s++) { 
            SimpleHistogramDataset xydataset = 
                                    new SimpleHistogramDataset(serie.getName());
            
            double[] observations = new double[dataset.getItemCount(s)];
            double lastlowerbound = -1;
            
            int i;
            for (i = 0; i < dataset.getItemCount(s); i++) { 
                observations[i] = dataset.getX(s, i).doubleValue();
                double x = dataset.getX(s, i).doubleValue();
                double round = ((long)(x/binsize))*binsize;
                
                if (round > lastlowerbound) { 
                    xydataset.addBin(new SimpleHistogramBin(round,
                                                            round + binsize,
                                                            true,
                                                            false));
                    
                    lastlowerbound = round;
                }
            }
           
            xydataset.addObservations(observations);
            xydataset.setAdjustForBinSize(false);
            
            NumberAxis yAxis = new NumberAxis(serie.getName());
            xyplots[s] = new XYPlot(xydataset, xAxis, yAxis, null);
            xyplots[s].setOrientation(PlotOrientation.VERTICAL);
        }
        
        return xyplots;
    }

    public static JFreeChart createCombinedTimeSeriesChart(String title,
                                                           String timeAxisLabel, 
                                                           ArrayList series,
                                                           boolean legend, 
                                                           boolean tooltips, 
                                                           boolean urls) 
           throws ParseException, GraphingException {
        
        ValueAxis timeAxis = new DateAxis(timeAxisLabel);
        CombinedDomainXYPlot plot = new CombinedDomainXYPlot(timeAxis);
       
        for (int i = 0; i < series.size() ; i++) { 
            Series serie = (Series) series.get(i);
            Cursor cursor = Action.getState().getCursors().
                                                   getCursor(serie.getCursor());
            
            if (cursor == null)
                throw new GraphingException("Unable to find cursor [" + 
                                            serie.getCursor() +"]");
            
            DTFDataset dataset = new DTFDataset(cursor.getQuery(),
                                                serie,
                                                0,
                                                Integer.MAX_VALUE);
            
            if (dataset.getSeriesCount() == 0)
                continue;
            
            XYPlot xyplot = null;
            
            if (serie.getMode() != null && serie.getMode().equals("histogram")) { 
                XYPlot[] xyplots = createHistogramPlot(timeAxis, 
                                                       serie,
                                                       dataset,
                                                       legend,
                                                       tooltips,
                                                       urls,
                                                       serie.getSampleunit());
                
                for(int s = 0; s < xyplots.length; s++) 
                    plot.add(xyplots[s]);
            } else { 
                NumberAxis valueAxis = new NumberAxis(serie.getName());
               
                if (serie.getLowerLimit() != -1) { 
                    valueAxis.setAutoRange(false);
                    valueAxis.setLowerBound(serie.getLowerLimit());
                } 
                
                if (serie.getUpperLimit() != -1) { 
                    valueAxis.setAutoRange(false);
                    valueAxis.setUpperBound(serie.getUpperLimit());
                } 
                
                xyplot = new XYPlot(dataset, timeAxis, valueAxis, null);
                plot.add(xyplot);
            }
        }
       
        plot.setDomainCrosshairLockedOnData(true);

        XYToolTipGenerator toolTipGenerator = null;
        if (tooltips) {
            toolTipGenerator = StandardXYToolTipGenerator.getTimeSeriesInstance();
        }

        XYURLGenerator urlGenerator = null;
        if (urls) {
            urlGenerator = new StandardXYURLGenerator();
        }

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true,false);
        renderer.setBaseToolTipGenerator(toolTipGenerator);
        renderer.setURLGenerator(urlGenerator);
        plot.setRenderer(renderer);

        JFreeChart chart = new JFreeChart(title, 
                                          JFreeChart.DEFAULT_TITLE_FONT,
                                          plot,
                                          legend);
        return chart;
    }

    private static DTFDataset aggregateBy(DTFDataset dataset) {
        DTFDataset result =  new DTFDataset();
        HashMap x = new HashMap();
        HashMap y = new HashMap();
       
        int itemCount = Integer.MAX_VALUE;
        
        for (int s = 0; s < dataset.getSeriesCount(); s++) 
            if (itemCount > dataset.getItemCount(s))
                    itemCount = dataset.getItemCount(s);
        
        for (int s = 0; s < dataset.getSeriesCount(); s++) { 
            for (int i = 0; i < itemCount; i++) { 
                DatasetKey setkey = (DatasetKey)dataset.getSeriesKey(s);
                String primaryKey = setkey.getPrimary();
                Long xlong =  (Long)dataset.getX(s, i);
                double ydouble =  dataset.getYValue(s, i);
                
                long[] xserie = (long[])x.get(primaryKey);
                if (xserie == null) {
                    xserie = new long[dataset.getItemCount(itemCount)];
                    x.put(primaryKey, xserie);
                }
                
                double[] yserie = (double[])y.get(primaryKey);
                if (yserie == null) {
                    yserie = new double[dataset.getItemCount(itemCount)];
                    y.put(primaryKey, yserie);
                }
                
                yserie[i] += ydouble;
                xserie[i] = xlong.longValue();
            }
        }
       
        Iterator keys = x.keySet().iterator();
        while (keys.hasNext()) { 
            String key = (String)keys.next();
            
            ArrayList xl = new ArrayList();
            long[] xserie = (long[])x.get(key);
            for (int xi = 0; xi < xserie.length; xi++)
                xl.add(new Long(xserie[xi]));
            
            ArrayList yl = new ArrayList();
            double[] yserie = (double[])y.get(key);
            for (int yi = 0; yi < yserie.length; yi++)
                yl.add(new Double(yserie[yi]));
           
            DatasetKey dkey = new DatasetKey(key,null);
            result.addSerie(dkey, xl, yl);
        }
        
        return result;
    }
}
