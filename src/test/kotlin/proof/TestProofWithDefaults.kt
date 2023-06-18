package proof

import com.ontotext.graphdb.Config
import com.ontotext.test.TemporaryLocalFolder
import com.ontotext.trree.OwlimSchemaRepository
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection
import org.eclipse.rdf4j.rio.RDFFormat
import org.junit.*
import org.slf4j.LoggerFactory
import java.io.File


class TestProofWithDefaults {
    private val logger = LoggerFactory.getLogger(this::class.java)


    @Before
    fun removeAllTriples() {
        connection.prepareUpdate(deleteAll).execute()
    }

    @Test

    fun testBasicInference() { //copied from old proof
        val fileName = object {}.javaClass.getResource("sample.trig")?.file
        connection.add(fileName?.let { File(it) }, "http://base.uri", RDFFormat.TRIG)
        connection.add(OWL.CLASS, RDFS.SUBCLASSOF, RDFS.CLASS)
        connection.prepareTupleQuery(explainFood).evaluate().use { result ->
            val ctxs = HashSet<Value>()
            var count = 0
            while (result.hasNext()) {
                val bs = result.next()
                val cB = bs.getBinding("ctx")
                Assert.assertNotNull("Expected object to be always bound", cB)
                Assert.assertNotNull("Expected object to be not null", cB.value)
                ctxs.add(cB.value)
                count++
            }
            Assert.assertEquals("total iterations", 12, ctxs.size.toLong())
            Assert.assertEquals("total results", 16, count.toLong())
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
//                "ruleset" to RULESET,
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
