package org.rapla.client;

import com.google.web.bindery.event.shared.EventBus;
import org.rapla.client.ActivityManager.Activity;
import org.rapla.client.ActivityManager.Place;
import org.rapla.client.event.PlaceChangedEvent;
import org.rapla.client.gwt.view.RaplaPopups;
import org.rapla.components.i18n.BundleManager;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

@Singleton
public class Application implements ApplicationView.Presenter
{

    private final Logger logger;
    private final BundleManager bundleManager;
    private final ClientFacade facade;
    private final ActivityManager activityManager;
    private final ApplicationView mainView;
    private final Map<String, PlacePresenter> placePresenters;
    private final Map<String, ActivityPresenter> activityPresenters;
    private PlacePresenter actualPlacePresenter;

    private final EventBus eventBus;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Inject
    public Application(final ApplicationView mainView,EventBus eventBus, Map<String, ActivityPresenter> activityPresenters, Map<String, PlacePresenter> placePresenters, Logger logger, BundleManager bundleManager, ClientFacade facade, ActivityManager activityManager)
    {
        this.mainView = mainView;
        this.activityManager = activityManager;
        this.bundleManager = bundleManager;
        this.facade = facade;
        this.logger = logger;
        this.eventBus = eventBus;
        this.activityPresenters = activityPresenters;
        this.placePresenters =placePresenters;
        mainView.setPresenter(this);
    }

    public void start()
    {
        try
        {
            ActivityManager am = activityManager;
            am.init( this);
            mainView.setLoggedInUser(facade.getUser().getName(bundleManager.getLocale()));
            mainView.updateMenu();
            // Test for the resources
            facade.addModificationListener(new ModificationListener()
            {

                @Override
                public void dataChanged(ModificationEvent evt) throws RaplaException
                {
                    actualPlacePresenter.updateView();
                    // TODO inform activities?
                }
            });
            RaplaPopups.getProgressBar().setPercent(100);
        }
        catch (RaplaException e)
        {
            logger.error(e.getMessage(), e);
        }
    }

    public void selectPlace(Place place)
    {
        if (place != null && placePresenters.containsKey(place.getId()))
        {
            final String placeId = place.getId();
            actualPlacePresenter = placePresenters.get(placeId);
            actualPlacePresenter.initForPlace(place);
            mainView.updateContent( actualPlacePresenter.provideContent());
        }
        else
        {
            actualPlacePresenter = findBestSuited();
            actualPlacePresenter.resetPlace();
            mainView.updateContent( actualPlacePresenter.provideContent());
        }
    }

    private PlacePresenter findBestSuited()
    {
        final Set<Entry<String, PlacePresenter>> entrySet = placePresenters.entrySet();
        for (Entry<String, PlacePresenter> entry : entrySet)
        {
            if(entry.getKey().equals(CalendarPlacePresenter.PLACE_ID))
            {
                return entry.getValue();
            }
        }
        // last change take first...
        return placePresenters.values().iterator().next();
    }

    @Override
    public void menuClicked(String action)
    {
        if ("resources".equals(action))
        {
            eventBus.fireEvent(new PlaceChangedEvent(new Place(ResourceSelectionPlace.PLACE_ID, null)));
        }
    }

    public boolean startActivity(Activity activity)
    {
        if (activity != null && activityPresenters.containsKey(activity.getId()))
        {
            final ActivityPresenter activityPresenter = activityPresenters.get(activity.getId());
            if (activityPresenter.startActivity(activity))
            {
                return true;
            }
        }
        return false;
    }

    //    @Override
    //    public void addClicked()
    //    {
    //        logger.info("Add clicked");
    //        try
    //        {
    //            Reservation newEvent = facade.newReservation();
    //            final Date selectedDate = facade.today();
    //            final Date time = new Date(DateTools.MILLISECONDS_PER_MINUTE * calendarOptions.getWorktimeStartMinutes());
    //            final Date startDate = raplaLocale.toDate(selectedDate, time);
    //            final Classification classification = newEvent.getClassification();
    //            final Attribute first = classification.getType().getAttributes()[0];
    //            classification.setValue(first, "Test");
    //
    //            final Date endDate = new Date(startDate.getTime() + DateTools.MILLISECONDS_PER_HOUR);
    //            final Appointment newAppointment = facade.newAppointment(startDate, endDate);
    //            newEvent.addAppointment(newAppointment);
    //            final Allocatable[] resources = facade.getAllocatables();
    //            newEvent.addAllocatable(resources[0]);
    //            eventBus.fireEvent(new DetailSelectEvent(newEvent, null));
    //        }
    //        catch (RaplaException e1)
    //        {
    //            logger.error(e1.getMessage(), e1);
    //        }
    //    }
    //
}
