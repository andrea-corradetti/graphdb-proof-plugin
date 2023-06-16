package proof;

import com.ontotext.trree.*;
import com.ontotext.trree.query.QueryResultIterator;
import com.ontotext.trree.query.StatementSource;
import com.ontotext.trree.sdk.StatementIterator;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;

class ExplainIter extends StatementIterator implements ReportSupportedSolution {
    private final ProofPlugin proofPlugin;

    private final Logger logger;

    public long ctxToExplain;

    // the request context that stores the instance and the options for that iterator (current inferencer, repository connection etc)
    ProofContext proofContext;
    // the key assigned to that instance to it can be retrieved from the context
    String key;
    // this the the Value(Request scoped bnode) designating the currently running instance (used to fetch the task from the context if multiple instances are
    // evaluated within same query0
    long reificationId;
    // instance of the inference to work with
    AbstractInferencer infer;
    // connection to the raw data to get only the AXIOM statements
    AbstractRepositoryConnection conn;
    long subjToExplain, predToExplain, objToExplain;
    boolean isExplicit = false;
    boolean isDerivedFromSameAs = false;
    long aContext = 0;
    ArrayList<Solution> solutions = new ArrayList<Solution>();
    Iterator<Solution> iter;
    Solution current = null;
    int currentNo = -1;
    long[] values = null;

    public ExplainIter(ProofPlugin proofPlugin, ProofContext proofContext, long reificationId2, long subjToExplain, long predToExplain, long objToExplain, long ctxToExplain, boolean isExplicit,
                       boolean isDerivedFromSameAs, long aContext, Logger logger) {
        this.proofPlugin = proofPlugin;
        this.proofContext = proofContext;
        reificationId = reificationId2;
        this.subjToExplain = subjToExplain;
        this.predToExplain = predToExplain;
        this.objToExplain = objToExplain;
        this.isExplicit = isExplicit;
        this.isDerivedFromSameAs = isDerivedFromSameAs;
        this.aContext = aContext;
        this.subject = reificationId;
        this.predicate = proofPlugin.explainId;
        this.logger = logger;
        this.ctxToExplain = ctxToExplain;
    }

    public void init() {
        if (!isExplicit) {
            infer.isSupported(subjToExplain, predToExplain, objToExplain, 0, 0, this);
            iter = solutions.iterator();
            if (iter.hasNext())
                current = iter.next();
            if (current != null) {
                currentNo = 0;
            }
        } else {
            ArrayList<long[]> arr = new ArrayList<long[]>();
            arr.add(new long[]{subjToExplain, predToExplain, objToExplain, aContext});
            current = new Solution("explicit", arr);
            currentNo = 0;
            iter = new Iterator<Solution>() {

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
    }

    @Override
    public boolean report(String ruleName, QueryResultIterator q) {
        logger.debug("report rule {} for {},{},{}", ruleName, this.subjToExplain, this.predToExplain, this.objToExplain);
        while (q.hasNext()) {
            if (q instanceof StatementSource) {
                StatementSource source = (StatementSource) q;
                Iterator<StatementIdIterator> sol = source.solution();
                boolean isSame = false;
                ArrayList<long[]> aSolution = new ArrayList<long[]>();
                while (sol.hasNext()) {
                    StatementIdIterator iter = sol.next();
                    // try finding an existing explicit or in-context with same subj, pred and obj
                    try (StatementIdIterator ctxIter = conn.getStatements(iter.subj, iter.pred, iter.obj, true, 0, proofPlugin.excludeDeletedHiddenInferred)) {
                        logger.debug(String.format("Contesti di %d %d %d", iter.subj, iter.pred, iter.obj));
                        if (ctxToExplain == -9999) { //normal proof behaviour
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
                                if (ctxIter.context == ctxToExplain) {
                                    iter.context = ctxIter.context;
                                    iter.status = ctxIter.status;
                                    break;
                                }
                                ctxIter.next();
                            }
                        }

                    }
                    if (iter.subj == this.subjToExplain && iter.pred == this.predToExplain && iter.obj == this.objToExplain) {
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
        return conn;
    }

}
