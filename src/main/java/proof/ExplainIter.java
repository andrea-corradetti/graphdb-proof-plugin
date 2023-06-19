package proof;

import com.ontotext.trree.*;
import com.ontotext.trree.query.QueryResultIterator;
import com.ontotext.trree.query.StatementSource;
import com.ontotext.trree.sdk.StatementIterator;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;

class ExplainIter extends StatementIterator implements ReportSupportedSolution {

    private final Logger logger;

    AbstractInferencer inferencer;
    AbstractRepositoryConnection repositoryConnection; // connection to the raw data to get only the AXIOM statements ???

    private final Quad statementToExplain;

    long reificationId; //id of bnode representing the explain operation

    boolean isExplicit;
    boolean isDerivedFromSameAs;
    long explicitContext; //FIXME possibly misguiding name

    ArrayList<Solution> solutions = new ArrayList<>();
    Iterator<Solution> iter;
    Solution current = null;
    int currentNo = -1;
    long[] values = null;

    public ExplainIter(ProofContext proofContext, long reificationId, long explainId, Quad statementToExplain, ExplicitStatementProps explicitStatementProps) {
        this.reificationId = reificationId;
        this.subject = this.reificationId;
        this.predicate = explainId;

        this.statementToExplain = statementToExplain;

        this.isExplicit = explicitStatementProps.isExplicit;
        this.isDerivedFromSameAs = explicitStatementProps.isDerivedFromSameAs;
        this.explicitContext = explicitStatementProps.explicitContext;
        this.inferencer = proofContext.inferencer;
        this.repositoryConnection = proofContext.repositoryConnection;
        this.logger = proofContext.logger;
        this.init();
    }

    public void init() {
        if (isExplicit) {
            ArrayList<long[]> arr = new ArrayList<>();
            arr.add(new long[]{statementToExplain.subject, statementToExplain.predicate, statementToExplain.object, explicitContext});
            current = new Solution("explicit", arr);
            currentNo = 0;
            iter = getEmptySolutionIterator();
        } else {
            inferencer.isSupported(statementToExplain.subject, statementToExplain.predicate, statementToExplain.object, 0, 0, this);
            iter = solutions.iterator();
            if (iter.hasNext()) current = iter.next();
            if (current != null) {
                currentNo = 0;
            }
        }
    }

    @NotNull
    @Contract(value = " -> new", pure = true)
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

                boolean isSelfReferential = false;
                ArrayList<long[]> antecedents = new ArrayList<>();

                while (sol.hasNext()) {
                    StatementIdIterator antecedent = sol.next();
                    logger.debug("Iter default context = " + antecedent.context);

                    // try finding an existing explicit or in-context with same subj, pred and obj
                    try (StatementIdIterator ctxIter = repositoryConnection.getStatements(antecedent.subj, antecedent.pred, antecedent.obj, true, 0, ProofPlugin.excludeDeletedHiddenInferred)) {
                        logger.debug(String.format("Contexts for %d %d %d", antecedent.subj, antecedent.pred, antecedent.obj));
                        if (statementToExplain.context == -9999) { //normal proof behaviour
                            while (ctxIter.hasNext()) {
                                logger.debug(String.valueOf(ctxIter.context));
                                if (ctxIter.context != SystemGraphs.EXPLICIT_GRAPH.getId()) {
                                    antecedent.context = ctxIter.context;
                                    antecedent.status = ctxIter.status;
                                    break;
                                }
                                ctxIter.next();
                            }
                        } else {
                            while (ctxIter.hasNext()) {
                                logger.debug(String.valueOf(ctxIter.context));
                                if (ctxIter.context == statementToExplain.context) {
                                    antecedent.context = ctxIter.context;
                                    antecedent.status = ctxIter.status;
                                    break;
                                }
                                ctxIter.next();
                            }
                        }
                    }

                    if (antecedent.subj == statementToExplain.subject && antecedent.pred == statementToExplain.predicate && antecedent.obj == statementToExplain.object) {
                        isSelfReferential = true;
                        break;
                    }
                    antecedents.add(new long[]{antecedent.subj, antecedent.pred, antecedent.obj, antecedent.context, antecedent.status});
                }

                Solution solution = new Solution(ruleName, antecedents);
                logger.debug("isSelfReferential {} for solution {}", isSelfReferential, solution);
                if (!isSelfReferential) {
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
                if (iter.hasNext()) current = iter.next();
                else current = null;
            }
        }
        return false;
    }

    @Override
    public AbstractRepositoryConnection getConnection() {
        return repositoryConnection;
    }

}
