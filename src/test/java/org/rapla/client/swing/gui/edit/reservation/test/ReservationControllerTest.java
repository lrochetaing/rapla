/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
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

package org.rapla.client.swing.gui.edit.reservation.test;

import org.junit.Assert;
import org.rapla.client.ReservationController;
import org.rapla.client.ReservationEdit;
import org.rapla.client.swing.gui.tests.GUITestCase;
import org.rapla.client.swing.toolkit.DialogUI;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.components.util.DateTools;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.storage.StorageOperator;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import java.awt.Point;
import java.awt.Window;
import java.util.Date;
import java.util.concurrent.Semaphore;

public final class ReservationControllerTest extends GUITestCase {
	RaplaFacade facade = null;

	public void testMain() throws Exception {
		Reservation[] reservations = facade.getReservationsForAllocatable(null, null, null, null);
		final ReservationController c = getService(ReservationController.class);
		final Reservation reservation = reservations[0];
		c.edit(reservation);
		getLogger().info("ReservationController started");
	}

	public void testMove() throws Exception {
		Reservation[] reservations = facade.getReservationsForAllocatable(null, null, null, null);
		final ReservationController c =  getService(ReservationController.class);
		final Reservation reservation = reservations[0];
		Appointment[] appointments = reservation.getAppointments();
		final Appointment appointment = appointments[0];
		final Date from = appointment.getStart();
		final Semaphore mutex = new Semaphore(1);
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				boolean keepTime = true;
				Point p = null;
				AppointmentBlock appointmentBlock = new AppointmentBlock( appointment);
				Date newStart = DateTools.addDay(appointment.getStart());
				try {
					c.moveAppointment(appointmentBlock, newStart, createPopupContext(),	keepTime);
					Appointment app = facade.getPersistant(reservation).getAppointments()[0];
					Assert.assertEquals(DateTools.addDay(from), app.getStart());
					// Now the test can end
					mutex.release();
				} catch (RaplaException e) {
					e.printStackTrace();
				}
			}

		});
		// We block a mutex to wait for the move thread to finish
		mutex.acquire();
		SwingUtilities.invokeAndWait(new Runnable() {

			@Override
			public void run() {
				for (Window window : JDialog.getWindows()) {
					if (window instanceof DialogUI) {
						RaplaButton button = ((DialogUI) window).getButton(1);
						button.doClick();
					}
				}
			}
		});
		// now wait until move thread is finished
		mutex.acquire();
		mutex.release();
		
		//Testing undo & redo function
		facade.getCommandHistory().undo();
		Assert.assertEquals(from, facade.getPersistant(reservation).getAppointments()[0].getStart());
		facade.getCommandHistory().redo();
		Assert.assertEquals(DateTools.addDay(from), facade.getPersistant(reservation).getAppointments()[0].getStart());
	}
	
	
	public void testPeriodChange() throws Exception {
		RaplaFacade facade = getFacade();
		ClassificationFilter[] filters = facade.getDynamicType(StorageOperator.PERIOD_TYPE).newClassificationFilter().toArray();
		Allocatable[] periods = facade.getAllocatables(filters);
		facade.removeObjects(periods);
		Thread.sleep(500);
		Reservation[] reservations = facade.getReservationsForAllocatable(null, null, null, null);
		ReservationController c = getService(ReservationController.class);
		c.edit(reservations[0]);
		getLogger().info("ReservationController started");
		ReservationEdit editor = c.getEditWindows()[0];
		Date startDate = new Date();
		editor.addAppointment(startDate, new Date(startDate.getTime() + DateTools.MILLISECONDS_PER_DAY));
		editor.save();
		User user = facade.getUser();
		Allocatable period = facade.newPeriod(user);
		Classification classification = period.getClassification();
		classification.setValue("start",startDate);
		classification.setValue("start",new Date(startDate.getTime() + 3
				* DateTools.MILLISECONDS_PER_DAY));
		facade.store(period);
		Thread.sleep(500);
	}

	public static void main(String[] args) {
		new ReservationControllerTest().interactiveTest("testMain");
	}
}
