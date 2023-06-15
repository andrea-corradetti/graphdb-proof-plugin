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
                println("bindingSet to string $bindingSet")
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
    fun `12 antecedents for mary as subject`() {
        connection.prepareUpdate(registerLNameFn).execute()
        connection.prepareUpdate(addMary).execute()
        val explainResult = connection.prepareTupleQuery(explainMaryInSubject).evaluate()
        explainResult.use { result ->
            val resultList = result.toList()
            assertEquals("Query returns 12 statements", 12, resultList.count())
        }
    }


    fun `describe Merlo`() {
        connection.prepareUpdate(addWine).execute()
        connection.prepareUpdate(registerLNameFn).execute()
        connection.prepareUpdate(registerStmtFn).execute()
        connection.prepareUpdate(registerBNodeFn).execute()


        connection.prepareGraphQuery(describeMerlo).evaluate().use { result ->
            val resultList = result.toList()
            println("Describe result - $resultList")
            assertEquals("urn:Merlo describe has 6 results", 6, resultList.count())
        }

        connection.prepareTupleQuery(explainMerlotTypeRedWine).evaluate().use { result ->
            val resultList = result.toList()
            println("Explain result - $resultList")
            assertEquals("Merlo rdf:type redWine has 2 antecedents", 2, resultList.count())
        }
    }

    @Test
    fun `Lassie has antecedents in correct named graph`() {
        connection.prepareUpdate(insertLassieNg).execute()
        connection.prepareTupleQuery(explain(":Lassie", "rdf:type", ":Mammal", ":G1")).evaluate().use {
            val resultList = it.toList()
            println("antecedents - $resultList")
            assertEquals("Result has 2 antecedents", 2, resultList.count())

            val isAntecedentFromG1 = resultList.any { bindingSet ->
                bindingSet.getBinding("context").value.stringValue() == "http://www.example.com/G1"
            }

            assertTrue("Antecedent is in :G1", isAntecedentFromG1)
        }

        connection.prepareTupleQuery(explain(":Lassie", "rdf:type", ":Mammal", ":G2")).evaluate().use {
            val resultList = it.toList()
            println("antecedents - $resultList")
            assertEquals("Result has 2 antecedents", 2, resultList.count())

            val isAntecedentFromG1 = resultList.any { bindingSet ->
                bindingSet.getBinding("context").value.stringValue() == "http://www.example.com/G2"
            }

            assertTrue("Antecedent is in :G1", isAntecedentFromG1)
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
        fun setUp() {
            setWorkDir()
            val sailParams = mapOf(
                "register-plugins" to proof.ProofPlugin::class.qualifiedName as String,
                "ruleset" to "owl2-rl",
            )
            repository = getRepository(sailParams)
            connection = repository.connection
        }

        @JvmStatic
        fun setWorkDir() {
            System.setProperty("graphdb.home.work", "${tmpFolder.root}")
            Config.reset()
        }

        private fun getRepository(sailParams: Map<String, String>): SailRepository {
            val sail = OwlimSchemaRepository().apply { setParameters(sailParams) }
            return SailRepository(sail).apply {
                dataDir = tmpFolder.newFolder("proof-plugin-explain-${sailParams["ruleset"]}"); init()
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