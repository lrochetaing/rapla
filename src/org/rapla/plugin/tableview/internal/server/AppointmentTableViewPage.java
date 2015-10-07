/*--------------------------------------------------------------------------*
 | Copyright (C) 2012 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.plugin.tableview.internal.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.tableview.AppointmentTableColumn;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.TableViewExtensionPoints;
import org.rapla.plugin.tableview.internal.MyAppoitmentTableColumn;
import org.rapla.plugin.tableview.internal.TableConfig;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;
import org.rapla.plugin.tableview.internal.TableViewPlugin;

public class AppointmentTableViewPage extends TableViewPage<AppointmentBlock> 
{
    public AppointmentTableViewPage( RaplaContext context, CalendarModel calendarModel ) 
    {
        super( context,calendarModel );
    }
    
    public String getCalendarHTML() throws RaplaException {
       List<RaplaTableColumn<AppointmentBlock>> appointmentColumnPlugins = loadAppointmentColumns();
       final List<AppointmentBlock> blocks = model.getBlocks();
       return getCalendarHTML(appointmentColumnPlugins, blocks, TableViewPlugin.BLOCKS_SORTING_STRING_OPTION);
    }

    int compareTo(AppointmentBlock object1, AppointmentBlock object2) 
    {
        return object1.compareTo( object2);
    }
    
    private List<RaplaTableColumn<AppointmentBlock>> loadAppointmentColumns() throws RaplaException, RaplaContextException
    {
        List<RaplaTableColumn<AppointmentBlock>> columnPlugins = new ArrayList<RaplaTableColumn<AppointmentBlock>>();
        final Preferences preferences = getClientFacade().getSystemPreferences();
        TableConfig config = TableConfig.read( preferences, getI18n());
        final Collection<TableColumnConfig> columns = config.getColumns("appointments");
        for ( final TableColumnConfig column: columns)
        {
            final RaplaLocale raplaLocale = getRaplaLocale();
            columnPlugins.add( new MyAppoitmentTableColumn(column, raplaLocale));
        }
        final Collection<AppointmentTableColumn> lookupServicesFor = getContainer().lookupServicesFor(TableViewExtensionPoints.APPOINTMENT_TABLE_COLUMN);
        for ( AppointmentTableColumn column:lookupServicesFor)
        {
            columnPlugins.add( column);
        }
        return columnPlugins;
    }
    
   
}

