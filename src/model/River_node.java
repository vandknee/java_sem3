package model;
//abstract class work as interface
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class River_node //store info about the river location
{
    private String id;
    private String name;
    private String region;
    private List<River_node> upstream = new ArrayList<>();
    private List<River_node> downstream = new ArrayList<>();

    public River_node(String id, String name, String region) {
        this.id = id;
        this.name = name;
        this.region = region;
    }

    public String get_id()
     { return id; }
    public String get_name()
     { return name; }
    public String get_region()
     { return region; }
    public List<River_node> get_upstream() 
    { return Collections.unmodifiableList(upstream); }
    public List<River_node> get_downstream() 
    { return Collections.unmodifiableList(downstream);//other parts can just see the list not modified

     }

    public void connect_to(River_node next) //connect the downstream and upstream
    {
        if (!downstream.contains(next))
       {
            downstream.add(next);//add next as downstream and other as it upstream
            next.upstream.add(this);
        }
    }

    public abstract String describe();
    public abstract double get_risk_weight();
}
