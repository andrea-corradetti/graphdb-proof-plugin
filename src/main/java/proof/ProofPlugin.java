package proof;

import com.ontotext.trree.*;
import com.ontotext.trree.sdk.Entities.Scope;
import com.ontotext.trree.sdk.*;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a plugin that can return rules and particular premises that
 * cause a particular statement to be inferred using current inferencer
 * 
 * The approach is to access the inferencer isSupported() method by providing a 
 * suitable handler that handles the reported matches by rule.
 *
 *   if we like to explain an inferred statement: 
 *
 *   PREFIX pr: http://www.ontotext.com/proof/
 *	 PREFIX onto: http://www.ontotext.com/
 *   select * {
 *   	graph onto:implicit {?s ?p ?o}
 *      ?solution pr:explain (?s ?p ?o) .
 *      ?solution pr:rule ?rulename .
 *      ?solution pr:subject ?subj .
 *      ?solution pr:predicate ?pred.
 *      ?solution pr:object ?obj .
 *      ?solution pr:context ?context .
 *   }
 * 
 * @author damyan.ognyanov
 *
 */
public class ProofPlugin extends PluginBase implements StatelessPlugin, SystemPlugin, Preprocessor, PatternInterpreter, ListPatternInterpreter {
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	// private key to store the connection in the request context


	public static final String NAMESPACE = "http://www.ontotext.com/proof/";

	public static final IRI EXPLAIN_URI = SimpleValueFactory.getInstance().createIRI(NAMESPACE+"explain");
	public static final IRI RULE_URI = SimpleValueFactory.getInstance().createIRI(NAMESPACE+"rule");
	public static final IRI SUBJ_URI = SimpleValueFactory.getInstance().createIRI(NAMESPACE+"subject");
	public static final IRI PRED_URI = SimpleValueFactory.getInstance().createIRI(NAMESPACE+"predicate");
	public static final IRI OBJ_URI = SimpleValueFactory.getInstance().createIRI(NAMESPACE+"object");
	public static final IRI CONTEXT_URI = SimpleValueFactory.getInstance().createIRI(NAMESPACE+"context");
	private final static String KEY_STORAGE = "storage";

	int contextMask = StatementIdIterator.DELETED_STATEMENT_STATUS | StatementIdIterator.SKIP_ON_BROWSE_STATEMENT_STATUS |
			StatementIdIterator.INFERRED_STATEMENT_STATUS;

	long explainId = 0;
	long ruleId = 0;
	long subjId = 0;
	long predId = 0;
	long objId = 0;
	long contextId = 0;

	/*
	 * main entry for predicate resolution of the ProvenancePlugin
	 * 
	 * (non-Javadoc)
	 * @see com.ontotext.trree.sdk.PatternInterpreter#interpret(long, long, long, long, com.ontotext.trree.sdk.Statements, com.ontotext.trree.sdk.Entities, com.ontotext.trree.sdk.RequestContext)
	 */
	@Override
	public StatementIterator interpret(long subject, long predicate, long object, long context,
									   PluginConnection pluginConnection, RequestContext requestContext) {
		
		if (predicate != explainId && predicate != ruleId && predicate != contextId &&
				predicate != subjId && predicate != predId && predicate != objId)
			return null;

		// make sure we have the proper request context set when preprocess() has been invoked
		// if not return EMPTY
		ProofContext ctx = (requestContext instanceof ProofContext)?(ProofContext)requestContext:null;

		// not our context
		if (ctx == null)
			return StatementIterator.EMPTY;
		
		if (predicate == ruleId){
			// same for the object
			ExplainIter task = (ExplainIter)ctx.getAttribute(KEY_STORAGE+subject);
			if (task == null || (task.current == null))
				return StatementIterator.EMPTY;
			// bind the value of the predicate from the current solution as object of the triple pattern
			return StatementIterator.create(task.reificationId, predicate, 
					pluginConnection.getEntities().put(SimpleValueFactory.getInstance().createLiteral(task.current.rule), Scope.REQUEST), 0);
		} else if (predicate == subjId){
			// same for the object
			ExplainIter task = (ExplainIter)ctx.getAttribute(KEY_STORAGE+subject);
			if (task == null || (task.current == null))
				return StatementIterator.EMPTY;
			if (object != 0 && task.values[0] != object)
				return StatementIterator.EMPTY;
			// bind the value of the predicate from the current solution as object of the triple pattern
			return StatementIterator.create(task.reificationId, predicate, task.values[0], 0);
		} else if (predicate == predId){
			// same for the object
			ExplainIter task = (ExplainIter)ctx.getAttribute(KEY_STORAGE+subject);
			if (task == null || (task.current == null))
				return StatementIterator.EMPTY;
			if (object != 0 && task.values[1] != object)
				return StatementIterator.EMPTY;
			// bind the value of the predicate from the current solution as object of the triple pattern
			return StatementIterator.create(task.reificationId, predicate, task.values[1], 0);
		} else if (predicate == objId){
			// same for the object
			ExplainIter task = (ExplainIter)ctx.getAttribute(KEY_STORAGE+subject);
			if (task == null || (task.current == null))
				return StatementIterator.EMPTY;
			if (object != 0 && task.values[2] != object)
				return StatementIterator.EMPTY;
			// bind the value of the predicate from the current solution as object of the triple pattern
			return StatementIterator.create(task.reificationId, predicate, task.values[2], 0);
		} else if (predicate == contextId){
			// same for the object
			ExplainIter task = (ExplainIter)ctx.getAttribute(KEY_STORAGE+subject);
			if (task == null || (task.current == null))
				return StatementIterator.EMPTY;
			if (object != 0 && task.values[3] != object)
				return StatementIterator.EMPTY;
			// bind the value of the predicate from the current solution as object of the triple pattern
			return StatementIterator.create(task.reificationId, predicate, task.values[3], 0);
		}
		
		// if the predicate is not one of the registered in the ProvenancePlugin return null 
		return null;
	}
	/**
	 * returns some cardinality values for the plugin patterns to make sure
	 * that derivedFrom is evaluated first and binds the solution designator
	 * the solution indicator is used by the assess predicates to get the subject, pred or object of the current solution
	 */
	@Override
	public double estimate(long subject, long predicate, long object, long context, PluginConnection pluginConnection,
						   RequestContext requestContext) {
		// if subject is not bound, any patttern return max value until there is some binding ad subject place
		if (subject == 0)
			return Double.MAX_VALUE;
		// explain fetching predicates
		if (predicate == ruleId || predicate == subjId|| predicate == predId || 
				predicate == objId || predicate == contextId) {
			return 1.0;
		}
		// unknown predicate??? maybe it is good to throw an exception
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
		return new ProofContext(request);

	}
	
	/**
	 * init the plugin
	 */
	@Override
	public void initialize(InitReason initReason, PluginConnection pluginConnection) {
		// register the predicates

		explainId = pluginConnection.getEntities().put(EXPLAIN_URI, Scope.SYSTEM);
		ruleId = pluginConnection.getEntities().put(RULE_URI, Scope.SYSTEM);
		subjId = pluginConnection.getEntities().put(SUBJ_URI, Scope.SYSTEM);
		predId = pluginConnection.getEntities().put(PRED_URI, Scope.SYSTEM);
		objId = pluginConnection.getEntities().put(OBJ_URI, Scope.SYSTEM);
		contextId = pluginConnection.getEntities().put(CONTEXT_URI, Scope.SYSTEM);
	}

	@Override
	public double estimate(long subject, long predicate, long[] objects, long context, 
			PluginConnection pluginConnection, RequestContext requestContext) {
		if (predicate == explainId) {
			if (objects.length != 3)
				return Double.MAX_VALUE;
			if (objects[0] == 0 || objects[1] == 0 || objects[2] == 0)
				return Double.MAX_VALUE;
			return 10L;
		}
		return Double.MAX_VALUE;
	}

	@Override
	public StatementIterator interpret(long subject, long predicate, long[] objects, long context,
            PluginConnection pluginConnection, RequestContext requestContext) {
		// make sure we have the proper request context set when preprocess() has been invoked
		// if not return EMPTY
		ProofContext proofContext = (requestContext instanceof ProofContext)?(ProofContext)requestContext:null;

		// not our context
		if (proofContext == null)
			return StatementIterator.EMPTY;
		
		if (predicate == explainId) {
			if (objects == null || objects.length < 3 || objects.length > 4)
				return StatementIterator.EMPTY;

			long subjToExplain = objects[0];
			long objToExplain = objects[1];
			long predToExplain = objects[2];
			long ctxToExplain = (objects.length == 4 ) ? objects[3] : -9999; //FIXME placeholder context.
			// empty if no binding, or some of the nodes is not a regular entity
			if (subjToExplain <= 0 || predToExplain <= 0 || objToExplain <= 0)
				return StatementIterator.EMPTY;
			// a context if an explicit exists
			long aContext = 0;
			AbstractInferencer infer = proofContext.inferencer;
			if (infer.getInferStatementsFlag() == false)
				return StatementIterator.EMPTY;

			// handle an explicit statement
			AbstractRepositoryConnection conn = proofContext.repositoryConnection;
			boolean isExplicit = false;
			boolean isDerivedFromSameAs = false;
			{
				StatementIdIterator iter = conn.getStatements(subjToExplain, objToExplain, predToExplain, StatementIdIterator.DELETED_STATEMENT_STATUS | StatementIdIterator.SKIP_ON_BROWSE_STATEMENT_STATUS | StatementIdIterator.INFERRED_STATEMENT_STATUS);
				logger.debug("iter getStatements context" + iter.context);
				try {
					isExplicit = iter.hasNext();
					aContext = iter.context;
					// handle if explicit comes from sameAs
					isDerivedFromSameAs = 0 != (iter.status & StatementIdIterator.SKIP_ON_REINFER_STATEMENT_STATUS);
				} finally {
					iter.close();
				}
			}
			// create task associated with the predicate
			// allocate a request scope id
			long reificationId = pluginConnection.getEntities().put(SimpleValueFactory.getInstance().createBNode(), Scope.REQUEST);
			
			// create a Task instance and pass the iterator of the statements from the target graph
			ExplainIter ret = new ExplainIter(this, proofContext, reificationId, subjToExplain, objToExplain, predToExplain, ctxToExplain,
					isExplicit, isDerivedFromSameAs, aContext, logger);
			// access the inferencers and the repository connection from systemoptions
			ret.infer = infer;
			ret.conn = conn;
			ret.init();
			// store the task into request context  
			proofContext.setAttribute(KEY_STORAGE+reificationId, ret);
			
			// return the newly created task instance (it is a valid StatementIterator that could be reevaluated until all solutions are 
			// generated)
			return ret;
		}
		return null;
	}

}
