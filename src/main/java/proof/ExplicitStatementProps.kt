package proof;

public class ExplicitStatementProps {
    public final long explicitContext;
    public final boolean isExplicit;
    public final boolean isDerivedFromSameAs;

    public ExplicitStatementProps(boolean isExplicit, long explicitContext, boolean isDerivedFromSameAs) {
        this.explicitContext = explicitContext;
        this.isExplicit = isExplicit;
        this.isDerivedFromSameAs = isDerivedFromSameAs;
    }
}
