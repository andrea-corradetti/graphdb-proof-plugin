package proof;

import com.ontotext.trree.StatementIdIterator;
import com.ontotext.trree.SystemGraphs;
import com.ontotext.trree.sdk.*;
import com.ontotext.trree.sdk.Entities.Scope;
import org.eclipse.rdf4j.model.IRI;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.rdf4j.model.util.Values.*;


/**
 * This is a plugin that can return rules and particular premises that
 * cause a particular statement to be inferred using current inferencer
 * <p>
 * The approach is to access the inferencer isSupported() method by providing a
 * suitable handler that handles the reported matches by rule.
 * <p>
 * if we like to explain an inferred statement:
 * <p>
 * PREFIX pr: http://www.ontotext.com/proof/
 * PREFIX onto: http://www.ontotext.com/
 * select * {
 * graph onto:implicit {?s ?p ?o}
 * ?solution pr:explain (?s ?p ?o) .
 * ?solution pr:rule ?rulename .
 * ?solution pr:subject ?subj .
 * ?solution pr:predicate ?pred.
 * ?solution pr:object ?obj .
 * ?solution pr:context ?context .
 * }
 *
 * @author damyan.ognyanov
 */
public class ProofPlugin extends PluginBase implements StatelessPlugin, SystemPlugin, Preprocessor, PatternInterpreter, ListPatternInterpreter {
    public static final int excludeDeletedHiddenInferred = StatementIdIterator.DELETED_STATEMENT_STATUS | StatementIdIterator.SKIP_ON_BROWSE_STATEMENT_STATUS | StatementIdIterator.INFERRED_STATEMENT_STATUS;
    private static final String NAMESPACE = "http://www.ontotext.com/proof/";
    private static final IRI EXPLAIN_URI = iri(NAMESPACE + "explain");
    private static final IRI RULE_URI = iri(NAMESPACE + "rule");
    private static final IRI SUBJ_URI = iri(NAMESPACE + "subject");
    private static final IRI PRED_URI = iri(NAMESPACE + "predicate");
    private static final IRI OBJ_URI = iri(NAMESPACE + "object");
    private static final IRI CONTEXT_URI = iri(NAMESPACE + "context");
    private static final String KEY_STORAGE = "storage";
    private static final int UNBOUND = 0;

    public static boolean isSharedKnowledgeInDefaultGraph = true;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private long explainId;
    private long ruleId;
    private long subjId;
    private long predId;
    private long objId;
    private long contextId;

    /*
     * main entry for predicate resolution of the ProvenancePlugin
     *
     * (non-Javadoc)
     * @see com.ontotext.trree.sdk.PatternInterpreter#interpret(long, long, long, long, com.ontotext.trree.sdk.Statements, com.ontotext.trree.sdk.Entities, com.ontotext.trree.sdk.RequestContext)
     */
    @Override
    public StatementIterator interpret(long subject, long predicate, long object, long context, PluginConnection pluginConnection, RequestContext requestContext) {
        boolean shouldNotHandlePredicate = predicate != explainId && predicate != ruleId && predicate != contextId && predicate != subjId && predicate != predId && predicate != objId;
        if (shouldNotHandlePredicate) {
            return null;
        }

        ProofContext proofContext = (requestContext instanceof ProofContext) ? (ProofContext) requestContext : null;
        if (proofContext == null) {
            return StatementIterator.EMPTY;
        }

        ExplainIter explainIter = (ExplainIter) proofContext.getAttribute(KEY_STORAGE + subject);
        if (explainIter == null || (explainIter.currentSolution == null)) {
            return StatementIterator.EMPTY;
        }

        return getStatementIterator(predicate, object, pluginConnection, explainIter);
    }

    @NotNull
    private StatementIterator getStatementIterator(long predicate, long object, PluginConnection pluginConnection, ExplainIter explainIter) {
        // bind the value of the predicate from the current solution as object of the triple pattern
        if (predicate == ruleId) {
            long rule = pluginConnection.getEntities().put(literal(explainIter.currentSolution.rule), Scope.REQUEST);
            return StatementIterator.create(explainIter.reificationId, ruleId, rule, 0);
        } else if (predicate == subjId) {
            if (object != UNBOUND && object != explainIter.values[0]) return StatementIterator.EMPTY;
            return StatementIterator.create(explainIter.reificationId, subjId, explainIter.values[0], 0);
        } else if (predicate == predId) {
            if (object != UNBOUND && object != explainIter.values[1]) return StatementIterator.EMPTY;
            return StatementIterator.create(explainIter.reificationId, predId, explainIter.values[1], 0);
        } else if (predicate == objId) {
            if (object != UNBOUND && object != explainIter.values[2]) return StatementIterator.EMPTY;
            return StatementIterator.create(explainIter.reificationId, objId, explainIter.values[2], 0);
        } else if (predicate == contextId) {
            if (object != UNBOUND && object != explainIter.values[3]) return StatementIterator.EMPTY;
            return StatementIterator.create(explainIter.reificationId, contextId, explainIter.values[3], 0);
        }

        throw new PluginException("Interpret is handling the wrong predicate");
    }

    /**
     * returns some cardinality values for the plugin patterns to make sure
     * that derivedFrom is evaluated first and binds the solution designator
     * the solution indicator is used by the assess predicates to get the subject, pred or object of the current solution
     */
    @Override
    public double estimate(long subject, long predicate, long object, long context, PluginConnection pluginConnection, RequestContext requestContext) {
        // if subject is not bound, any pattern return max value until there is some binding ad subject place
        if (subject == UNBOUND) {
            return Double.MAX_VALUE;
        }
        // explain fetching predicates
        if (predicate == ruleId || predicate == subjId || predicate == predId || predicate == objId || predicate == contextId) {
            return 1.0;
        }
        // unknown predicate??? maybe it is good to throw an exception //no idea what to do about this old comment
        return Double.MAX_VALUE;
    }

    @Override
    public String getName() {
        return "proof-from-source";
    }

    /**
     * the plugin uses preprocess to register its request context and access the system options
     * where the current inferencer and repository connections are placed
     */
    @Override
    public RequestContext preprocess(Request request) {
        return new ProofContext(request, logger);
    }

    /**
     * init the plugin
     */
    @Override
    public void initialize(InitReason initReason, PluginConnection pluginConnection) {
        Entities entities = pluginConnection.getEntities();
        explainId = entities.put(EXPLAIN_URI, Scope.SYSTEM);
        ruleId = entities.put(RULE_URI, Scope.SYSTEM);
        subjId = entities.put(SUBJ_URI, Scope.SYSTEM);
        predId = entities.put(PRED_URI, Scope.SYSTEM);
        objId = entities.put(OBJ_URI, Scope.SYSTEM);
        contextId = entities.put(CONTEXT_URI, Scope.SYSTEM);
    }

    @Override
    public double estimate(long subject, long predicate, long[] objects, long context, PluginConnection pluginConnection, RequestContext requestContext) {
        if (predicate != explainId) {
            return Double.MAX_VALUE;
        }
        if (objects.length != 3) {
            return Double.MAX_VALUE;
        }
        if (objects[0] == UNBOUND || objects[1] == UNBOUND || objects[2] == UNBOUND) {
            return Double.MAX_VALUE;
        }
        return 10L;
    }

    @Override
    public StatementIterator interpret(long subject, long predicate, long[] objects, long context, PluginConnection pluginConnection, RequestContext requestContext) {
        ProofContext proofContext = (requestContext instanceof ProofContext) ? (ProofContext) requestContext : null;
        if (proofContext == null) {
            return StatementIterator.EMPTY;
        }

        if (predicate != explainId) {
            return null;
        }

        if (objects == null || objects.length < 3 || objects.length > 4) {
            return StatementIterator.EMPTY;
        }

        long subjToExplain = objects[0];
        long objToExplain = objects[1];
        long predToExplain = objects[2];
        long ctxToExplain = (objects.length == 4) ? objects[3] : SystemGraphs.EXPLICIT_GRAPH.getId(); //FIXME placeholder context.
        Quad statementToExplain = new Quad(subjToExplain, objToExplain, predToExplain, ctxToExplain, 0);

        boolean areObjectsBoundIncorrectly = subjToExplain <= 0 || predToExplain <= 0 || objToExplain <= 0;
        if (areObjectsBoundIncorrectly) {
            return StatementIterator.EMPTY;
        }

        if (!proofContext.inferencer.getInferStatementsFlag()) {
            return StatementIterator.EMPTY;
        }

        ExplicitStatementProps explicitStatementProps = getExplicitStatementProps(subjToExplain, objToExplain, predToExplain, proofContext);

        // allocate a request scope id
        long reificationId = pluginConnection.getEntities().put(bnode(), Scope.REQUEST);

        // create a Task instance and pass the iterator of the statements from the target graph
        ExplainIter ret = new ExplainIter(proofContext, reificationId, explainId, statementToExplain, explicitStatementProps);

        // store the task into request context
        proofContext.setAttribute(KEY_STORAGE + reificationId, ret);

        // return the newly created task instance (it is a valid StatementIterator that could be reevaluated until all solutions are
        // generated)
        return ret;
    }

    @NotNull
    private ExplicitStatementProps getExplicitStatementProps(long subjToExplain, long objToExplain, long predToExplain, ProofContext proofContext) {
        boolean isExplicit;
        long explicitContext;
        boolean isDerivedFromSameAs;
        StatementIdIterator iterForExplicit = proofContext.repositoryConnection.getStatements(subjToExplain, objToExplain, predToExplain, excludeDeletedHiddenInferred);
        try (iterForExplicit) {
            logger.debug("iter getStatements context" + iterForExplicit.context);
            isExplicit = iterForExplicit.hasNext();
            explicitContext = iterForExplicit.context;
            isDerivedFromSameAs = (iterForExplicit.status & StatementIdIterator.SKIP_ON_REINFER_STATEMENT_STATUS) != 0; // handle if explicit comes from sameAs
        }
        return new ExplicitStatementProps(isExplicit, explicitContext, isDerivedFromSameAs);
    }


}


