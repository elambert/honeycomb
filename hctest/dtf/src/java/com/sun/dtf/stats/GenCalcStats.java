package com.sun.dtf.stats;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;

import com.sun.dtf.exception.QueryException;
import com.sun.dtf.exception.StatsException;
import com.sun.dtf.query.Cursor;


public class GenCalcStats {

    protected final static String SEP_STR = ".";
    
    protected final static String AVG_VAL = "avg_val";
    protected final static String TOT_VAL = "tot_val";
    
    protected final static String MAX_VAL = "max_val";
    protected final static String MIN_VAL = "min_val";

    protected final static String MIN_DUR = "min_dur";
    protected final static String MAX_DUR = "max_dur";
    protected final static String AVG_DUR = "avg_dur";
    
    protected final static String TOT_DUR = "tot_dur";
    
    protected final static String TOT_OCC = "tot_occ";
    protected final static String AVG_OCC = "avg_occ";

    private DecimalFormat formatter = new DecimalFormat("#0.000");

    protected String formatNumber(String num) {
        return formatter.format(num);
    }

    protected String formatNumber(double num) {
        return formatter.format(num);
    }

    protected String formatNumber(long num) {
        return formatter.format(num);
    }

    private class Event {
        private HashMap map = new HashMap();

        private long min = -1;
        private long max = -1;

        private long start = -1;
        private long stop = -1;

        private long minDuration = -1;
        private long maxDuration = -1;
        
        private long accDuration = 0;

        private long occurences = 0;

        public void setStart(long start) {
            occurences++;
            this.start = start;
            
            if (min == -1 || start < min)
                min = start;

            calcDuration();
        }

        public void setStop(long stop) {
            this.stop = stop;
            
            if (max == -1 || stop > max)
                max = stop;
            
            calcDuration();
        }

        public void calcDuration() {
            if (start != -1 && stop != -1) {
                long duration = stop - start;

                if (minDuration == -1 || duration < minDuration)
                    minDuration = duration;

                if (maxDuration == -1 || duration > maxDuration)
                    maxDuration = duration;

                accDuration+=duration;
                start = -1;
                stop = -1;
            }
        }

        public long duration() {
            return (max - min);
        }

        public void addProp(String key, long value) {
            Prop prop = (Prop) map.get(key);

            if (prop == null) {
                prop = new Prop();
                map.put(key, prop);
            }

            prop.addResult(value);
        }

        public HashMap getProps() {
            return map;
        }

        public long getMaxDuration() {
            return maxDuration;
        }

        public long getMinDuration() {
            return minDuration;
        }

        public long getOccurences() {
            return occurences;
        }
        
        public long getAvgDuration() { 
            if (occurences == 0)
                return 0;
            
            return accDuration/occurences;
        }
    }

    private class Prop {
        private long accValue = 0;

        private long occurences = 0;

        private long maxValue = 0;

        private long minValue = 0;

        public void addResult(long value) {
            accValue += value;
            occurences++;

            if (value > maxValue)
                maxValue = value;

            if (value < minValue)
                minValue = value;
        }

        public long getAccValue() {
            return accValue;
        }

        public void setAccValue(long accValue) {
            this.accValue = accValue;
        }

        public long getMaxValue() {
            return maxValue;
        }

        public void setMaxValue(long maxValue) {
            this.maxValue = maxValue;
        }

        public long getMinValue() {
            return minValue;
        }

        public void setMinValue(long minValue) {
            this.minValue = minValue;
        }

        public long getOccurences() {
            return occurences;
        }

        public void setOccurences(long occurences) {
            this.occurences = occurences;
        }
    }

    public LinkedHashMap calcStats(Cursor cursor)
            throws StatsException {
        LinkedHashMap properties = new LinkedHashMap();
        Event event = new Event();

        try {
            HashMap result = null;
            while ((result = cursor.next(false)) != null) {
                Iterator fields = result.keySet().iterator();

                while (fields.hasNext()) {
                    String fullpropname = (String) fields.next();
                    String keyprop = fullpropname.substring(fullpropname.lastIndexOf(".") + 1, fullpropname.length());

                    try {
                        String propValue = (String) result
                                .get(fullpropname);
                        long value = new Long(propValue).longValue();

                        if (keyprop.equals("start")) {
                            event.setStart(value);
                            continue;
                        }

                        if (keyprop.equals("stop")) {
                            event.setStop(value);
                            continue;
                        }
                            
                        event.addProp(keyprop, value);
                    } catch (NumberFormatException e) {
                        // ignoring.. 
                    }
                }
            }
        } catch (QueryException e) {
            throw new StatsException("Error with cursor.", e);
        }

        double duration = (event.duration() / 1000.0f);

        HashMap props = event.getProps();
        Iterator propKeys = props.keySet().iterator();

        while (propKeys.hasNext()) {
            String pkey = (String) propKeys.next();
            Prop prop = (Prop) props.get(pkey);

            double average = (double) prop.getAccValue() / duration;

            properties.put(pkey + SEP_STR + AVG_VAL, formatNumber(average));
            properties.put(pkey + SEP_STR + TOT_VAL, formatNumber(prop.getAccValue()));
            properties.put(pkey + SEP_STR + MAX_VAL, formatNumber(prop.getMaxValue()));
            properties.put(pkey + SEP_STR + MIN_VAL, formatNumber(prop.getMinValue()));
        }

        properties.put(MIN_DUR, formatNumber(event.getMinDuration() / 1000.0f));
        properties.put(MAX_DUR, formatNumber(event.getMaxDuration() / 1000.0f));
        properties.put(AVG_DUR, formatNumber(event.getAvgDuration() / 1000.0f));
        properties.put(TOT_DUR, formatNumber(duration));
        
        properties.put(TOT_OCC, formatNumber(event.getOccurences()));
        properties.put(AVG_OCC, formatNumber(event.getOccurences() / duration));

        return properties;
    }
}
