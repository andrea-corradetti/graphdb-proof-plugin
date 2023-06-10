package proof


import com.ontotext.graphdb.Config
import com.ontotext.test.TemporaryLocalFolder
import com.ontotext.trree.OwlimSchemaRepository
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection
import org.junit.*


class TestProofWithOwl2RL {
    @Before
    fun removeAllTriples() {
        connection.prepareUpdate(deleteAll).execute()
    }


    @Test
    fun `Lessie is a dog is inserted correctly`() {
        connection.prepareUpdate(addLessie).execute()
        val explainResult = connection.prepareTupleQuery(selectLassieIsDog).evaluate()
        explainResult.use {
            val resultList = it.toList()

            assertEquals(1, resultList.count())
            println("listResult = $resultList")

            resultList.forEachIndexed { index, bindingSet ->
                println("Result $index")
                bindingSet.forEach { binding -> println("${binding.name} = ${binding.value}") }
            }
        }

    }

    @Test
    fun `Lessie is a mammal has the right antecedents`() {
        connection.prepareUpdate(addLessie).execute()
        val explainResult = connection.prepareTupleQuery(explainLessie).evaluate()
        explainResult.use {
            val resultList = it.toList()
            assertEquals("Statement has exactly 2 antecedents", 2, resultList.count())

            resultList.forEachIndexed { index, bindingSet ->
                println("result $index")
                bindingSet.forEach { binding -> println("${binding.name} = ${binding.value}") }
            }

            val dogSubclassMammal = mapOf(
                "rule" to "rule_cax_sco",
                "s" to "http://www.example.com/Dog",
                "p" to "http://www.w3.org/2000/01/rdf-schema#subClassOf",
                "o" to "http://www.example.com/Mammal",
                "context" to "http://www.ontotext.com/explicit",
            )

            val dogSubclassMammalIsAntecedent = resultList.any { bindingSet ->
                dogSubclassMammal.all { (key, value) -> bindingSet.getBinding(key).value.stringValue() == value }
            }

            val lassieTypeDog = mapOf(
                "rule" to "rule_cax_sco",
                "s" to "http://www.example.com/Lassie",
                "p" to "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
                "o" to "http://www.example.com/Dog",
                "context" to "http://www.ontotext.com/explicit",
            )

            val lassieTypeDogIsAntecedent = resultList.any { bindingSet ->
                lassieTypeDog.all { (key, value) -> bindingSet.getBinding(key).value.stringValue() == value }
            }

            assertTrue("Dog subclass Mammal is antecedent", dogSubclassMammalIsAntecedent)
            assertTrue("Lassie type Dog is antecedent", lassieTypeDogIsAntecedent)
        }

    }

    @Test
    fun `Mary has child in named graph`() {
        connection.prepareUpdate(addMary).execute()
        val explainResult = connection.prepareTupleQuery(explainMary).evaluate()
        explainResult.use { result ->
            val resultList = result.toList()
            assertEquals("Statement has exactly 2 antecedents", 2, resultList.count())

            resultList.forEachIndexed { index, bindingSet ->
                println("result $index")
                bindingSet.forEach { binding -> println("${binding.name} = ${binding.value}") }
            }

            val bindingsMap = mapOf(
                "rule" to "rule_prp_inv1",
                "s" to "urn:John",
                "p" to "urn:childOf",
                "o" to "urn:Mary",
                "context" to "urn:family",
            )

            val isAntecedentInNamedGraph = resultList.any { bindingSet ->
                bindingsMap.all { (key, value) -> bindingSet.getBinding(key).value.stringValue() == value }
            }

            assertTrue("John child of mary is in context urn:family", isAntecedentInNamedGraph)
        }
    }

    @Test
    fun `Antecedent has explicit rule`() {
        connection.prepareUpdate(addMary).execute()
        val explainResult = connection.prepareTupleQuery(explainMaryExplicit).evaluate()
        explainResult.use { result ->
            val resultList = result.toList()
            assertEquals("Statement has exactly 1 antecedent", 1, resultList.count())

            resultList.forEachIndexed { index, bindingSet ->
                println("result $index")
                bindingSet.forEach { binding -> println("${binding.name} = ${binding.value}") }
            }

            val bindingsMap = mapOf(
                "rule" to "explicit",
                "s" to "urn:John",
                "p" to "urn:childOf",
                "o" to "urn:Mary",
                "context" to "urn:family",
            )

            val isAntecedentExplicit = resultList.any { bindingSet ->
                bindingsMap.all { (key, value) -> bindingSet.getBinding(key).value.stringValue() == value }
            }

            assertTrue("John child of mary is explicit", isAntecedentExplicit)
        }

    }

    @Test
    fun `mary is subject of 12 statements`() {
        connection.prepareUpdate(registerJSFn).execute()
        connection.prepareUpdate(addMary).execute()
        val explainResult = connection.prepareTupleQuery(explainMaryInSubject).evaluate()
        explainResult.use { result ->
            val resultList = result.toList()
            assertEquals("Query returns threes statements", 12, resultList.count())
        }
    }

    private val deleteAll = """
        DELETE {?s ?p ?o} where {
                ?s ?p ?o .
        }
    """.trimIndent()

    private val addLessie = """
         PREFIX : <http://www.example.com/>
        
         INSERT DATA {
            :Lassie rdf:type :Dog.
            :Dog rdfs:subClassOf :Mammal.
        }
    """.trimIndent()

    private val selectLassieIsDog = """
        PREFIX : <http://www.example.com/>
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        Select ?subject ?predicate ?object WHERE {
          ?subject ?predicate ?object .
          FILTER (?subject = :Lassie && ?predicate = rdf:type  && ?object = :Dog)
        }
    """.trimIndent()

    private val explainLessie = """
        PREFIX : <http://www.example.com/>
        PREFIX t: <http://www.example.com/tbox/>
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        prefix proof: <http://www.ontotext.com/proof/>

        select ?rule ?s ?p ?o ?context where {
            values (?subject ?predicate ?object) {(:Lassie rdf:type :Mammal)}
            ?ctx proof:explain (?subject ?predicate ?object) .
            ?ctx proof:rule ?rule .
            ?ctx proof:subject ?s .
            ?ctx proof:predicate ?p .
            ?ctx proof:object ?o .
            ?ctx proof:context ?context .
        }
    """.trimIndent()

    private val addMary = """
        INSERT DATA {
            <urn:childOf> owl:inverseOf <urn:hasChild> .
            graph <urn:family> {
                <urn:John> <urn:childOf> <urn:Mary>
            }
        }
    """.trimIndent()

    private val explainMary = """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX proof: <http://www.ontotext.com/proof/>
        SELECT ?rule ?s ?p ?o ?context WHERE {
            VALUES (?subject ?predicate ?object) {(<urn:Mary> <urn:hasChild> <urn:John>)}
            ?ctx proof:explain (?subject ?predicate ?object) .
            ?ctx proof:rule ?rule .
            ?ctx proof:subject ?s .
            ?ctx proof:predicate ?p .
            ?ctx proof:object ?o .
            ?ctx proof:context ?context .
        }
    """.trimIndent()

    private val explainMaryExplicit = """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX proof: <http://www.ontotext.com/proof/>
        SELECT ?rule ?s ?p ?o ?context WHERE {
            VALUES (?subject ?predicate ?object) {(<urn:John> <urn:childOf> <urn:Mary>)}
            ?ctx proof:explain (?subject ?predicate ?object) .
            ?ctx proof:rule ?rule .
            ?ctx proof:subject ?s .
            ?ctx proof:predicate ?p .
            ?ctx proof:object ?o .
            ?ctx proof:context ?context .
        }
    """.trimIndent()

    private val registerJSFn = """
        PREFIX jsfn:<http://www.ontotext.com/js#>
        INSERT DATA {
            [] jsfn:register '''
            function lname(value) {
             if(value instanceof org.eclipse.rdf4j.model.IRI)
                 return value.getLocalName();
             else
                 return ""+value;
            }
        '''
        }
    """.trimIndent()

    private val explainMaryInSubject = """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX onto: <http://www.ontotext.com/>
        prefix proof: <http://www.ontotext.com/proof/>
        PREFIX jsfn: <http://www.ontotext.com/js#>
        SELECT (concat('(',jsfn:lname(?subject),',',jsfn:lname(?predicate),',',jsfn:lname(?object),')') as ?stmt)
            ?rule ?s ?p ?o ?context
        WHERE {
            bind(<urn:Mary> as ?subject) .
            {?subject ?predicate ?object}

            ?ctx proof:explain (?subject ?predicate ?object) .
            ?ctx proof:rule ?rule .
            ?ctx proof:subject ?s .
            ?ctx proof:predicate ?p .
            ?ctx proof:object ?o .
            ?ctx proof:context ?context .
        }
    """.trimIndent()

    companion object {
        private lateinit var repository: SailRepository
        private lateinit var connection: SailRepositoryConnection

        @JvmField
        @ClassRule
        val tmpFolder = TemporaryLocalFolder()

        @JvmStatic
        @BeforeClass
        fun setUp() {
            setWorkDir()
            repository = getRepository()
            connection = repository.connection
        }

        @JvmStatic
        fun setWorkDir() {
            System.setProperty("graphdb.home.work", "${tmpFolder.root}")
            Config.reset()
        }

        private fun getRepository(): SailRepository {
            val sailParams = mapOf(
                "register-plugins" to proof.ProofPlugin::class.qualifiedName as String,
                "ruleset" to "owl2-rl",
            )
            val sail = OwlimSchemaRepository().apply { setParameters(sailParams) }
            return SailRepository(sail).apply {
                dataDir = tmpFolder.newFolder("proof-plugin-explain"); init()
            }
        }

        @JvmStatic
        @AfterClass
        fun cleanUp() {
            resetWorkDir()
            connection.close()
            repository.shutDown()
        }

        @JvmStatic
        fun resetWorkDir() {
            System.clearProperty("graphdb.home.work")
            Config.reset()
        }

    }
}