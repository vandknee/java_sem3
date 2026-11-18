package model;

public class Confluence extends River_node {
    public Confluence(String id, String name, String region) {
        super(id, name, region);
    }

    @Override
    public String describe() { return "Confluence"; }

    @Override
    public double get_risk_weight() { return 1.5; }
}
