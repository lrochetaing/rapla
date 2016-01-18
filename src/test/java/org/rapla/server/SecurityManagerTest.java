package org.rapla.server;

import org.eclipse.jetty.server.Server;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.ServletTestBase;
import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.logger.Logger;
import org.rapla.server.internal.ServerServiceImpl;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.test.util.RaplaTestCase;

import javax.inject.Provider;
import java.util.Date;
import java.util.Locale;

@RunWith(JUnit4.class)
public class SecurityManagerTest  {


	protected RaplaFacade facade1;
	
	Locale locale;
	private Logger logger;
	private ServerServiceImpl serverService;
	private Server server;

	@Before public void setUp() throws Exception
	{
		logger = RaplaTestCase.initLoger();
		int port = 8052;
		serverService = (ServerServiceImpl) RaplaTestCase.createServer(logger, "testdefault.xml");
		server = ServletTestBase.createServer( serverService, port);
		Provider<RaplaFacade> clientFacadeProvider = RaplaTestCase.createFacadeWithRemote(logger, port);
		facade1 = clientFacadeProvider.get();
		//facade2 = clientFacadeProvider.get();
		//facade2.login("homer", "duffs".toCharArray());
		locale = Locale.getDefault();
	}

	public void tearDown() throws Exception
	{
		server.stop();
	}

	@Test
	public void testConflictForbidden() throws Exception
	{
		// We test conflict prevention for an appointment that is in the future
		Date start = new Date(facade1.today().getTime() + DateTools.MILLISECONDS_PER_DAY  +  10 * DateTools.MILLISECONDS_PER_HOUR);
		Date end = new Date( start.getTime() + 2 * DateTools.MILLISECONDS_PER_HOUR);
		
		facade1.login("homer", "duffs".toCharArray());
		DynamicType roomType = facade1.getDynamicType("room");
		ClassificationFilter filter = roomType.newClassificationFilter();
		filter.addEqualsRule("name", "erwin");
		Allocatable resource = facade1.getAllocatables( filter.toArray())[0];
		Appointment app1;
		{
			app1 = facade1.newAppointment( start, end ) ;
			// First we create a reservation for the resource
			Reservation event = facade1.newReservation();
			event.getClassification().setValue("name", "taken");
			event.addAppointment( app1 );
			event.addAllocatable( resource );
			facade1.store( event );
		}
		facade1.logout();
		// Now we login as a non admin user, who isnt allowed to create conflicts on the resource erwin
		facade1.login("monty", "burns".toCharArray());
		{
			Reservation event = facade1.newReservation();
			// A new event with the same time for the same resource should fail. 
			event.getClassification().setValue("name", "conflicting event");
			Appointment app = facade1.newAppointment( start, end ) ;
			event.addAppointment( app);
			event.addAllocatable( resource );
			try 
			{
				facade1.store( event );
				Assert.fail("Security Exception expected");
			}
			catch ( Exception ex)
			{
				Throwable ex1 = ex;
				boolean secExceptionFound = false;
				while ( ex1 != null)
				{
					if ( ex1 instanceof RaplaSecurityException)
					{
						secExceptionFound = true;
					}
					ex1 = ex1.getCause();
				}
				if ( !secExceptionFound)
				{
					ex.printStackTrace();
					Assert.fail("Exception expected but was not security exception");
				}
			}
			// moving the start of the second appointment to the end of the first one should work 
			app.move( end );
			facade1.store( event );
		}
		{
			// We have to reget the event
			DynamicType eventType = facade1.getDynamicType("event");
			ClassificationFilter eventFilter = eventType.newClassificationFilter();
			eventFilter.addEqualsRule("name", "conflicting event");
			
			Reservation event = facade1.getReservationsForAllocatable( null, null, null, eventFilter.toArray())[0];
				// But moving back the appointment to today should fail
			event = facade1.edit( event );
			Date startPlus1 = new Date( start.getTime() + DateTools.MILLISECONDS_PER_HOUR) ;
			event.getAppointments()[0].move( startPlus1,end);
			try 
			{
				facade1.store( event );
				Assert.fail("Security Exception expected");
			}
			catch ( Exception ex)
			{
				Throwable ex1 = ex;
				boolean secExceptionFound = false;
				while ( ex1 != null)
				{
					if ( ex1 instanceof RaplaSecurityException)
					{
						secExceptionFound = true;
					}
					ex1 = ex1.getCause();
				}
				if ( !secExceptionFound)
				{
					ex.printStackTrace();
					Assert.fail("Exception expected but was not security exception");
				}
			}
			facade1.logout();
		    Thread.sleep(100);
		}

	}
	
}
