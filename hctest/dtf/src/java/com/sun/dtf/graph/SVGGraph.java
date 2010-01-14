package com.sun.dtf.graph;

import java.awt.Rectangle;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.jfree.chart.JFreeChart;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;

import com.sun.dtf.exception.GraphingException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.exception.StorageException;
import com.sun.dtf.state.ActionState;
import com.sun.dtf.state.DTFState;

public class SVGGraph extends ImageGraph { 

    public static void graph(URI uri,
                             String title,
                             ArrayList series,
                             String xAxisName,
                             int width,
                             int height)
           throws GraphingException, ParseException {
        JFreeChart chart = createChart(series, title, xAxisName);
        
        DOMImplementation domImpl =
            GenericDOMImplementation.getDOMImplementation();
        Document document = domImpl.createDocument(null, "svg", null);

        // Create an instance of the SVG Generator
        SVGGraphics2D svgGenerator = new SVGGraphics2D(document);

        // draw the chart in the SVG generator
        chart.draw(svgGenerator, new Rectangle(0,0,width,height));
        
        // Write svg file
        try { 
            DTFState state = ActionState.getInstance().getState();
            OutputStream outputStream =  state.getStorage().getOutputStream(uri);
            Writer out = new OutputStreamWriter(outputStream, "UTF-8");
            svgGenerator.stream(out, true /* use css */);                       
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) { 
            throw new GraphingException("Error writing svg.",e);
        } catch (StorageException e) {
            throw new GraphingException("Error writing svg.",e);
        }
    }
}
