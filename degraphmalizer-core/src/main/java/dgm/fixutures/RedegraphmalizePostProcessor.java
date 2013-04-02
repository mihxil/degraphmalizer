package dgm.fixutures;

import com.google.common.collect.Iterables;
import com.google.inject.Provider;
import dgm.Degraphmalizr;
import dgm.ID;
import dgm.configuration.Configuration;
import dgm.degraphmalizr.degraphmalize.DegraphmalizeRequestScope;
import dgm.degraphmalizr.degraphmalize.DegraphmalizeRequestType;
import dgm.degraphmalizr.degraphmalize.LoggingDegraphmalizeCallback;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * This thing runs after fixture data has been inserted.
 * The purpose is to degraphmalize every document that has just been inserted, so all documents
 * in each index for which there is a fixture configuration.
 *
 * @author Ernst Bunders
 */
public class RedegraphmalizePostProcessor implements PostProcessor
{
    private final Client client;
    private final Provider<Configuration> cfgProvider;
    private final Degraphmalizr degraphmalizr;

    private static final Logger log = LoggerFactory.getLogger(RedegraphmalizePostProcessor.class);

    @Inject
    public RedegraphmalizePostProcessor(Client client, Provider<Configuration> cfgProvider, Degraphmalizr degraphmalizr)
    {
        this.client = client;
        this.cfgProvider = cfgProvider;
        this.degraphmalizr = degraphmalizr;
    }

    @Override
    public void run()
    {
        try
        {
            QueryBuilder qb = new MatchAllQueryBuilder();

            String[] indices = Iterables.toArray(cfgProvider.get().getFixtureConfiguration().getIndexNames(), String.class);
            SearchResponse response = client.prepareSearch()
                    .setSearchType(SearchType.QUERY_AND_FETCH)
                    .setNoFields()
                    .setIndices(indices)
                    .setQuery(qb)
                    .setSize(-1)
                    .setVersion(true)
                    .execute().actionGet();


            for (SearchHit hit : response.getHits().getHits())
            {
                ID id = new ID(hit.getIndex(), hit.getType(), hit.getId(), hit.version());
                log.debug("Re-degraphmalizing document {}", id);
                degraphmalizr.degraphmalize(DegraphmalizeRequestType.UPDATE, DegraphmalizeRequestScope.DOCUMENT, id, new LoggingDegraphmalizeCallback());
            }

        } catch (Exception e)
        {
            log.error("Something went wrong re-degraphmalizing fixture documents.", e);
        }
    }

}
