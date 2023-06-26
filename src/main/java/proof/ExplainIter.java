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
    Solution currentSolution = null;
    int currentPremiseNo = -1;
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
            long[] antecedent = {statementToExplain.subject, statementToExplain.predicate, statementToExplain.object, explicitContext};
            currentSolution = new Solution("explicit", List.of(antecedent));
            currentPremiseNo = 0;
            iter = getEmptySolutionIterator();
        } else {
            inferencer.isSupported(statementToExplain.subject, statementToExplain.predicate, statementToExplain.object, 0, 0, this); //this method has a side effect that prompts the overridden report() method to populate our solution with antecedents. The strange this reference does exactly that. Sorry, I don't make the rules. No idea what the fourth parameter does. Good luck
            iter = solutions.iterator();
            if (iter.hasNext()) {
                currentSolution = iter.next();
            }
            if (currentSolution != null) {
                currentPremiseNo = 0;
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
        Set<Quad> antecedents = new HashSet<>();
        while (queryResultIterator.hasNext()) {
            if (queryResultIterator instanceof StatementSource source) {
                Iterator<StatementIdIterator> sourceSolutionIterator = source.solution();

                while (sourceSolutionIterator.hasNext()) {
                    try (StatementIdIterator antecedent = sourceSolutionIterator.next()) {
                        boolean isSelfReferential = (antecedent.subj == statementToExplain.subject && antecedent.pred == statementToExplain.predicate && antecedent.obj == statementToExplain.object);
                        if (isSelfReferential) {
                            logger.debug("skipped - self referential");
                            continue;
                        }

                        logger.debug("Iter default context = " + antecedent.context);

                        Set<Quad> antecedentWithAllContexts = getAntecedentWithAllContexts(antecedent);
                        logger.debug("antecedents with all contexts " + antecedentWithAllContexts);

                        boolean isStatementInSameContext = antecedentWithAllContexts.stream().anyMatch(quad -> quad.context == statementToExplain.context);
                        if (isStatementInSameContext) {
                            logger.debug("statement is same context {}", statementToExplain.context);
                            antecedents.add(new Quad(antecedent.subj, antecedent.pred, antecedent.obj, statementToExplain.context, antecedent.status));
                        }

                        boolean isStatementInDefaultGraph = !isStatementInSameContext && antecedentWithAllContexts.stream().anyMatch(quad -> quad.context == SystemGraphs.EXPLICIT_GRAPH.getId());
                        if (ProofPlugin.isSharedKnowledgeInDefaultGraph && isStatementInDefaultGraph) {
                            logger.debug("statement is in default graph");
                            antecedents.add(new Quad(antecedent.subj, antecedent.pred, antecedent.obj, SystemGraphs.EXPLICIT_GRAPH.getId(), antecedent.status));
                        }

                        boolean isStatementInScope = isStatementInSameContext || (ProofPlugin.isSharedKnowledgeInDefaultGraph && isStatementInDefaultGraph);

                        boolean isStatementOnlyImplicit = !isStatementInScope && antecedentWithAllContexts.stream().allMatch(quad -> quad.context == SystemGraphs.IMPLICIT_GRAPH.getId());
                        if (isStatementOnlyImplicit) {
                            logger.debug("statement is only implicit");
                            antecedents.add(new Quad(antecedent.subj, antecedent.pred, antecedent.obj, SystemGraphs.IMPLICIT_GRAPH.getId(), antecedent.status));
                        }

                        if (!isStatementInScope && !isStatementOnlyImplicit) {
                            logger.debug("statement {},{},{} is out of scope", antecedent.subj, antecedent.pred, antecedent.obj);
                            return false;
                        }
                        logger.debug("Saved antecedents " + antecedents);
                    }
                }
            }
            queryResultIterator.next();
        }

//        boolean areAllAntecedentsImplicit = antecedents.stream().allMatch(quad -> quad.context == SystemGraphs.IMPLICIT_GRAPH.getId());
//        if (areAllAntecedentsImplicit) {
//            logger.debug("All antecedents are implicit");
//            return false;
//        }

        List<long[]> antecedentsAsArrays = antecedents.stream().map(quad -> new long[]{quad.subject, quad.predicate, quad.object, quad.context}).collect(Collectors.toList());
        Solution solution = new Solution(ruleName, antecedentsAsArrays);
        boolean added = solutions.add(solution);
        logger.debug(added ? "added" : "already added");
        return false;
    }

    private Set<Quad> getAntecedentWithAllContexts(StatementIdIterator antecedent) {
        Set<Quad> antecedentsWithAllContexts = new HashSet<>();
        antecedentsWithAllContexts.add(new Quad(antecedent.subj, antecedent.pred, antecedent.obj, antecedent.context, antecedent.status));
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
        currentSolution = null;
        solutions = null;
    }


    @Override
    public boolean next() {
        while (currentSolution != null) {
            if (currentPremiseNo < currentSolution.premises.size()) {
                values = currentSolution.premises.get(currentPremiseNo++);
                return true;
            }

            values = null;
            currentPremiseNo = 0;
            currentSolution = iter.hasNext() ? iter.next() : null;
        }
        return false;
    }

    @Override
    public AbstractRepositoryConnection getConnection() {
        return repositoryConnection;
    }

}
