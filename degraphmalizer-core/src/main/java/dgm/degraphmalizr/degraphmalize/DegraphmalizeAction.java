package dgm.degraphmalizr.degraphmalize;

import com.fasterxml.jackson.databind.JsonNode;
import dgm.configuration.TypeConfig;
import dgm.ID;

import java.util.concurrent.Future;

public class DegraphmalizeAction
{
    protected final DegraphmalizeActionType actionType;
    protected final DegraphmalizeActionScope actionScope;

    protected final Iterable<TypeConfig> configs;
    protected final ID id;

    protected JsonNode document = null;
    protected final DegraphmalizeStatus status;

    // TODO fix in Degraphmalizer.degraphmalizeJob
    public Future<JsonNode> result;

    public DegraphmalizeAction(DegraphmalizeActionType actionType, DegraphmalizeActionScope actionScope, ID id, Iterable<TypeConfig> configs, DegraphmalizeStatus callback)
    {
        this.actionType = actionType;
        this.actionScope = actionScope;
        this.id = id;
        this.configs = configs;
        this.status = callback;
    }

    public final ID id()
    {
        return id;
    }

    public DegraphmalizeActionType type() {
        return actionType;
    }

    public DegraphmalizeActionScope scope() {
        return actionScope;
    }

    public final Iterable<TypeConfig> configs()
    {
        return configs;
    }

    public final DegraphmalizeStatus status()
    {
        return status;
    }

    // TODO wrong
    public final void setDocument(JsonNode document)
    {
        this.document = document;
    }

    public final JsonNode document()
    {
        return document;
    }

    public final Future<JsonNode> resultDocument()
    {
        return result;
    }
}
