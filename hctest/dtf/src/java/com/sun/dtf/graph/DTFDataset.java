package com.sun.dtf.graph;

import java.util.HashMap;
import java.util.Iterator;
import java.util.ArrayList;

import org.jfree.data.DomainOrder;
import org.jfree.data.general.DatasetChangeListener;
import org.jfree.data.general.DatasetGroup;
import org.jfree.data.xy.XYDataset;

import com.sun.dtf.actions.graph.Series;
import com.sun.dtf.exception.GraphingException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.exception.QueryException;

import com.sun.dtf.query.QueryIntf;
import com.sun.dtf.util.StringUtil;

public class DTFDataset implements XYDataset {
 
    private ArrayList KEYS_TO_SKIP = null;
    
    private QueryIntf _query = null;
    private HashMap _x = null;
    private HashMap _y = null;
    private ArrayList _series = null;
    
    private String getKey(String fullkey) { 
        return fullkey.substring(fullkey.lastIndexOf(".")+1,fullkey.length());
    }

    public DTFDataset() {
        _x = new HashMap();
        _y = new HashMap();
        _series = new ArrayList();
       
        KEYS_TO_SKIP = new ArrayList();
        KEYS_TO_SKIP.add("start");
        KEYS_TO_SKIP.add("stop");
    }
    
    public void addSerie(DatasetKey key, ArrayList x, ArrayList y) { 
        _series.add(key);
        _x.put(key, x);
        _y.put(key, y);
    }
    
    public DTFDataset(QueryIntf query, 
                      Series serie,
                      int offset, 
                      int maxSize) 
           throws GraphingException, ParseException { 
        this();
        _query = query;
       
        String sepby = serie.getSeparateBy();
        String aggby = serie.getAggregateBy();

        /*
         * Sample the results using the sampleSize determine how many results 
         * to actually read through.
         */
        int current = 0;
        try {
            long sample = serie.getSampleunit();
            String cursor = serie.getCursor() + ".";
            
            HashMap element = null;
            while ((element = _query.next(false)) != null) { 
                
                if (current >= offset) { 
                    Iterator keys = element.keySet().iterator();
                                       
                    while (keys.hasNext()) { 
                        String fullkey = (String) keys.next();
                        String value = (String) element.get(fullkey);
                        String key = getKey(fullkey);
                        
                        String by = null;

                        if (sepby != null)
                            by = (String)element.get(cursor + sepby);
                        
                        if (aggby != null)
                            by = (String)element.get(cursor + aggby);

                        if (by == null && sepby != null)
                            throw new GraphingException("Unable to find [" + 
                                                        sepby + "] attribute.");
                       
                        if (!KEYS_TO_SKIP.contains(key) && 
                            StringUtil.isNumber(value)) { 

                            if (value == null)
                                throw new GraphingException("Missing value for ["
                                                            + key + "]");

                            String stop = (String)element.get(cursor + "stop");
                                   
                            if (stop == null)
                                throw new GraphingException(
                                    "Unable to find [stop] attribute."); 
                            
                            DatasetKey skey = null;
                                                        
                            if (aggby != null) 
                                skey = new DatasetKey(key, null);
                            else if (sepby != null) 
                                skey = new DatasetKey(key, by);
                            else 
                                skey = new DatasetKey(key, null);
                            
                            // save x
                            ArrayList xserie = (ArrayList)_x.get(skey);
                            if (xserie == null) {
                                xserie = new ArrayList();
                                _series.add(skey);
                                _x.put(skey, xserie);
                            }
                               
                            ArrayList yserie = (ArrayList)_y.get(skey);
                            if (yserie == null) {
                                yserie = new ArrayList();
                                _y.put(skey, yserie);
                            }
                             
                            Long record = new Long(stop);
                            Double yvalue = new Double(value);
                            
                            if (aggby != null) { 
                                ArrayList aux = null; 
                                Long xSampled = new Long((record.longValue() / sample) * sample);
                                int index = xserie.indexOf(xSampled);
                                
                                if (index != -1) {
                                    aux = (ArrayList) yserie.get(index);
                                } else {
                                    aux = new ArrayList();
                                 
                                    /* 
                                     * XXX: need to make this cleaner...
                                     * reorder by x
                                     */
                                    int s = 0;
                                    for (s = 0; s < xserie.size(); s++) { 
                                        Long auxL = (Long)xserie.get(s);
                                        if (auxL.longValue() > xSampled.longValue()) 
                                            break;
                                    }
                                    
                                    xserie.add(s,xSampled);
                                    yserie.add(s,aux);
                                }
                                
                                aux.add(yvalue);
                            } else { 
                                
                                /* 
                                 * XXX: need to make this cleaner...
                                 * reorder by x
                                 */
                                int s = 0;
                                for (s = 0; s < xserie.size(); s++) { 
                                    Long auxL = (Long)xserie.get(s);
                                    if (auxL.longValue() > record.longValue()) 
                                        break;
                                }
                                
                                xserie.add(s,record);
                                yserie.add(s,new Double(value));
                            }
                        }
                    }
                }
                current++;
                
                if (current >= maxSize)
                    break;
                
            }
        } catch (QueryException e) {
            throw new GraphingException("Error querying.",e);
        }
        
        /*
         * TODO: here is where we can have different aggregation logic, such as
         *       averaging, sum, rolling average, etc.
         */
        if (aggby != null) { 
            // calculate averages and compress ArrayLists that are contained
            // in the y series... :)
            Iterator skeys = _series.iterator();
            
            while (skeys.hasNext()) { 
                DatasetKey key = (DatasetKey) skeys.next();
                ArrayList yserie = (ArrayList) _y.get(key);
               
                for (int i = 0; i < yserie.size(); i++) { 
                    ArrayList aux = (ArrayList) yserie.get(i);
                    double acc = 0;
                    
                    for (int a = 0; a < aux.size(); a++) { 
                        acc+=((Double)aux.get(a)).doubleValue();
                    }
                    yserie.remove(i);
                    
                    if (serie.getAggFunc().equals(Series.AVG_AGG_FUNC)) { 
                        yserie.add(i,new Double(acc/aux.size()));
                    } else if (serie.getAggFunc().equals(Series.SUM_AGG_FUNC)) { 
                        yserie.add(i,new Double(acc));
                    }
                }
            }
        }
    }

    public DomainOrder getDomainOrder() { return DomainOrder.NONE; }

    public int getItemCount(int series) {
        Comparable key = (Comparable) _series.get(series);
        ArrayList s = (ArrayList)_x.get(key);
        return s.size();
    }

    public Number getX(int series, int item) {
        Comparable key = (Comparable) _series.get(series);
        ArrayList s = (ArrayList)_x.get(key);
        return (Number)s.get(item);
    }

    public double getXValue(int series, int item) {
        Comparable key = (Comparable) _series.get(series);
        ArrayList s = (ArrayList)_x.get(key);
        return ((Long)s.get(item)).doubleValue();
    }
    
    public Number getY(int series, int item) {
        Comparable key = (Comparable) _series.get(series);
        ArrayList s = (ArrayList)_y.get(key);
        return (Number)s.get(item);
    }

    public double getYValue(int series, int item) {
        Comparable key = (Comparable) _series.get(series);
        ArrayList s = (ArrayList)_y.get(key);
        return ((Double)s.get(item)).doubleValue();
    }

    public int getSeriesCount() {
        return _series.size();
    }

    public Comparable getSeriesKey(int series) {
        return (Comparable)_series.get(series);
    }

    public int indexOf(Comparable seriesKey) {
        return _series.indexOf(seriesKey);
    }

    public void addChangeListener(DatasetChangeListener listener) { }
    public DatasetGroup getGroup() { return null; }
    public void removeChangeListener(DatasetChangeListener listener) { }
    public void setGroup(DatasetGroup group) { }
}
