package model;

public class Tributary extends River_node
 {
    public Tributary(String id, String name, String region) {
        super(id, name, region);
    }

    @Override
    public String describe() 
    { return "Tributary"; }

    @Override
    public double get_risk_weight() 
    { return 1.1; }
}
