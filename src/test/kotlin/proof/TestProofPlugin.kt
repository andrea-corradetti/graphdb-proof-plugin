package proof


import com.ontotext.graphdb.Config
import com.ontotext.test.TemporaryLocalFolder
import com.ontotext.trree.OwlimSchemaRepository
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection
import org.junit.*



class TestProofPlugin {
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


    @Before
    fun removeAllTriples() {
        connection.prepareUpdate(deleteAll).execute()
    }


    @Test
    fun checkLessieIsADog() {
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
    fun explainLessieIsAMammal() {
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

    companion object {

        private lateinit var repository: SailRepository
        private lateinit var connection: SailRepositoryConnection


        @JvmField
        @ClassRule
        val tmpFolder = TemporaryLocalFolder()


        @JvmStatic
        @BeforeClass
        fun setWorkDir() {
            System.setProperty("graphdb.home.work", "${tmpFolder.root}")
            Config.reset()
        }

        @JvmStatic
        @AfterClass
        fun resetWorkDir() {
            System.clearProperty("graphdb.home.work")
            Config.reset()
        }

        @JvmStatic
        @AfterClass
        fun cleanUp() {
            connection.close()
        }

        @JvmStatic
        @BeforeClass
        fun setUp() {
            repository = getRepository()
            connection = repository.connection
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

    }


}