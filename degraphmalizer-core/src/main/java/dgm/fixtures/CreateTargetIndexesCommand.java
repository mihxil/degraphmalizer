/*
 * Copyright (C) 2013 All rights reserved
 * VPRO The Netherlands
 */
package dgm.fixtures;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Provider;
import dgm.configuration.*;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: rico
 * Date: 03/04/2013
 */
public class CreateTargetIndexesCommand implements Command<List<String>>
{
    private final Client client;
    private final Provider<Configuration> cfgProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Logger log = LoggerFactory.getLogger(WriteResultDocumentsCommand.class);

    @Inject
    public CreateTargetIndexesCommand(Client client, Provider<Configuration> cfgProvider)
    {
        this.client = client;
        this.cfgProvider = cfgProvider;
    }

    @Override
    public List<String> execute() throws Exception
    {
        List<String> indexes = new ArrayList<String>();
        FixtureConfiguration fixtureConfiguration = cfgProvider.get().getFixtureConfiguration();
        Set<String> indexNames = new HashSet<String>();
        for (String index : fixtureConfiguration.getIndexNames())
        {
            for (String type : fixtureConfiguration.getIndexConfig(index).getTypeNames())
            {
                final Iterable<TypeConfig> configs = Configurations.configsFor(cfgProvider.get(), index, type);
                for (TypeConfig typeConfig : configs)
                {
                    indexNames.add(typeConfig.targetIndex());
                }
            }
        }
        for (String indexName : indexNames)
        {
            log.debug("Creating index [{}]", indexName);
            FixtureIndexConfiguration indexConfig = fixtureConfiguration.getIndexConfig(indexName);
            try
            {
                client.admin().indices().create(buildCreateIndexRequest(indexName, indexConfig)).get();
                indexes.add(indexName);
            } catch (Exception e)
            {
                throw new Exception("something went wrong creating index [" + indexName + "]", e);
            }
        }
        return indexes;
    }

    CreateIndexRequest buildCreateIndexRequest(String indexName, FixtureIndexConfiguration indexConfig) throws IOException
    {
        CreateIndexRequest request = new CreateIndexRequest(indexName, createSettings());
        if (indexConfig != null)
        {
            for (String typeName : indexConfig.getTypeNames())
            {
                FixtureTypeConfiguration typeConfig = indexConfig.getTypeConfig(typeName);
                if (typeConfig.getMapping() != null)
                {
                    request.mapping(typeName, typeConfig.getMapping().toString());
                    log.debug("Add mapping for type [{}] in index [{}]", typeName, indexName);
                }
            }
        }
        return request;
    }

    private Settings createSettings()
    {
        ObjectNode indexConfigSettingsNode = objectMapper.createObjectNode();
        indexConfigSettingsNode.
                put("number_of_shards", 2).
                put("number_of_replicas", 1);

        return ImmutableSettings.settingsBuilder().loadFromSource(indexConfigSettingsNode.toString()).build();
    }
}
