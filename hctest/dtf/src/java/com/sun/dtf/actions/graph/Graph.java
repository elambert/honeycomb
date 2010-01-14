package com.sun.dtf.actions.graph;

import java.net.URI;
import java.util.ArrayList;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exception.ActionException;
import com.sun.dtf.exception.DTFException;
import com.sun.dtf.exception.ParseException;
import com.sun.dtf.graph.ConsoleGraph;
import com.sun.dtf.graph.PNGGraph;
import com.sun.dtf.graph.SVGGraph;

/**
 * @dtf.tag graph
 * 
 * @dtf.since 1.0
 * @dtf.author Rodney Gomes
 * 
 * @dtf.tag.desc  Graph tag can be used to generate graphs with JFreeChart. This
 *                tag is able to generate various types of graphs, including to 
 *                the screen and png or svg file formats.
 *                
 * @dtf.tag.example 
 * <script>
 *     <query uri="storage://OUTPUT/perf_dist.txt" 
 *            type="txt" 
 *            event="dtf.echo"
 *            cursor="perfcursor"/>
 *            
 *     <graph title="Unit testing..."
 *            uri="storage://OUTPUT/test.svg"
 *            type="svg">
 *         <series name="Events per second"
 *                 cursor="perfcursor"
 *                 mode="histogram"/>       
 *     </graph>
 * </script> 
 * 
 * @dtf.tag.example 
 * <script>
 *     <query uri="storage://OUTPUT/perf_dist.txt" 
 *            type="txt" 
 *            event="dtf.echo"
 *            cursor="perfcursor"/>
 *            
 *     <graph title="Timeline of each event"
 *            uri="storage://OUTPUT/test.png"
 *            type="png">
 *         <series name="Event timeline"
 *                 cursor="perfcursor"/>
 *     </graph>
 * </script> 
 */
public class Graph extends Action {
    
    public static String SVG_GRAPH = "svg";
    public static String PNG_GRAPH = "png";
    public static String CSL_GRAPH = "console";

    /**
     * @dtf.attr uri
     * @dtf.attr.desc The URI location to output the current graph to. This 
     *                is used by all the graph types except the console which
     *                just outputs to your console.
     */
    private String uri = null;
    
    /**
     * @dtf.attr title
     * @dtf.attr.desc The title to give this graph.
     */
    private String title = null;
    
    /**
     * @dtf.attr type
     * @dtf.attr.desc <p>
     *                The type of graph that we are going to generate. Currently
     *                there are 3 types of graphs and they are:
     *                </p>
     *                <b>Graph Types</b>
     *                <ul>
     *                <table border=1>
     *                    <tr>
     *                        <th>Type</th> 
     *                        <th>Description</th> 
     *                    </tr>
     *                    <tr>
     *                        <td>svg</td>
     *                        <td>Outputs the graphing results to an SVG, 
     *                            more information on SVG standard can be found 
     *                            <a href="http://www.w3.org/TR/SVG/">here</a></td>
     *                    </tr>
     *                    <tr>
     *                        <td>png</td>
     *                        <td>Outputs the graphing results to a PNG file.
     *                            more information on PNG standard can be found
     *                            <a href="http://www.w3.org/Graphics/PNG/">here</a>
     *                        </td>
     *                    </tr>
     *                    <tr>
     *                        <td>console</td>
     *                        <td>Outputs to the availble GUI Console, if none
     *                            is present than an exception will be thrown.</td>
     *                    </tr>
     *                </table>
     *                </ul>
     */
    private String type = null;
    
    /**
     * @dtf.attr width
     * @dtf.attr.desc The width of the output image.
     */
    private String width = null;
    
    /**
     * @dtf.attr height
     * @dtf.attr.desc The height of the output image.
     */
    private String height = null;
    
    public Graph() { }
    
    public void execute() throws DTFException {
        getLogger().info("Graphing " + this);
        ArrayList series = findActions(Series.class);
        
        if (getType().equals(CSL_GRAPH)) { 
            ConsoleGraph.graph(getUri(), 
                               getTitle(),
                               series,
                               "Time",
                               getWidth(),
                               getHeight());
        } else if (getType().equals(SVG_GRAPH)) { 
            if (getUri() == null)
                throw new ParseException("uri must be set, to output to an svg.");
            
            SVGGraph.graph(getUri(),
                           getTitle(),
                           series,
                           "Time",
                           getWidth(),
                           getHeight());
        } else if (getType().equals(PNG_GRAPH)) { 
            if (getUri() == null)
                throw new ParseException("uri must be set, to output to a png.");
            
            PNGGraph.graph(getUri(),
                           getTitle(),
                           series,
                           "Time",
                           getWidth(),
                           getHeight());
        }
    }
    
    public void setUri(String uri) { this.uri = uri; }
    public URI getUri() throws ActionException, ParseException { return parseURI(uri); }

    public String getTitle() throws ParseException { return replaceProperties(title); }
    public void setTitle(String title) { this.title = title; }

    public String getType() throws ParseException { return replaceProperties(type); }
    public void setType(String type) { this.type = type; }

    public int getWidth() throws ParseException { return toInt("width",width,1024); }
    public void setWidth(String width) { this.width = width; }

    public int getHeight() throws ParseException { return toInt("height",height,768); }
    public void setHeight(String height) { this.height = height; } 
}
