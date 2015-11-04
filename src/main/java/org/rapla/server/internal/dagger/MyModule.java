package org.rapla.server.internal.dagger;

import javax.inject.Named;

import org.rapla.framework.logger.Logger;
import org.rapla.server.ServerService;
import org.rapla.server.internal.ServerServiceImpl;
import org.rapla.server.internal.ServerStorageSelector;
import org.rapla.server.internal.ShutdownService;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.StorageOperator;

import dagger.Module;
import dagger.Provides;

/**
 * Created by Christopher on 04.11.2015.
 */
@Module public class MyModule
{
    ServerServiceImpl.ServerContainerContext context;
    Logger logger;

    public MyModule(ServerServiceImpl.ServerContainerContext context, Logger logger)
    {
        this.context = context;
        this.logger = logger;
    }

    @Provides public Logger provideLogger()
    {
        return logger;
    }

    @Provides ServerServiceImpl.ServerContainerContext provideContext()
    {
        return context;
    }

    @Named(ServerService.ENV_RAPLAMAIL_ID) @Provides Object mail()
    {
        return context.getMailSession();
    }

    @Provides ShutdownService st()
    {
        return context.getShutdownService();
    }

    @Provides CachableStorageOperator provide1(ServerStorageSelector selector)
    {
        return selector.get();
    }

    @Provides StorageOperator provide2(ServerStorageSelector selector)
    {
        return selector.get();
    }
}
