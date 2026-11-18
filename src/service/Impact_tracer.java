package service;

import model.River_node;
import java.util.List;
//for downstream

public class Impact_tracer implements Tracer 
{
    private boolean depth_first;

    public Impact_tracer() 
    { this(false); }

    public Impact_tracer(boolean depth_first)
     { this.depth_first = depth_first; }

    @Override
    public List<River_node> trace(River_node start)
     {
        return Trace_walk.run(start, false, depth_first);
    }
}
