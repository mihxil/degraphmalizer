package dgm.modules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.*;
import dgm.configuration.*;
import dgm.configuration.javascript.JavascriptConfiguration;
import dgm.configuration.javascript.PollingConfigurationMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

class CachedProvider<T> implements Provider<T>
{
    final Provider<T> sourceProvider;

    // current value is cached here
    T cached = null;

    public CachedProvider(Provider<T> sourceProvider)
    {
        this.sourceProvider = sourceProvider;

        invalidate();
    }

    public T get()
    {
        return cached;
    }

    public boolean invalidate()
    {
        // create new cached
        final T d = sourceProvider.get();

        // return old config if loading failed
        if (d == null)
            return false;

        cached = d;
        return true;
    }
}

/**
 * Non-reloading javascript cached
 */
public class ReloadingJSConfModule extends AbstractModule implements ConfigurationMonitor
{
    private static final Logger log = LoggerFactory.getLogger(ReloadingJSConfModule.class);

	final String scriptFolder;
    final PollingConfigurationMonitor poller;
    final CachedProvider<Configuration> cachedProvider;

    public ReloadingJSConfModule(final String scriptFolder) throws IOException
    {
        this.scriptFolder = scriptFolder;

        /**
         * When loading the configuration this will throw an exception with an invalid configuration, but only on startup.
         * When already running with a configuration this will log an error message trying to load an invalid configuration, but
         * will continue to run with the previous valid configuration.
         */
        final Provider<Configuration> confLoader = new Provider<Configuration>() {
            private AtomicReference<Configuration> configuration = new AtomicReference<Configuration>(createConfiguration(scriptFolder));
            @Override public Configuration get() {
                try
                {
                    final Configuration d = configuration.getAndSet(null);
                    if (d == null)
                        return createConfiguration(scriptFolder);

                    return d;
                }
                catch (ConfigurationException ce)
                {
                    log.info("Failed to load configuration, {}", ce.getMessage());
                    return null;
                }
                catch (Exception e)
                {
                    log.info("Unknown Exception while loading configuration, {}", e);
                    return null;
                }
            }
        };

        this.cachedProvider = new CachedProvider<Configuration> (confLoader);
        this.poller = new PollingConfigurationMonitor(scriptFolder, 200, this);

        // start the poller (does nothing if it is already running)
        poller.start();
    }

    private Configuration createConfiguration(String scriptFolder) throws IOException {
        return new JavascriptConfiguration(new ObjectMapper(), new File(scriptFolder));
    }

    @Provides
    public final Configuration provideConfiguration() throws IOException
	{
        return cachedProvider.get();
    }

    @Override
    public final void configurationChanged(String index)
    {
        log.info("Configuration change detected for target-index {}", index);

        // try to reload the configuration
        if(!cachedProvider.invalidate())
            log.info("Failed to reload configuration");

        // print configuration if debugging is enabled
        if(!log.isDebugEnabled())
            return;

        // get new config
        final Configuration cfg = cachedProvider.get();
        for(final IndexConfig i : cfg.indices().values())
            for(final TypeConfig t : i.types().values())
                log.debug("Found target configuration /{}/{} --> /{}/{}",
                        new Object[]{t.sourceIndex(), t.sourceType(), i.name(), t.name()});
    }

    @Override
    protected void configure()
    {}
}