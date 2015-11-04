package org.rapla.server.internal;

import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.storage.ImportExportManager;
import org.rapla.storage.impl.server.ImportExportManagerImpl;

import javax.inject.Inject;
import javax.inject.Provider;

@DefaultImplementation(of=ImportExportManager.class,context = InjectionContext.server)
public class ImportExportManagerProvider implements Provider<ImportExportManager>
{
    private final ServerStorageSelector selector;
    @Inject
    public ImportExportManagerProvider(ServerStorageSelector selector)
    {
        this.selector = selector;
    }

    @Override public ImportExportManager get()
    {
        return selector.getImportExportManager().get();
    }
}
