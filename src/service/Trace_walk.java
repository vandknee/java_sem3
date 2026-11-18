package service;

import model.River_node;
import java.util.*;

public class Trace_walk 
{
    public static List<River_node> run(River_node start, boolean upstream, boolean depth_first) 
    {
        List<River_node> result = new ArrayList<>();//store node found during transversal
        Set<String> visited = new HashSet<>();//id discovered
        visited.add(start.get_id());//first id get added
        if (depth_first)
         {
            visit(start, upstream, visited, result);
        } else//bfs implementation
             {
            Queue<River_node> queue = new LinkedList<>();
            queue.add(start);
            while (!queue.isEmpty())
                 {
                River_node current = queue.remove();
                List<River_node> neighbours = upstream ? current.get_upstream() : current.get_downstream();
                for (River_node next : neighbours)
                     {
                    if (visited.add(next.get_id())) 
                        {
                        result.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        return result;
    }

    private static void visit(River_node current, boolean upstream, Set<String> visited, List<River_node> result) {
        List<River_node> neighbours = upstream ? current.get_upstream() : current.get_downstream();
        for (River_node next : neighbours) {
            if (visited.add(next.get_id())) {
                result.add(next);
                visit(next, upstream, visited, result);
            }
        }
    }
}
