package proof;

public class Quad {
    public final long subject;
    public final long predicate;
    public final long object;
    public final long context;

    public Quad(long subject, long predicate, long object, long context) {
        this.subject = subject;
        this.predicate = predicate;
        this.object = object;
        this.context = context;
    }
}
