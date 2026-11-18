package service;

import model.River_node;
import java.util.List;
//perform upstream tracing
public class Source_tracer implements Tracer
 {
    private boolean depth_first;

    public Source_tracer() 
    { this(false); }//default is bfs

    public Source_tracer(boolean depth_first) 
    { this.depth_first = depth_first; }

    @Override
    public List<River_node> trace(River_node start)
     {
        return Trace_walk.run(start, true, depth_first);
    }
}
