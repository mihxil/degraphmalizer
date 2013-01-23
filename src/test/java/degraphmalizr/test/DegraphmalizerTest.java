package degraphmalizr.test;

import com.google.inject.*;
import com.google.inject.matcher.Matchers;
import com.tinkerpop.blueprints.Graph;
import exceptions.DegraphmalizerException;
import modules.*;
import degraphmalizr.*;
import degraphmalizr.jobs.*;
import elasticsearch.LocalES;
import neo4j.CommonNeo4j;
import neo4j.EphemeralEmbeddedNeo4J;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Client;
import org.nnsoft.guice.sli4j.slf4j.Slf4jLoggingModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.fest.assertions.Assertions.assertThat;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

class LocalNode
{
    public static LocalNode localNode()
    {
        final ArrayList<Module> modules = new ArrayList<Module>();

        // some defaults
        modules.add(new BlueprintsSubgraphManagerModule());
        modules.add(new DegraphmalizerModule());
        modules.add(new ThreadpoolModule());
        modules.add(new LocalES());
        modules.add(new CommonNeo4j());
        modules.add(new EphemeralEmbeddedNeo4J());
        modules.add(new StaticJSConfModule("conf/"));
        modules.add(new Slf4jLoggingModule(Matchers.any()));

        modules.add(new LogconfModule());

        // the injector
        final Injector injector = com.google.inject.Guice.createInjector(modules);

        return injector.getInstance(LocalNode.class);
    }

    @Inject
    Degraphmalizr d;

    @Inject
    Client es;

    @Inject
    Graph G;
}

@Test
public class DegraphmalizerTest implements DegraphmalizeStatus
{
    private final static Logger log = LoggerFactory.getLogger(DegraphmalizerTest.class);

    LocalNode ln;

	@BeforeClass
	public void setUp()
	{
        ln = LocalNode.localNode();
	}

    @Test
	public void aTest() throws ExecutionException, InterruptedException, DegraphmalizerException
    {
        final String target = "test-target";
        final String idx = "test-index";
        final String tp = "test-type";
        final String id = "1234";

        // create target index if it doesn't exist
        if (!ln.es.admin().indices().prepareExists(target).execute().actionGet().exists())
        {
            final CreateIndexResponse cir = ln.es.admin().indices().prepareCreate(target).execute().actionGet();
            if (! cir.acknowledged())
                throw new RuntimeException("failed to create index " + target);
        }

        final IndexResponse ir = ln.es.prepareIndex(idx,tp,id)
                .setSource("{\"children\":[1,2,3]}").execute().actionGet();

        log.info("Indexed /{}/{}/{} as version {} into ES", new Object[] { idx, tp, id, ir.version() });

        final IndexResponse ir1 = ln.es.prepareIndex(idx,tp,"1")
                .setSource("{\"cheese\":\"gorgonzola\"}").execute().actionGet();

        log.info("Indexed /{}/{}/1 as version {} into ES", new Object[] { idx, tp, ir1.version() });

        final IndexResponse ir2 = ln.es.prepareIndex(idx,tp,"2")
                .setSource("{\"cheese\":\"mozarella\"}").execute().actionGet();

        log.info("Indexed /{}/{}/2 as version {} into ES", new Object[] { idx, tp, ir2.version() });

        // degraphmalize "1" and wait for and print result
        final DegraphmalizeAction action1 = ln.d.degraphmalize(new ID(idx,tp,"1",ir1.version()), this);

        log.info("Degraphmalize of 1: {}", action1.resultDocument().get());

        // degraphmalize "2" and wait for and print result
        final DegraphmalizeAction action2 = ln.d.degraphmalize(new ID(idx,tp,"2",ir2.version()), this);

        log.info("Degraphmalize of 2: {}", action2.resultDocument().get());

        // degraphmalize "1234"
        final DegraphmalizeAction action0 = ln.d.degraphmalize(new ID(idx,tp,id,ir.version()), this);

        log.info("Degraphmalize of 3: {}", action0.resultDocument().get());

        assertThat(action0.resultDocument().get().get("succes").toString().equals("true")).isTrue();
        assertThat(action1.resultDocument().get().get("succes").toString().equals("true")).isTrue();
        assertThat(action2.resultDocument().get().get("succes").toString().equals("true")).isTrue();

        ln.es.close();
        ln.G.shutdown();
	}

    @Override
    public void recomputeStarted(RecomputeAction action)
    {
        log.info("restart");
    }

    @Override
    public void recomputeComplete(RecomputeResult result)
    {
        log.info("rcomplete");
    }

    @Override
    public void complete(DegraphmalizeResult result)
    {
        log.info("dcomplete");
    }

    @Override
    public void exception(DegraphmalizeResult result)
    {
        log.warn("Exception: {}", result.exception().getMessage());
        result.exception().printStackTrace();
    }
}
