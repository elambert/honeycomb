package com.sun.dtf.graph;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.encoders.KeypointPNGEncoderAdapter;

import com.sun.dtf.exception.GraphingException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.exception.StorageException;
import com.sun.dtf.state.ActionState;
import com.sun.dtf.state.DTFState;

public class PNGGraph extends ImageGraph { 

    public static void graph(URI uri,
                             String title,
                             ArrayList series,
                             String xAxisName,
                             int width,
                             int height)
           throws GraphingException, ParseException {
        JFreeChart chart = createChart(series, title, xAxisName);

        //chart.setBackgroundPaint(new Color(255,255,255,0));
        
        // Write png file
        try { 
            DTFState state = ActionState.getInstance().getState();
            OutputStream outputStream =  state.getStorage().getOutputStream(uri);
            
            KeypointPNGEncoderAdapter encoder = new KeypointPNGEncoderAdapter();
            encoder.setEncodingAlpha(false);
            
            byte[] bytes = encoder.encode(chart.createBufferedImage(width, height));
            outputStream.write(bytes, 0, bytes.length);
            
            outputStream.close();
        } catch (IOException e) { 
            throw new GraphingException("Error writing svg.",e);
        } catch (StorageException e) {
            throw new GraphingException("Error writing svg.",e);
        }
    }
}
