package proof;

import com.ontotext.trree.*;
import com.ontotext.trree.query.QueryResultIterator;
import com.ontotext.trree.query.StatementSource;
import com.ontotext.trree.sdk.StatementIterator;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;

class ExplainIter extends StatementIterator implements ReportSupportedSolution {

    private final Quad statementToExplain;

    private final Logger logger;

    // the key assigned to that instance to it can be retrieved from the context
    String key;
    // this the the Value(Request scoped bnode) designating the currently running instance (used to fetch the task from the context if multiple instances are
    // evaluated within same query0
    long reificationId;

    // instance of the inference to work with
    AbstractInferencer inferencer;
    // connection to the raw data to get only the AXIOM statements
    AbstractRepositoryConnection repositoryConnection;


    boolean isExplicit;
    boolean isDerivedFromSameAs;
    long aContext;
    ArrayList<Solution> solutions = new ArrayList<>();
    Iterator<Solution> iter;
    Solution current = null;
    int currentNo = -1;
    long[] values = null;

    public ExplainIter(ProofContext proofContext, long reificationId, long explainId, Quad statementToExplain, boolean isExplicit,
                       boolean isDerivedFromSameAs, long aContext) {
        this.reificationId = reificationId;
        this.subject = this.reificationId;
        this.predicate = explainId;


        this.statementToExplain = statementToExplain;

        this.isExplicit = isExplicit;
        this.isDerivedFromSameAs = isDerivedFromSameAs;
        this.aContext = aContext;
        this.inferencer = proofContext.inferencer;
        this.repositoryConnection = proofContext.repositoryConnection;
        this.logger = proofContext.logger;
        this.init();
    }

    public void init() {
        if (isExplicit) {
            ArrayList<long[]> arr = new ArrayList<>();
            arr.add(new long[]{statementToExplain.subject, statementToExplain.predicate, statementToExplain.object, aContext});
            current = new Solution("explicit", arr);
            currentNo = 0;
            iter = getEmptySolutionIterator();
        } else {
            inferencer.isSupported(statementToExplain.subject, statementToExplain.predicate, statementToExplain.object, 0, 0, this);
            iter = solutions.iterator();
            if (iter.hasNext())
                current = iter.next();
            if (current != null) {
                currentNo = 0;
            }
        }
    }

    private Iterator<Solution> getEmptySolutionIterator() {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public Solution next() {
                return null;
            }
        };
    }

    @Override
    public boolean report(String ruleName, QueryResultIterator q) {
        logger.debug("report rule {} for {},{},{}", ruleName, statementToExplain.subject, statementToExplain.predicate, statementToExplain.object);
        while (q.hasNext()) {
            if (q instanceof StatementSource) {
                StatementSource source = (StatementSource) q;
                Iterator<StatementIdIterator> sol = source.solution();
                boolean isSame = false;
                ArrayList<long[]> aSolution = new ArrayList<long[]>();
                while (sol.hasNext()) {
                    StatementIdIterator iter = sol.next();
                    // try finding an existing explicit or in-context with same subj, pred and obj
                    try (StatementIdIterator ctxIter = repositoryConnection.getStatements(iter.subj, iter.pred, iter.obj, true, 0, ProofPlugin.excludeDeletedHiddenInferred)) {
                        logger.debug(String.format("Contesti di %d %d %d", iter.subj, iter.pred, iter.obj));
                        if (statementToExplain.context == -9999) { //normal proof behaviour
                            while (ctxIter.hasNext()) {
                                logger.debug(String.valueOf(ctxIter.context));
                                if (ctxIter.context != SystemGraphs.EXPLICIT_GRAPH.getId()) {
                                    iter.context = ctxIter.context;
                                    iter.status = ctxIter.status;
                                    break;
                                }
                                ctxIter.next();
                            }
                        } else {
                            while (ctxIter.hasNext()) {
                                logger.debug(String.valueOf(ctxIter.context));
                                if (ctxIter.context == statementToExplain.context) {
                                    iter.context = ctxIter.context;
                                    iter.status = ctxIter.status;
                                    break;
                                }
                                ctxIter.next();
                            }
                        }

                    }
                    if (iter.subj == statementToExplain.subject && iter.pred == statementToExplain.predicate && iter.obj == statementToExplain.object) {
                        isSame = true;
                    }
                    aSolution.add(new long[]{iter.subj, iter.pred, iter.obj, iter.context, iter.status});
                }
                Solution solution = new Solution(ruleName, aSolution);
                logger.debug("isSelfReferential {} for solution {}", isSame, solution);
                if (!isSame) {
                    if (!solutions.contains(solution)) {
                        logger.debug("added");
                        solutions.add(solution);
                    } else {
                        logger.debug("already added");
                    }
                } else {
                    logger.debug("not added - self referential");
                }
            }
            q.next();
        }
        return false;
    }

    @Override
    public void close() {
        current = null;
        solutions = null;
    }

    @Override
    public boolean next() {
        while (current != null) {
            if (currentNo < current.premises.size()) {
                values = current.premises.get(currentNo);
                currentNo++;
                return true;
            } else {
                values = null;
                currentNo = 0;
                if (iter.hasNext())
                    current = iter.next();
                else
                    current = null;
            }
        }
        return false;
    }

    @Override
    public AbstractRepositoryConnection getConnection() {
        return repositoryConnection;
    }

}
