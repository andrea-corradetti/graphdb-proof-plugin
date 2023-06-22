package proof;

import com.ontotext.trree.*;
import com.ontotext.trree.query.QueryResultIterator;
import com.ontotext.trree.query.StatementSource;
import com.ontotext.trree.sdk.StatementIterator;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

class ExplainIter extends StatementIterator implements ReportSupportedSolution {

    private final Logger logger;
    private final Quad statementToExplain;
    AbstractInferencer inferencer;
    AbstractRepositoryConnection repositoryConnection; // connection to the raw data to get only the AXIOM statements ???
    long reificationId; //id of bnode representing the explain operation

    boolean isExplicit;
    boolean isDerivedFromSameAs;
    long explicitContext; //FIXME possibly misguiding name

    Set<Solution> solutions = new LinkedHashSet<>();
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
            if (iter.hasNext()) {
                current = iter.next();
            }
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
    public boolean report(String ruleName, QueryResultIterator queryResultIterator) {
        logger.debug("report rule {} for {},{},{}", ruleName, statementToExplain.subject, statementToExplain.predicate, statementToExplain.object);
        List<Quad> antecedents = new ArrayList<>();
        while (queryResultIterator.hasNext()) {
            if (queryResultIterator instanceof StatementSource) {
                StatementSource source = (StatementSource) queryResultIterator;
                Iterator<StatementIdIterator> sourceSolutionIterator = source.solution();

                while (sourceSolutionIterator.hasNext()) {
                    try (StatementIdIterator antecedent = sourceSolutionIterator.next()) {
                        logger.debug("Iter default context = " + antecedent.context);

                        boolean isSelfReferential = (antecedent.subj == statementToExplain.subject && antecedent.pred == statementToExplain.predicate && antecedent.obj == statementToExplain.object);
                        if (isSelfReferential) {
                            logger.debug("not added - self referential");
                            continue;
                        }

                        List<Quad> antecedentsWithAllContexts = getAntecedentWithAllContexts(antecedent);

                        boolean isStatementInSameContext = antecedentsWithAllContexts.stream().anyMatch(quad -> quad.context == statementToExplain.context);
                        if (isStatementInSameContext) {
                            logger.debug("statement is same context {}", statementToExplain.context);
                            Quad toAdd = new Quad(antecedent.subj, antecedent.pred, antecedent.obj, statementToExplain.context, antecedent.status);
                            antecedents.add(toAdd);
                        }

                        boolean isStatementInDefaultGraph = antecedentsWithAllContexts.stream().anyMatch(quad -> quad.context == SystemGraphs.EXPLICIT_GRAPH.getId());
                        if (isStatementInDefaultGraph) {
                            logger.debug("statement is in default graph");
                            Quad toAdd = new Quad(antecedent.subj, antecedent.pred, antecedent.obj, SystemGraphs.EXPLICIT_GRAPH.getId(), antecedent.status);
                            antecedents.add(toAdd);
                        }

                        boolean isStatementOutOfScope = !isStatementInSameContext && !isStatementInDefaultGraph;

                        boolean isStatementOnlyImplicit = isStatementOutOfScope && antecedentsWithAllContexts.stream().anyMatch(quad -> quad.context == SystemGraphs.IMPLICIT_GRAPH.getId());
                        if (isStatementOnlyImplicit) {
                            logger.debug("statement is only implicit");
                            antecedents.add(new Quad(antecedent.subj, antecedent.pred, antecedent.obj, SystemGraphs.IMPLICIT_GRAPH.getId(), antecedent.status));
                        }

                        logger.debug("Saved antecedents " + antecedents);
                    }
                }
            }
            queryResultIterator.next();
        }

        boolean areAllAntecedentsImplicit = antecedents.stream().allMatch(quad -> quad.context == SystemGraphs.IMPLICIT_GRAPH.getId());
        if (areAllAntecedentsImplicit) {
            logger.debug("All antecedents are implicit");
            antecedents = new ArrayList<>();
        }

        if (!antecedents.isEmpty()) {
            List<long[]> antecedentsAsArrays = antecedents.stream().map(quad -> new long[]{quad.subject, quad.predicate, quad.object, quad.context}).collect(Collectors.toList());
            Solution solution = new Solution(ruleName, antecedentsAsArrays);

            if (!solutions.contains(solution)) {
                logger.debug("added");
                solutions.add(solution);
            } else {
                logger.debug("already added");
            }
        }
        return false;
    }

    private List<Quad> getAntecedentWithAllContexts(StatementIdIterator antecedent) {
        ArrayList<Quad> antecedentsWithAllContexts = new ArrayList<>();
        try (StatementIdIterator ctxIter = repositoryConnection.getStatements(antecedent.subj, antecedent.pred, antecedent.obj, true, 0, ProofPlugin.excludeDeletedHiddenInferred)) {
            logger.debug(String.format("Contexts for %d %d %d", antecedent.subj, antecedent.pred, antecedent.obj));
            while (ctxIter.hasNext()) {
                antecedentsWithAllContexts.add(new Quad(ctxIter.subj, ctxIter.pred, ctxIter.obj, ctxIter.context, ctxIter.status));
                logger.debug(String.valueOf(ctxIter.context));
                ctxIter.next();
            }
            logger.debug("All contexts for antecedent" + antecedentsWithAllContexts);
        }
        return antecedentsWithAllContexts;
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
                if (iter.hasNext()) {
                    current = iter.next();
                } else {
                    current = null;
                }
            }
        }
        return false;
    }

    @Override
    public AbstractRepositoryConnection getConnection() {
        return repositoryConnection;
    }

}
