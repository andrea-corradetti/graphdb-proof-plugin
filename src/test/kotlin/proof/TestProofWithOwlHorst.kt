package proof

import com.ontotext.graphdb.Config
import com.ontotext.test.TemporaryLocalFolder
import com.ontotext.trree.OwlimSchemaRepository
import junit.framework.TestCase.assertEquals
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection
import org.junit.*


private const val RULESET = "owl-horst"

class TestProofWithOwlHorst {
    @Before
    fun removeAllTriples() {
        connection.prepareUpdate(deleteAll).execute()
    }

    @Test
    fun `4 antecedents for mary as subject`() {
        connection.prepareUpdate(addMary).execute()
        connection.prepareUpdate(registerLNameFn).execute()
        val explainResult = connection.prepareTupleQuery(explainMaryInSubject).evaluate()

        explainResult.use { result ->
            val resultList = result.toList()
            println("statements $resultList")
            assertEquals("Query returns 4 statements", 4, resultList.count())
        }
    }

//    FIXME test behaves differently than query executed in workbench. No idea why
//    @Test
//    fun `describe Merlo`() {
//        connection.prepareUpdate(addWine).execute()
//        connection.prepareUpdate(registerLNameFn).execute()
//        connection.prepareUpdate(registerStmtFn).execute()
////        connection.prepareUpdate(registerBNodeFn).execute()
//
//        connection.prepareTupleQuery(registeredFns).evaluate().use {
//            assertEquals("2 functions are registered", 2, it.count())
//        }
//
//
//        connection.prepareGraphQuery(describeMerlo).evaluate().use { result ->
//            val resultList = result.toList()
//            println("Describe result - $resultList")
//            assertEquals("urn:Merlo describe has 6 results", 6, resultList.count())
//        }
//
//        connection.prepareUpdate(addWine).execute()
//
//
//        connection.prepareTupleQuery(explainMerlotTypeRedWine).evaluate().use { result ->
//            val resultList = result.toList()
//            println("Explain result - $resultList")
//            assertEquals("Merlo rdf:type redWine has 2 antecedents", 2, resultList.count())
//        }
//    }

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
                "ruleset" to RULESET,
//                "disable-sameAs" to "false",
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
