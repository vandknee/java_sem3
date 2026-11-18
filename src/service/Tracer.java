package service;
//interface for tracing
import model.River_node;
import java.util.List;

public interface Tracer {
    List<River_node> trace(River_node start);
}
