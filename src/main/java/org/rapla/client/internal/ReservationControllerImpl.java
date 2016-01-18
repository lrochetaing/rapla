/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
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
package org.rapla.client.internal;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.ReservationController;
import org.rapla.client.ReservationEdit;
import org.rapla.client.extensionpoints.EventCheck;
import org.rapla.client.internal.HTMLInfo.Row;
import org.rapla.client.internal.RaplaClipboard.CopyType;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.DateTools.TimeWithoutTimezone;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.components.util.undo.CommandUndo;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;

import javax.inject.Provider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class ReservationControllerImpl implements ModificationListener, ReservationController
{
    /** We store all open ReservationEditWindows with their reservationId
     * in a map, to lookupDeprecated if the reservation is already beeing edited.
     That prevents editing the same Reservation in different windows
     */
    Collection<ReservationEdit> editWindowList = new ArrayList<ReservationEdit>();
    AppointmentFormater appointmentFormater;
    private ReservationEditFactory editFactory;
    ClientFacade facade;
    private RaplaLocale raplaLocale;
    private Logger logger;
    private RaplaResources i18n;
    private CalendarSelectionModel calendarModel;
    private RaplaClipboard clipboard;
    public ReservationControllerImpl(ClientFacade facade, RaplaLocale raplaLocale,Logger logger, RaplaResources i18n, AppointmentFormater appointmentFormater, CalendarSelectionModel calendarModel, RaplaClipboard clipboard)
    {
        this.facade = facade;
        this.raplaLocale = raplaLocale;
        this.logger = logger;
        this.i18n = i18n;
        this.calendarModel = calendarModel;
        this.appointmentFormater=appointmentFormater; 
        this.clipboard = clipboard;
        facade.addModificationListener(this);
    }
    
    public void setEditFactory(ReservationEditFactory editFactory)
    {
        this.editFactory = editFactory;
    }
    
    protected RaplaFacade getFacade()
    {
        return facade.getRaplaFacade();
    }

    protected ClientFacade getClientFacade()
    {
        return facade;
    }
    
    protected RaplaLocale getRaplaLocale()
    {
        return raplaLocale;
    }
    
    protected Logger getLogger()
    {
        return logger;
    }
    
    protected RaplaResources getI18n()
    {
        return i18n;
    }

    public void addReservationEdit(ReservationEdit editWindow) {
        editWindowList.add(editWindow);
    }

    public void removeReservationEdit(ReservationEdit editWindow) {
        editWindowList.remove(editWindow);
    }

    public ReservationEdit edit(Reservation reservation) throws RaplaException {
        return startEdit(reservation,null);
    }

    public ReservationEdit edit(AppointmentBlock appointmentBlock)throws RaplaException {
    	return startEdit(appointmentBlock.getAppointment().getReservation(), appointmentBlock);
    }

    public ReservationEdit[] getEditWindows() {
        return  editWindowList.toArray( new ReservationEdit[] {});
    }

    private ReservationEdit startEdit(Reservation reservation,AppointmentBlock appointmentBlock)
        throws RaplaException {
        // Lookup if the reservation is already beeing edited
        ReservationEdit c = null;
        Iterator<ReservationEdit> it = editWindowList.iterator();
        while (it.hasNext()) {
            c = it.next();
            if (c.getReservation().isIdentical(reservation))
                break;
            else
                c = null;
        }

        if (c != null) {
            c.toFront();
        } else {
            c = editFactory.create(reservation, appointmentBlock);
            // only is allowed to exchange allocations
        }
        return c;
    }

    

	public void deleteBlocks(Collection<AppointmentBlock> blockList,
			PopupContext context) throws RaplaException 
	{
	    boolean deleted = showDeleteDialog(context,blockList.toArray());
        if ( !deleted)
            return;
        
		Set<Appointment> appointmentsToRemove = new LinkedHashSet<Appointment>();
		HashMap<Appointment,List<Date>> exceptionsToAdd = new LinkedHashMap<Appointment,List<Date>>();
		HashMap<Reservation,Integer> appointmentsRemoved = new LinkedHashMap<Reservation,Integer>();
		Set<Reservation> reservationsToRemove = new LinkedHashSet<Reservation>();
        
		for ( AppointmentBlock block: blockList)
		{
			Appointment appointment = block.getAppointment();
			Date from = new Date(block.getStart());
			Repeating repeating = appointment.getRepeating();
			boolean exceptionsAdded = false;
			if ( repeating != null)
	        {
			    List<Date> dateList = exceptionsToAdd.get( appointment );
			    if ( dateList == null)
                {
                    dateList = new ArrayList<Date>();
                    exceptionsToAdd.put( appointment,dateList);
                }
		        dateList.add(from);
		        if ( isNotEmptyWithExceptions(appointment, dateList))
		        {
		             exceptionsAdded = true;
		        }
		        else
		        {
		            exceptionsToAdd.remove( appointment);
		        }
	        }
			if (!exceptionsAdded)
			{
			    boolean added = appointmentsToRemove.add(appointment);
			    if ( added)
			    {
			        Reservation reservation = appointment.getReservation();
                    Integer count = appointmentsRemoved.get(reservation);
                    if ( count == null)
                    {
                        count = 0;
                    }
                    count++;
                    appointmentsRemoved.put( reservation, count);
			    }
			}
		}

		
		for (Reservation reservation: appointmentsRemoved.keySet())
		{
		    Integer count = appointmentsRemoved.get( reservation);
		    Appointment[] appointments = reservation.getAppointments();
            if ( count == appointments.length)
		    {
		        reservationsToRemove.add( reservation);
		        for (Appointment appointment:appointments)
		        {
		            appointmentsRemoved.remove(appointment);
		        }
		    }
		}
		
	    DeleteBlocksCommand command = new DeleteBlocksCommand(reservationsToRemove, appointmentsToRemove, exceptionsToAdd);
	    CommandHistory commanHistory = getCommandHistory();
        commanHistory.storeAndExecute( command);
	}

	abstract protected boolean showDeleteDialog(PopupContext context, Object[] deletables) throws RaplaException;
    
	abstract protected PopupContext getPopupContext();
    
    abstract protected void showException(Exception ex,PopupContext sourceComponent);

    abstract protected int showDialog(String action, PopupContext context, List<String> optionList, List<String> iconList, String title, String content, String dialogIcon) throws RaplaException;

    abstract protected Provider<Set<EventCheck>> getEventChecks() throws RaplaException;
/*
    protected boolean showDeleteDialog(PopupContext context, Object[] deletables)
    {
        InfoFactory infoFactory = getService(InfoFactory.class);
        DialogUI dlg =infoFactory.createDeleteDialog(deletables, context);
        dlg.start();
        int result = dlg.getSelectedIndex();
        return result == 0;
    }

    private int showDialog(String action, PopupContext context, List<String> optionList, List<Icon> iconList, String title, String content, ImageIcon dialogIcon) throws RaplaException
    {
        DialogUI dialog = DialogUI.create(
                getContext()
                ,context
                ,true
                ,title
                ,content
                ,optionList.toArray(new String[] {})
        );
        dialog.setIcon(dialogIcon);
        for ( int i=0;i< optionList.size();i++)
        {
            dialog.getButton(i).setIcon(iconList.get( i));
        }
        
        dialog.start(context);
        int index = dialog.getSelectedIndex();
        return index;
    }
    
    protected Collection<EventCheck> getEventChecks()
    {
        Collection<EventCheck> checkers = getContainer().lookupServicesFor(EventCheck.class);
        return checkers;
    }
*/

   
    
    public class DeleteBlocksCommand extends DeleteUndo<Reservation>
	{
	    Set<Reservation> reservationsToRemove;
	    Set<Appointment> appointmentsToRemove; 
	    Map<Appointment, List<Date>> exceptionsToAdd;
	    
	    private Map<Appointment,Allocatable[]> allocatablesRemoved = new HashMap<Appointment,Allocatable[]>();
	    private Map<Appointment,Reservation> parentReservations = new HashMap<Appointment,Reservation>();
	      
	    public DeleteBlocksCommand(Set<Reservation> reservationsToRemove, Set<Appointment> appointmentsToRemove, Map<Appointment, List<Date>> exceptionsToAdd) {
	        super( ReservationControllerImpl.this.getClientFacade(),ReservationControllerImpl.this.getI18n(),reservationsToRemove);
	        this.reservationsToRemove = reservationsToRemove;
	        this.appointmentsToRemove = appointmentsToRemove;
	        this.exceptionsToAdd = exceptionsToAdd;
	    }

	    public boolean execute() throws RaplaException {
    	    HashMap<Reservation,Reservation> toUpdate = new LinkedHashMap<Reservation,Reservation>();
    	    allocatablesRemoved.clear();
    	    for (Appointment appointment:appointmentsToRemove)
    	    {
    	        Reservation reservation = appointment.getReservation();
    	        if ( reservationsToRemove.contains( reservation))
    	        {
    	            continue;
    	        }
    	        parentReservations.put(appointment, reservation);
    	        Reservation mutableReservation=  toUpdate.get(reservation);
                if ( mutableReservation == null)
                {
                    mutableReservation = getFacade().edit( reservation);
                    toUpdate.put( reservation, mutableReservation);
                }
                Allocatable[] restrictedAllocatables = mutableReservation.getRestrictedAllocatables(appointment);
                mutableReservation.removeAppointment( appointment);
                allocatablesRemoved.put( appointment, restrictedAllocatables);
    	    }
    	    for (Appointment appointment:exceptionsToAdd.keySet())
            {
                Reservation reservation = appointment.getReservation();
                if ( reservationsToRemove.contains( reservation))
                {
                    continue;
                }
                Reservation mutableReservation=  toUpdate.get(reservation);
                if ( mutableReservation == null)
                {
                    mutableReservation = getFacade().edit( reservation);
                    toUpdate.put( reservation, mutableReservation);
                }
                Appointment found = mutableReservation.findAppointment( appointment);
                if ( found != null)
                {
                    Repeating repeating = found.getRepeating();
                    if ( repeating != null)
                    {
                        List<Date> list = exceptionsToAdd.get( appointment);
                        for (Date exception: list)
                        {
                            repeating.addException( exception);
                        }
                    }
                }
            }
    	    Reservation[] updateArray = toUpdate.values().toArray(Reservation.RESERVATION_ARRAY);
    		Reservation[] removeArray = reservationsToRemove.toArray( Reservation.RESERVATION_ARRAY);
    		getFacade().storeAndRemove(updateArray, removeArray);
    		return true;
        }
	    
	    public boolean undo() throws RaplaException {
	        if (!super.undo())
            {
                return false;
            }
	        HashMap<Reservation,Reservation> toUpdate = new LinkedHashMap<Reservation,Reservation>();
            for (Appointment appointment:appointmentsToRemove)
            {
                Reservation reservation = parentReservations.get(appointment);
                Reservation mutableReservation=  toUpdate.get(reservation);
                if ( mutableReservation == null)
                {
                    mutableReservation = getFacade().edit( reservation);
                    toUpdate.put( reservation, mutableReservation);
                }
                mutableReservation.addAppointment( appointment);
                Allocatable[] removedAllocatables = allocatablesRemoved.get( appointment);
                mutableReservation.setRestriction( appointment, removedAllocatables);
            }
            for (Appointment appointment:exceptionsToAdd.keySet())
            {
                Reservation reservation = appointment.getReservation();
                Reservation mutableReservation=  toUpdate.get(reservation);
                if ( mutableReservation == null)
                {
                    mutableReservation = getFacade().edit( reservation);
                    toUpdate.put( reservation, mutableReservation);
                }
                Appointment found = mutableReservation.findAppointment( appointment);
                if ( found != null)
                {
                    Repeating repeating = found.getRepeating();
                    if ( repeating != null)
                    {
                        List<Date> list = exceptionsToAdd.get( appointment);
                        for (Date exception: list)
                        {
                            repeating.removeException( exception);
                        }
                    }
                }
            }
          
            Reservation[] updateArray = toUpdate.values().toArray(Reservation.RESERVATION_ARRAY);
            Reservation[] removeArray = Reservation.RESERVATION_ARRAY;
            getFacade().storeAndRemove(updateArray,removeArray);
            return true;
	    }
	    
	    boolean cut;
	    public boolean isCut() {
            return cut;
        }
	    
	    public void setCut(boolean cut) {
            this.cut = cut;
        }
	    
	    public String getCommandoName() 
	    {
	        return getI18n().getString(isCut() ? "cut" :"delete") + " " + getI18n().getString("appointments");
	    }
	}

    public void deleteAppointment(AppointmentBlock appointmentBlock, PopupContext context) throws RaplaException {
    	boolean includeEvent = true;
        final DialogAction dialogResult = showDialog(appointmentBlock, "delete", includeEvent, context);
		deleteAppointment(appointmentBlock, dialogResult, false);
    }

    private void deleteAppointment(AppointmentBlock appointmentBlock, final DialogAction dialogResult,final boolean isCut) throws RaplaException {
        Appointment appointment = appointmentBlock.getAppointment();
        final Date startDate = new Date(appointmentBlock.getStart());
        Set<Appointment> appointmentsToRemove = new LinkedHashSet<Appointment>();
        HashMap<Appointment,List<Date>> exceptionsToAdd = new LinkedHashMap<Appointment,List<Date>>();
        Set<Reservation> reservationsToRemove = new LinkedHashSet<Reservation>();
        switch (dialogResult) {
            case SINGLE:
                Repeating repeating = appointment.getRepeating();
                if ( repeating != null )
                {
                    List<Date> exceptionList = Collections.singletonList( startDate);
                    if ( isNotEmptyWithExceptions(appointment, exceptionList))
                    {
                        exceptionsToAdd.put( appointment,exceptionList);
                    }
                    else
                    {
                        appointmentsToRemove.add( appointment);
                    }
                }
                else
                {
                    appointmentsToRemove.add( appointment);
                }
                break;
            case EVENT:
                reservationsToRemove.add( appointment.getReservation());
                break;
            case SERIE:
                appointmentsToRemove.add( appointment);
                break;
            case CANCEL:
                return;
        }
    
        DeleteBlocksCommand command = new DeleteBlocksCommand(reservationsToRemove, appointmentsToRemove, exceptionsToAdd)
        {
            public String getCommandoName() {
                String name;
                I18nBundle i18n = getI18n();
                if (dialogResult == DialogAction.SINGLE)
                    name =i18n.format("single_appointment.format",startDate);
                else if (dialogResult == DialogAction.EVENT)
                    name = i18n.getString("reservation");
                else if  (dialogResult == DialogAction.SERIE)
                    name = i18n.getString("serie");
                else
                    name = i18n.getString("appointment");
                return i18n.getString(isCut ? "cut" :"delete") + " " + name;
            }
        };
        CommandHistory commandHistory = getCommandHistory();
        commandHistory.storeAndExecute( command );
    }
    
    private boolean isNotEmptyWithExceptions(Appointment appointment, List<Date> exceptions) {
        Repeating repeating = appointment.getRepeating();
        if ( repeating != null)
        {
            
            int number = repeating.getNumber();
            if ( number>=1)
            {
                final int length = repeating.getExceptions().length + exceptions.size();
                if (length >= number-1)
                {
                    Collection<AppointmentBlock> blocks = new ArrayList<AppointmentBlock>();
                    appointment.createBlocks(appointment.getStart(), appointment.getMaxEnd(), blocks);
                    int blockswithException = 0;
                    for (AppointmentBlock block:blocks)
                    {
                        long start = block.getStart();
                        boolean blocked = false;
                        for (Date excepion: exceptions)
                        {
                            if (DateTools.isSameDay(excepion.getTime(), start))
                            {
                                blocked = true;
                            }
                        }
                        if ( blocked)
                        {
                            blockswithException++;
                        }
                    }
                    if ( blockswithException >= blocks.size())
                    {
                        return false;
                    }
                }
                    
            }
        }
        return true;
    }
    
    public Appointment copyAppointment(Appointment appointment) throws RaplaException {
        return clone(appointment);
    }

    <T extends Entity> T clone(T obj) throws RaplaException
    {
        return getFacade().clone(obj, getClientFacade().getUser());
    }

    enum DialogAction
    {
    	EVENT,
    	SERIE,
    	SINGLE,
    	CANCEL
    }
    
    private DialogAction showDialog(AppointmentBlock appointmentBlock
            ,String action
            ,boolean includeEvent
            ,PopupContext context
    		) throws RaplaException
    {
    	Appointment appointment = appointmentBlock.getAppointment();
    	Date from = new Date(appointmentBlock.getStart());
    	Reservation reservation = appointment.getReservation();
        getLogger().debug(action + " '" + appointment + "' for reservation '" +  reservation + "'");
        List<String> optionList = new ArrayList<String>();
        List<String> iconList = new ArrayList<String>();
        List<DialogAction> actionList = new ArrayList<ReservationControllerImpl.DialogAction>();
        String dateString = getRaplaLocale().formatDate(from);
        
        if ( reservation.getAppointments().length <=1 ||  includeEvent)
        {
            optionList.add(i18n.getString("reservation"));
        	iconList.add("icon.edit_window_small");
        	actionList.add(DialogAction.EVENT);
        }
        if ( appointment.getRepeating() != null && reservation.getAppointments().length > 1 )
        {
            String shortSummary = appointmentFormater.getShortSummary(appointment);
        	optionList.add(i18n.getString("serie") + ": " + shortSummary);
        	iconList.add("icon.repeating");
        	actionList.add(DialogAction.SERIE);
        }
        if ( (appointment.getRepeating() != null  && isNotEmptyWithExceptions( appointment, Collections.singletonList(from)))|| reservation.getAppointments().length > 1)
        {
        	optionList.add(i18n.format("single_appointment.format",dateString));
        	iconList.add("icon.single");
        	actionList.add( DialogAction.SINGLE);
        }
        if (optionList.size() > 1) {
          
            String title = i18n.getString(action);
            String content = i18n.getString(action+ "_appointment.format");
            String dialogIcon = "icon.question";
            
			int index = showDialog(action, context, optionList, iconList, title, content, dialogIcon);
			if ( index < 0)
			{
			    return DialogAction.CANCEL;
			}
            return actionList.get(index);
        }
        else
        {
        	if ( action.equals("delete"))
        	{
        		 boolean deleted = showDeleteDialog(context, new Object[] {appointment.getReservation()});
        		 if ( !deleted)
        			 return DialogAction.CANCEL;
        	       
        	}
        }
        if ( actionList.size() > 0)
        {
        	return actionList.get( 0 );
        }
        return DialogAction.EVENT;
    }



    public void copyAppointment(
            AppointmentBlock appointmentBlock
            ,PopupContext context
            ,Collection<Allocatable> contextAllocatables
            )
                    throws RaplaException
    {
         copyCutAppointment(appointmentBlock, context, contextAllocatables,"copy", false);
    }

    public void cutAppointment(
            AppointmentBlock appointmentBlock
            ,PopupContext context
            ,Collection<Allocatable> contextAllocatables
            )
                    throws RaplaException
    {
         copyCutAppointment(appointmentBlock, context, contextAllocatables, "cut", false);
    }
    
    public void copyReservations(Collection<Reservation> reservations,Collection<Allocatable> contextAllocatables )  throws RaplaException
    {
        List<Reservation> clones = new ArrayList<Reservation>();
        for (Reservation r:reservations)
        {
            Reservation copyReservation = clone(r);
            clones.add( copyReservation);
        }
        getClipboard().setReservation( clones, contextAllocatables);
    }

    public void cutReservations(Collection<Reservation> reservations,Collection<Allocatable> contextAllocatables )  throws RaplaException
    {
        List<Reservation> clones = new ArrayList<Reservation>();
        for (Reservation r:reservations)
        {
            Reservation copyReservation = clone(r);
            clones.add( copyReservation);
        }
        getClipboard().setReservation( clones, contextAllocatables);
        Set<Reservation> reservationsToRemove = new HashSet<Reservation>(reservations);
        Set<Appointment> appointmentsToRemove = Collections.emptySet();
        Map<Appointment, List<Date>> exceptionsToAdd = Collections.emptyMap();
        DeleteBlocksCommand command = new DeleteBlocksCommand(reservationsToRemove, appointmentsToRemove, exceptionsToAdd)
        {
            public String getCommandoName() {
                return getI18n().getString("cut");
            }
        };
        CommandHistory commandHistory = getCommandHistory();
        commandHistory.storeAndExecute( command );
    }


    private void copyCutAppointment(
    		                           AppointmentBlock appointmentBlock
                                       ,PopupContext context
                                       ,Collection<Allocatable> contextAllocatables
                                       ,String action
                                       ,boolean skipDialog
                                       )
        throws RaplaException
    {
        boolean deleteOriginal = action.equals("cut");
    	RaplaClipboard raplaClipboard = getClipboard();
        Appointment appointment = appointmentBlock.getAppointment();
        DialogAction dialogResult;
        if (skipDialog)
        {
            dialogResult = DialogAction.EVENT;
        }
        else
        {
            dialogResult = showDialog(appointmentBlock, action, true, context);
        }
        Reservation sourceReservation = appointment.getReservation();
       
        // copy info text to system clipboard
        {
	        StringBuffer buf = new StringBuffer();
	        ReservationInfoUI reservationInfoUI = new ReservationInfoUI(getI18n(),getRaplaLocale(), getFacade(),logger,appointmentFormater);
	    	boolean excludeAdditionalInfos = false;
	    
			List<Row> attributes = reservationInfoUI.getAttributes(sourceReservation, null, null, excludeAdditionalInfos);
			for (Row row:attributes)
			{
				buf.append( row.getField());
			}
			String string = buf.toString();
			raplaClipboard.copyToSystemClipboard( string);
			
        }
	        
        Allocatable[] restrictedAllocatables = sourceReservation.getRestrictedAllocatables(appointment);
        Appointment copy;
        if ( dialogResult == DialogAction.SINGLE)
        {
        	copy = copyAppointment(appointment);
        	copy.setRepeatingEnabled(false);
        	Date date = DateTools.cutDate(copy.getStart());
        	TimeWithoutTimezone time = DateTools.toTime(date.getTime());
        	Date newStart = new Date(date.getTime() + time.getMilliseconds());
        	copy.move(newStart);
        	RaplaClipboard.CopyType copyType = deleteOriginal ? CopyType.CUT_BLOCK : CopyType.COPY_BLOCK;
        	raplaClipboard.setAppointment(copy,  sourceReservation,copyType, restrictedAllocatables, contextAllocatables);
        }
        else if ( dialogResult == DialogAction.EVENT && appointment.getReservation().getAppointments().length >1)
        {
        	Reservation reservation = appointment.getReservation();
        	Reservation clone = clone(reservation);
            int num  = getAppointmentIndex(appointment);
            Appointment[] clonedAppointments = clone.getAppointments();
            if ( num >= clonedAppointments.length)
            {
                // appointment may be deleted
                return; //null;
            }
            Appointment clonedAppointment = clonedAppointments[num];
            RaplaClipboard.CopyType copyType = deleteOriginal ? CopyType.CUT_RESERVATION : CopyType.COPY_RESERVATION;
     		raplaClipboard.setAppointment(clonedAppointment, clone, copyType,  restrictedAllocatables, contextAllocatables);
     		copy =  clonedAppointment;
        }
        else
        {
            copy = copyAppointment(appointment);
            RaplaClipboard.CopyType copyType;
            if ( deleteOriginal)
            {
                copyType = sourceReservation.getAppointments().length == 1 ?  CopyType.CUT_RESERVATION : CopyType.CUT_BLOCK;
            }
            else
            {
                copyType = CopyType.COPY_BLOCK;
            }
            raplaClipboard.setAppointment(copy,  sourceReservation, copyType, restrictedAllocatables, contextAllocatables);
        }
        if ( deleteOriginal )
        {
            deleteAppointment(appointmentBlock, dialogResult, true);
        }
        //return copy;
    }
    

	public int getAppointmentIndex(Appointment appointment) {
		int num;
		Reservation reservation = appointment.getReservation();
		num = 0;
		for (Appointment app:reservation.getAppointments())
		{
		
			if ( appointment.equals(app))
			{
				break;
			}
			num++;
		}
		return num;
	}

    public void dataChanged(ModificationEvent evt) throws RaplaException {
    	
    	// we need to clone the list, because it could be modified during edit
        ArrayList<ReservationEdit> clone = new ArrayList<ReservationEdit>(editWindowList);
        for ( ReservationEdit edit:clone)
        {
            ReservationEdit c = edit;
            c.refresh(evt);
            TimeInterval invalidateInterval = evt.getInvalidateInterval();
			Reservation original = c.getOriginal();
			if ( invalidateInterval != null && original != null)
			{
				boolean test = false;
				for (Appointment app:original.getAppointments())
				{
					if ( app.overlaps( invalidateInterval))
					{
						test = true;
					}
					
				}
				if ( test )
				{
					try
					{
						Reservation persistant = getFacade().getPersistant( original);
						Date version = persistant.getLastChanged();
						Date originalVersion = original.getLastChanged();
						if ( originalVersion != null && version!= null && originalVersion.before( version))
						{
							c.updateReservation(persistant);
						}
					} 
					catch (EntityNotFoundException ex)
					{
						c.deleteReservation();	
					}
					
				}
			}
           
        }
    }

	private RaplaClipboard getClipboard() 
	{
        return clipboard;
    }
	
    public boolean isAppointmentOnClipboard() {
        return (getClipboard().getAppointment() != null || !getClipboard().getReservations().isEmpty());
    }
    
    public void pasteAppointment(Date start, PopupContext sourceComponent, boolean asNewReservation, boolean keepTime) throws RaplaException {
    	RaplaClipboard clipboard = getClipboard();
    
    	Collection<Reservation> reservations = clipboard.getReservations();
    	CommandUndo<RaplaException> pasteCommand;
    	if ( reservations.size() > 1 )
    	{
    		pasteCommand = new ReservationPaste(reservations, start, keepTime);
    	}
    	else
    	{
    		Appointment appointment = clipboard.getAppointment();
        	if (appointment == null) {
        		return;
        	}
	    	Reservation reservation = clipboard.getReservation();
	    	boolean copyWholeReservation = clipboard.isWholeReservation();
	
	    	Allocatable[] restrictedAllocatables = clipboard.getRestrictedAllocatables();
	    	
	    	long offset = getOffset(appointment.getStart(), start, keepTime);
			
	    	getLogger().debug("Paste appointment '" + appointment 
			          + "' for reservation '" + reservation 
			          + "' at " + start);
			
	    	
	    	Collection<Allocatable> currentlyMarked = calendarModel.getMarkedAllocatables();
	    	Collection<Allocatable> previouslyMarked = clipboard.getContextAllocatables();
	    	// exchange allocatables if pasted in a different allocatable slot
	    	if ( copyWholeReservation && currentlyMarked != null && previouslyMarked != null && currentlyMarked.size() == 1 && previouslyMarked.size() == 1)
	    	{
	    		Allocatable newAllocatable = currentlyMarked.iterator().next();
	    		Allocatable oldAllocatable = previouslyMarked.iterator().next();
				if ( !newAllocatable.equals( oldAllocatable))
				{
					if ( !reservation.hasAllocated(newAllocatable))
					{
						AppointmentBlock appointmentBlock = new AppointmentBlock(appointment);
						AllocatableExchangeCommand cmd = exchangeAllocatebleCmd(appointmentBlock, oldAllocatable, newAllocatable,null, sourceComponent);
						reservation = cmd.getModifiedReservationForExecute();
						appointment = reservation.getAppointments()[0];
					}
				}
	    	}
	    	pasteCommand = new AppointmentPaste(appointment, reservation, restrictedAllocatables, asNewReservation, copyWholeReservation, offset, sourceComponent);
    	}
    	getCommandHistory().storeAndExecute(pasteCommand);
    }

    public CommandHistory getCommandHistory()
    {
        return getClientFacade().getCommandHistory();
    }

    public void moveAppointment(AppointmentBlock appointmentBlock,Date newStart,PopupContext context, boolean keepTime) throws RaplaException {
        Date from = new Date( appointmentBlock.getStart());
    	if ( newStart.equals(from))
            return;
        getLogger().info("Moving appointment " + appointmentBlock.getAppointment() + " from " + from + " to " + newStart);
        resizeAppointment(appointmentBlock, newStart, null, context, keepTime);
    }

	public void resizeAppointment(AppointmentBlock appointmentBlock,  Date newStart, Date newEnd, PopupContext context, boolean keepTime) throws RaplaException {
        boolean includeEvent = newEnd == null;
        Appointment appointment = appointmentBlock.getAppointment();
        Date from = new Date(appointmentBlock.getStart());
		DialogAction result = showDialog(appointmentBlock, "move", includeEvent, context);
		
        if (result == DialogAction.CANCEL) {
        	return;
        }
    	
    	Date oldStart = from;
    	Date oldEnd   = (newEnd == null) ? null : new Date(from.getTime() + appointment.getEnd().getTime() - appointment.getStart().getTime());
        if ( keepTime && newStart != null && !newStart.equals( oldStart))
        {
        	newStart = new Date( oldStart.getTime() + getOffset(oldStart, newStart, keepTime));
        }
        AppointmentResize resizeCommand = new AppointmentResize(appointment, oldStart, oldEnd, newStart, newEnd, context, result, keepTime);
		getCommandHistory().storeAndExecute(resizeCommand);
    }

	public long getOffset(Date appStart, Date newStart, boolean keepTime) {
	    Date newStartAdjusted;
	    if (!keepTime)
	    {
	        newStartAdjusted = newStart;
	    }
	    else
	    {
	        //TimeWithoutTimezone oldStartTime = DateTools.toTime( appStart.getTime());
            newStartAdjusted = DateTools.toDateTime( newStart, appStart);
	    }
        long offset = newStartAdjusted.getTime() - appStart.getTime();
		return offset;
	}

    public boolean save(Reservation reservation, PopupContext sourceComponent) throws RaplaException {
        return save(Collections.singleton(reservation), sourceComponent);
    }

    public boolean save(Collection<Reservation> reservations, PopupContext sourceComponent) throws RaplaException {
        ReservationSave saveCommand = new ReservationSave(reservations,null, sourceComponent);
        return getCommandHistory().storeAndExecute(saveCommand);
    }
    
 

    @Override
    public void exchangeAllocatable(final AppointmentBlock appointmentBlock,final Allocatable oldAllocatable,final Allocatable newAllocatable,final Date newStart,PopupContext context)
			 throws RaplaException 
	{
        AllocatableExchangeCommand command = exchangeAllocatebleCmd( appointmentBlock, oldAllocatable, newAllocatable,newStart, context);
        if ( command != null)
        {
        	CommandHistory commandHistory = getCommandHistory();
			commandHistory.storeAndExecute( command );
        }
	}

	private AllocatableExchangeCommand exchangeAllocatebleCmd(AppointmentBlock appointmentBlock, final Allocatable oldAllocatable,final Allocatable newAllocatable, Date newStart,PopupContext context) throws RaplaException {
		Map<Allocatable,Appointment[]> newRestrictions = new HashMap<Allocatable, Appointment[]>();
        //Appointment appointment;
        //Allocatable oldAllocatable;
        //Allocatable newAllocatable;
        boolean removeAllocatable = false;
        boolean addAllocatable = false;
        Appointment addAppointment = null;
        List<Date> exceptionsAdded = new ArrayList<Date>();
        Appointment appointment = appointmentBlock.getAppointment();
        Reservation reservation = appointment.getReservation();
        Date date = new Date(appointmentBlock.getStart());
        
    	Appointment copy = null;
		Appointment[] restriction = reservation.getRestriction(oldAllocatable);
		boolean includeEvent = restriction.length ==  0;
		DialogAction result = showDialog(appointmentBlock, "exchange_allocatables", includeEvent, context);
        if (result == DialogAction.CANCEL)
            return null;

        if (result == DialogAction.SINGLE && appointment.getRepeating() != null) {
            copy = copyAppointment(appointment);
            copy.setRepeatingEnabled(false);
            Date dateTime = DateTools.toDateTime(  date, appointment.getStart());
			copy.move(dateTime);
        }
     
        if (result == DialogAction.EVENT && includeEvent )
        {
            removeAllocatable = true;
        	//modifiableReservation.removeAllocatable( oldAllocatable);
        	if ( reservation.hasAllocated( newAllocatable))
        	{
        	    newRestrictions.put( newAllocatable, Appointment.EMPTY_ARRAY);
        	    //modifiableReservation.setRestriction( newAllocatable, Appointment.EMPTY_ARRAY);
        	}
        	else
        	{

        	    addAllocatable = true;
        		//modifiableReservation.addAllocatable(newAllocatable);
        	}
        }
        else
        {
        	Appointment[] apps = reservation.getAppointmentsFor(oldAllocatable);
			if ( copy != null)
			{
			    exceptionsAdded.add(date);
			    //Appointment existingAppointment = modifiableReservation.findAppointment( appointment);
			    //existingAppointment.getRepeating().addException( date );
			    //modifiableReservation.addAppointment( copy);
			    addAppointment = copy;
			    
			    List<Allocatable> all =new ArrayList<Allocatable>(Arrays.asList(reservation.getAllocatablesFor(appointment)));
			    all.remove(oldAllocatable);
			    for ( Allocatable a:all)
			    {
			    	Appointment[] restr = reservation.getRestriction( a);
			    	if ( restr.length > 0)
			    	{
			    		List<Appointment> restrictions = new ArrayList<Appointment>( Arrays.asList( restr));
			    		restrictions.add( copy );
			    		newRestrictions.put(a,  restrictions.toArray(Appointment.EMPTY_ARRAY));
			    		//reservation.setRestriction(a, newRestrictions.toArray(new Appointment[] {}));
			    	}
			    }

			    newRestrictions.put( oldAllocatable, apps);
			    //modifiableReservation.setRestriction(oldAllocatable,apps);
			}
			else
			{
				if ( apps.length == 1)
				{
					//modifiableReservation.removeAllocatable(oldAllocatable);
				    removeAllocatable = true;
				}
				else
				{
					List<Appointment> appointments = new ArrayList<Appointment>(Arrays.asList( apps));
					appointments.remove( appointment);
					newRestrictions.put(oldAllocatable , appointments.toArray(Appointment.EMPTY_ARRAY));
					//modifiableReservation.setRestriction(oldAllocatable, appointments.toArray(Appointment.EMPTY_ARRAY));
				}
			}
			
			Appointment app;
			if ( copy != null)
			{
				app = copy;
			}
			else
			{
			    app = appointment;
			}
			
			
			if ( reservation.hasAllocated( newAllocatable))
			{
				Appointment[] existingRestrictions =reservation.getRestriction(newAllocatable);
				Collection<Appointment> restrictions = new LinkedHashSet<Appointment>( Arrays.asList(existingRestrictions));
				if ( existingRestrictions.length ==0 || restrictions.contains( app))
				{
					// is already allocated, do nothing
				}
				else
				{
					restrictions.add(app); 
				}
				newRestrictions.put( newAllocatable, restrictions.toArray(Appointment.EMPTY_ARRAY));
				//modifiableReservation.setRestriction(newAllocatable, newRestrictions.toArray(Appointment.EMPTY_ARRAY));
			}										
			else
			{
				addAllocatable = true;
			    //modifiableReservation.addAllocatable( newAllocatable);
				if ( reservation.getAppointments().length > 1 || addAppointment != null)
				{
					newRestrictions.put( newAllocatable,new Appointment[] {app});
				    //modifiableReservation.setRestriction(newAllocatable, new Appointment[] {appointment});
				}
			}
        }
        if ( newStart != null)
        {
            long offset = newStart.getTime() - appointmentBlock.getStart();
            Appointment app= addAppointment != null ? addAppointment : appointment;
            newStart = new Date( app.getStart().getTime()+ offset);
        }
        AllocatableExchangeCommand command = new AllocatableExchangeCommand( appointment, oldAllocatable, newAllocatable,newStart, newRestrictions, removeAllocatable, addAllocatable, addAppointment, exceptionsAdded, context);
		return command;
	}

    class AllocatableExchangeCommand implements CommandUndo<RaplaException>
    {
        Appointment appointment;
        Allocatable oldAllocatable;
        Allocatable newAllocatable;
        Map<Allocatable, Appointment[]> newRestrictions;
        Map<Allocatable, Appointment[]> oldRestrictions;
        
        boolean removeAllocatable;
        boolean addAllocatable;
        Appointment addAppointment;
        List<Date> exceptionsAdded;
        Date newStart;
        boolean firstTimeCall = true;
        PopupContext sourceComponent;
        
        AllocatableExchangeCommand(Appointment appointment, Allocatable oldAllocatable, Allocatable newAllocatable, Date newStart,Map<Allocatable, Appointment[]> newRestrictions, boolean removeAllocatable, boolean addAllocatable, Appointment addAppointment,
            List<Date> exceptionsAdded, PopupContext sourceComponent)  
        {
            this.appointment = appointment;
            this.oldAllocatable = oldAllocatable;
            this.newAllocatable = newAllocatable;
            this.newStart = newStart;
            this.newRestrictions = newRestrictions;
            this.removeAllocatable = removeAllocatable;
            this.addAllocatable = addAllocatable;
            this.addAppointment = addAppointment;
            this.exceptionsAdded = exceptionsAdded;
            this.sourceComponent = sourceComponent;
        }
        
        public boolean execute() throws RaplaException 
        {
            Reservation modifiableReservation = getModifiedReservationForExecute();
            if ( firstTimeCall)
            {
                firstTimeCall = false;
                Collection<Reservation> reservations = Collections.singleton(modifiableReservation);
                ReservationSave saveCommand = new ReservationSave(reservations,null, sourceComponent);
                return saveCommand.execute();
            }
            else
            {
                getFacade().store( modifiableReservation );
                return true;
            }
        }

		protected Reservation getModifiedReservationForExecute() throws RaplaException {
			Reservation reservation = appointment.getReservation();
            Reservation modifiableReservation = getFacade().edit(reservation);
            if ( addAppointment != null)
            {
                modifiableReservation.addAppointment( addAppointment);
            }
            Appointment existingAppointment = modifiableReservation.findAppointment( appointment);
            if ( existingAppointment != null)
            {
                for ( Date exception: exceptionsAdded)
                {
                    existingAppointment.getRepeating().addException( exception );
                }
            }
            if ( removeAllocatable)
            {
                modifiableReservation.removeAllocatable( oldAllocatable);
            }
            if ( addAllocatable)
            {
                modifiableReservation.addAllocatable(newAllocatable);
            }
            oldRestrictions = new HashMap<Allocatable, Appointment[]>();
            for ( Allocatable alloc: reservation.getAllocatables())
            {
                oldRestrictions.put( alloc, reservation.getRestriction( alloc));
            }
            for ( Allocatable alloc: newRestrictions.keySet())
            {
                Appointment[] restrictions = newRestrictions.get( alloc);
                ArrayList<Appointment> foundAppointments = new ArrayList<Appointment>();
                for ( Appointment app: restrictions)
                {
                    Appointment found = modifiableReservation.findAppointment( app);
                    if ( found != null)
                    {
                        foundAppointments.add( found);
                    }
                }
                modifiableReservation.setRestriction(alloc, foundAppointments.toArray( Appointment.EMPTY_ARRAY));
            }
            if ( newStart != null)
            {
                if ( addAppointment != null)
                {
                    addAppointment.move( newStart);
                } 
                else if (existingAppointment != null)
                {
                    existingAppointment.move( newStart);
                }
            }
//            long startTime = (dialogResult == DialogAction.SINGLE) ? sourceStart.getTime() : ap.getStart().getTime();
//            
//            changeStart = new Date(startTime + offset);
//            
//            if (resizing) {
//                changeEnd = new Date(changeStart.getTime() + (destEnd.getTime() - destStart.getTime()));
//                ap.move(changeStart, changeEnd);
//            } else {
//                ap.move(changeStart);
//            }
			return modifiableReservation;
		}
        
        public boolean undo() throws RaplaException 
        {
            Reservation modifiableReservation = getModifiedReservationForUndo();
            getFacade().store( modifiableReservation);
            return true;
        }

		protected Reservation getModifiedReservationForUndo()
				throws RaplaException {
			Reservation persistant = getFacade().getPersistant(appointment.getReservation());
            Reservation modifiableReservation = getFacade().edit(persistant);
            if ( addAppointment != null)
            {
                Appointment found = modifiableReservation.findAppointment( addAppointment );
                if ( found != null)
                {
                    modifiableReservation.removeAppointment( found );
                }
            }
            
            Appointment existingAppointment = modifiableReservation.findAppointment( appointment);
            if ( existingAppointment != null)
            {
                for ( Date exception: exceptionsAdded)
                {
                    existingAppointment.getRepeating().removeException( exception );
                }
                if ( newStart != null)
                {
                    Date oldStart = appointment.getStart();
                    existingAppointment.move( oldStart);
                }
            }
            if ( removeAllocatable)
            {
                modifiableReservation.addAllocatable( oldAllocatable);
            }
            if ( addAllocatable)
            {
                modifiableReservation.removeAllocatable(newAllocatable);
            }

            for ( Allocatable alloc: oldRestrictions.keySet())
            {
                Appointment[] restrictions = oldRestrictions.get( alloc);
                ArrayList<Appointment> foundAppointments = new ArrayList<Appointment>();
                for ( Appointment app: restrictions)
                {
                    Appointment found = modifiableReservation.findAppointment( app);
                    if ( found != null)
                    {
                        foundAppointments.add( found);
                    }
                }
                modifiableReservation.setRestriction(alloc, foundAppointments.toArray( Appointment.EMPTY_ARRAY));
            }
			return modifiableReservation;
		}
        
        public String getCommandoName() 
        {
            return getI18n().getString("exchange_allocatables");
        }
    }
    
	/**
	 * This class collects any information of an appointment that is resized or moved in any way 
	 * in the calendar view.
	 * This is where undo/redo for moving or resizing of an appointment 
	 * in the calendar view is realized. 
	 * @author Jens Fritz
	 *
	 */
    
    //Erstellt und bearbeitet von Dominik Krickl-Vorreiter und Jens Fritz
    class AppointmentResize implements CommandUndo<RaplaException> {
    	
    	private final Date oldStart;
    	private final Date oldEnd;
    	private final Date newStart;
    	private final Date newEnd;
    	
    	private final Appointment appointment;
    	private final PopupContext sourceComponent;
    	private final DialogAction dialogResult;
    	
    	private Appointment lastCopy;
		private boolean firstTimeCall = true;
		private boolean keepTime;

    	public AppointmentResize(Appointment appointment, Date oldStart, Date oldEnd, Date newStart, Date newEnd, PopupContext sourceComponent, DialogAction dialogResult, boolean keepTime) {
        	this.oldStart        = oldStart;
        	this.oldEnd          = oldEnd;
        	this.newStart        = newStart;
        	this.newEnd          = newEnd;
        	this.appointment     = appointment;
        	this.sourceComponent = sourceComponent;
        	this.dialogResult    = dialogResult;
        	this.keepTime = keepTime;
        	lastCopy = null;
    	}
    	
    	public boolean execute() throws RaplaException {
    		boolean resizing = newEnd != null;
    		Date sourceStart = oldStart;
    		Date destStart = newStart;
    		Date destEnd = newEnd;
    		return doMove(resizing, sourceStart, destStart, destEnd, false);
    	}

    	public boolean undo() throws RaplaException { 
    		boolean resizing = newEnd != null;
    		
    		Date sourceStart = newStart;
    		Date destStart = oldStart;
    		Date destEnd = oldEnd;

    		return doMove(resizing, sourceStart, destStart, destEnd, true);
    	}

		private boolean doMove(boolean resizing, Date sourceStart,
				Date destStart, Date destEnd, boolean undo) throws RaplaException {
			Reservation reservation        = appointment.getReservation();
            Reservation mutableReservation = getFacade().edit(reservation);
            Appointment mutableAppointment = mutableReservation.findAppointment(appointment);
            
            if (mutableAppointment == null) {
                throw new IllegalStateException("Can't find the appointment: " + appointment);
            }
            if(firstTimeCall)
            {
                if(!reservation.getLastChanged().equals(mutableReservation.getLastChanged()))
                {
                    getClientFacade().refresh();
                    throw new RaplaException(getI18n().format("error.new_version", reservation.toString()));
                }
            }
            else
            {
                boolean isNew = false;
                getFacade().checklastChanged( Collections.singletonList( reservation), isNew);
            }

			long offset = getOffset(sourceStart, destStart, keepTime);
            
        	Collection<Appointment> appointments;
        	
        	// Move the complete serie
        	switch (dialogResult) {
				case SERIE:
					// Wir wollen eine Serie (Appointment mit Wdh) verschieben
					appointments = Collections.singleton(mutableAppointment);
					break;
				case EVENT:
					// Wir wollen die ganze Reservation verschieben
					appointments = Arrays.asList(mutableReservation.getAppointments());
					break;
				case SINGLE:
					// Wir wollen nur ein Appointment aus einer Serie verschieben --> losl_sen von Serie
					
					Repeating repeating = mutableAppointment.getRepeating();
	    			if (repeating == null) {
	    				appointments = Arrays.asList(mutableAppointment);
	    			}
	    			else
	    			{
	    				if (undo)
	    				{
		    				mutableReservation.removeAppointment(lastCopy);
		    				repeating.removeException(oldStart);
		    				lastCopy = null;
		    	            Collection<Reservation> reservations = Collections.singleton(mutableReservation);
		    				ReservationSave saveCommand = new ReservationSave(reservations,null, sourceComponent);
		    				return saveCommand.execute();
	    				}
	    				else
	    				{
	    					lastCopy = copyAppointment(mutableAppointment);
	    					lastCopy.setRepeatingEnabled(false);
	    					appointments = Arrays.asList(lastCopy);
	    				}
	    			}
	    			
	    			break;
				default:
					throw new IllegalStateException("Dialog choice not supported "+ dialogResult ) ;
			}
            
            Date changeStart;
            Date changeEnd;
            
        	for (Appointment ap : appointments) {
        		long startTime = (dialogResult == DialogAction.SINGLE) ? sourceStart.getTime() : ap.getStart().getTime();
        		
        		changeStart = new Date(startTime + offset);
        		
    			if (resizing) {
					changeEnd = new Date(changeStart.getTime() + (destEnd.getTime() - destStart.getTime()));
                    ap.move(changeStart, changeEnd);
                } else {
                    ap.move(changeStart);
                }
        	}
        	
        	if ( !undo)
        	{
	        	if (dialogResult == DialogAction.SINGLE) {
	        		Repeating repeating = mutableAppointment.getRepeating();
	                
	    			if (repeating != null) {
	    				Allocatable[] restrictedAllocatables = mutableReservation.getRestrictedAllocatables(mutableAppointment);
	        			mutableReservation.addAppointment(lastCopy);
	        			mutableReservation.setRestriction(lastCopy, restrictedAllocatables);
	    				repeating.addException(oldStart);
	    			}
	            }
        	}
        	
        	if ( firstTimeCall)
			{
				firstTimeCall = false;
                Collection<Reservation> reservations = Collections.singleton(mutableReservation);
                ReservationSave saveCommand = new ReservationSave(reservations,null, sourceComponent);
                boolean result = saveCommand.execute();
                return result;
			}
			else
			{
				getFacade().store( mutableReservation );
				return true;
			}
		}
		
		public String getCommandoName() {
			return getI18n().getString("move");
		}
    }
    
    
    /**
     * This class collects any information of an appointment that is copied and pasted 
     * in the calendar view.
     * This is where undo/redo for pasting an appointment 
     * in the calendar view is realized. 
     * @author Jens Fritz
     *
     */    
    
    //Erstellt von Dominik Krickl-Vorreiter    
    class AppointmentPaste implements CommandUndo<RaplaException> {

		private final Appointment fromAppointment;
		private final Reservation fromReservation;
		private final Allocatable[] restrictedAllocatables;
		private final boolean asNewReservation;
		private final boolean copyWholeReservation;
		private final long offset;
		private final PopupContext sourceComponent;
		
		private Reservation saveReservation = null;
		private Appointment saveAppointment = null;
		private boolean firstTimeCall = true;
		
		public AppointmentPaste(Appointment fromAppointment, Reservation fromReservation, Allocatable[] restrictedAllocatables, boolean asNewReservation, boolean copyWholeReservation, long offset, PopupContext sourceComponent) {
			this.fromAppointment        = fromAppointment;
			this.fromReservation        = fromReservation;
			this.restrictedAllocatables = restrictedAllocatables;
			this.asNewReservation       = asNewReservation;
			this.copyWholeReservation   = copyWholeReservation;
			this.offset                 = offset;
			this.sourceComponent        = sourceComponent;
			
			assert !(!asNewReservation && copyWholeReservation);
		}
		
		public boolean execute() throws RaplaException {
			Reservation mutableReservation = null;
			
			if (asNewReservation) {
                final Reservation reservation = saveReservation != null ? saveReservation : fromReservation;
                mutableReservation =  ReservationControllerImpl.this.clone(reservation);
	        	
	        	// Alle anderen Appointments verschieben / entfernen
	            Appointment[] appointments = mutableReservation.getAppointments();
	            
	            for (int i=0; i < appointments.length; i++) {
	                Appointment app = appointments[i];
	                
	                if (copyWholeReservation) {
	                	if (saveReservation == null) {
	                		app.move(new Date(app.getStart().getTime() + offset));
	                	}
                	} else {
	                	mutableReservation.removeAppointment(app);
	                }
	            }
	        } else {
				mutableReservation =  getFacade().edit(fromReservation);
	        }
			
			if (!copyWholeReservation) {
				if (saveAppointment == null) {
					saveAppointment = copyAppointment(fromAppointment);	
					saveAppointment.move(new Date(saveAppointment.getStart().getTime() + offset));
		        }
				mutableReservation.addAppointment(saveAppointment);
				mutableReservation.setRestriction(saveAppointment, restrictedAllocatables);
	        }

			saveReservation = mutableReservation;
			if ( firstTimeCall)
			{
				firstTimeCall = false;
				Collection<Reservation> reservations = Collections.singleton(mutableReservation);
                ReservationSave saveCommand = new ReservationSave(reservations,null, sourceComponent);
                boolean result = saveCommand.execute();
				return result;
			}
			else
			{
				getFacade().store( mutableReservation );
				return true;
			}
		}

		public boolean undo() throws RaplaException {			
			if (asNewReservation) {
				Reservation mutableReservation = getFacade().edit(saveReservation);
				getFacade().remove(mutableReservation);
				return true;
			} else {
				Reservation mutableReservation = getFacade().edit(saveReservation);
				mutableReservation.removeAppointment(saveAppointment);
	            getFacade().store(mutableReservation);
				return true;
			}
		}
		
		public String getCommandoName() 
		{
			return getI18n().getString("paste");
		}	
    	
    }
    
    
    class ReservationPaste implements CommandUndo<RaplaException> {

		private final Collection<Reservation> fromReservations;
		Date start;
		boolean keepTime;
		Collection<Reservation> clones; 
		
		public ReservationPaste(Collection<Reservation> fromReservation,Date start, boolean keepTime) {
			this.fromReservations        = fromReservation;
			this.start = start;
			this.keepTime = keepTime;
		}
		
		public boolean execute() throws RaplaException {
            final User user = getClientFacade().getUser();
            clones = getFacade().copy(fromReservations,start, keepTime, user);
			PopupContext sourceComponent = getPopupContext();
			save(clones, sourceComponent);
			return true;
		}


        public boolean undo() throws RaplaException {			
			getFacade().storeAndRemove(Reservation.RESERVATION_ARRAY,clones.toArray( Reservation.RESERVATION_ARRAY) );
			return true;
		}	
		
		public String getCommandoName() 
		{
			return getI18n().getString("paste");
		}	
    	
    }
    
	public class ReservationSave extends SaveUndo<Reservation> {
    	
    	private final PopupContext sourceComponent;
    	Collection<Reservation> newReservations;

    	public ReservationSave(Collection<Reservation> newReservations, Collection<Reservation> original, PopupContext sourceComponent)
    	{
    		super(ReservationControllerImpl.this.getFacade(), ReservationControllerImpl.this.getI18n(),newReservations, original);
    		this.sourceComponent  = sourceComponent;
    		this.newReservations = newReservations;
    	}
    	
		public boolean execute() throws RaplaException
		{
			if ( firstTimeCall)
			{
				firstTimeCall = false;
				return save(newReservations, sourceComponent);
			}
			else
			{
				return super.execute();
			}
		}

		boolean save(Collection<Reservation> reservations,PopupContext sourceComponent) throws RaplaException {
		    Provider<Set<EventCheck>> checkers = getEventChecks();
            final Set<EventCheck> set = checkers.get();
            for (EventCheck eventCheck:set)
            {
                boolean successful= eventCheck.check(reservations, sourceComponent);
                if ( !successful)
                {
                    return false;
                }
            }
	        try {
	            getFacade().storeObjects( newReservations.toArray( Reservation.RESERVATION_ARRAY) );
	            return true;
	        } catch (Exception ex) {
	            showException(ex,sourceComponent);
	            return false;
	        }
		}
		
    	
    }
}



