package model;

public class Reservoir extends River_node {
    public Reservoir(String id, String name, String region) {
        super(id, name, region);
    }

    @Override
    public String describe() { return "Reservoir"; }

    @Override
    public double get_risk_weight() { return 1.3; }
}
