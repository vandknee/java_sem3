package service;
//manages graph of river
import model.*;
import util.Station_not_found;
import java.util.*;

public class River_network 
{
    private HashMap<String, River_node> nodes = new HashMap<>();//store id and river associated

    public void add_node(River_node node)//add node
     {
        if (nodes.containsKey(node.get_id())) 
            throw new IllegalArgumentException("Duplicate node id.");
        nodes.put(node.get_id(), node);
    }

    public River_node find_node(String id) throws Station_not_found 
    {
        River_node node = nodes.get(id);
        if (node == null) 
            throw new Station_not_found("Unknown node: " + id);
        return node;
    }

    public Monitoring_station find_station(String id) throws Station_not_found 
    {
        River_node node = find_node(id);
        if (!(node instanceof Monitoring_station)) throw new Station_not_found("Choose a monitoring station.");
        return (Monitoring_station) node;
    }
//add the edges
    public void add_link(String from, String to) throws Station_not_found
     {
        River_node first = find_node(from);
        River_node second = find_node(to);
        if (first == second) throw new IllegalArgumentException("A node cannot connect to itself.");
        first.connect_to(second);
    }

    //return rivernode sorted by id
    public List<River_node> get_nodes()
     {
        List<River_node> result = new ArrayList<>(nodes.values());
        Collections.sort(result, Comparator.comparing(River_node::get_id));
        return result;
    }

    //return the station type
    public List<Monitoring_station> get_stations()
     {
        List<Monitoring_station> result = new ArrayList<>();
        for (River_node node : get_nodes()) {
            if (node instanceof Monitoring_station) result.add((Monitoring_station) node);
        }
        return result;
    }
}
