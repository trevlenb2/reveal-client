package org.smartregister.reveal.presenter;

import android.support.v4.util.Pair;

import org.apache.commons.lang3.StringUtils;
import org.smartregister.configurableviews.ConfigurableViewsLibrary;
import org.smartregister.configurableviews.helper.ConfigurableViewsHelper;
import org.smartregister.configurableviews.model.RegisterConfiguration;
import org.smartregister.configurableviews.model.View;
import org.smartregister.configurableviews.model.ViewConfiguration;
import org.smartregister.domain.Location;
import org.smartregister.domain.Task.TaskStatus;
import org.smartregister.reveal.application.RevealApplication;
import org.smartregister.reveal.contract.TaskRegisterFragmentContract;
import org.smartregister.reveal.interactor.TaskRegisterFragmentInteractor;
import org.smartregister.reveal.model.TaskDetails;
import org.smartregister.reveal.util.Constants;
import org.smartregister.reveal.util.Constants.DatabaseKeys;
import org.smartregister.reveal.util.LocationUtils;
import org.smartregister.reveal.util.PreferencesUtil;
import org.smartregister.reveal.util.Utils;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Set;

import io.ona.kujaku.listeners.BaseLocationListener;

/**
 * Created by samuelgithengi on 3/11/19.
 */
public class TaskRegisterFragmentPresenter extends BaseLocationListener implements TaskRegisterFragmentContract.Presenter {

    private WeakReference<TaskRegisterFragmentContract.View> view;

    private String viewConfigurationIdentifier;

    private ConfigurableViewsHelper viewsHelper;

    private Set<View> visibleColumns;

    private TaskRegisterFragmentInteractor interactor;

    private LocationUtils locationUtils;
    private List<TaskDetails> tasks;
    private android.location.Location lastLocation;

    private boolean recalculateDistance;

    public TaskRegisterFragmentPresenter(TaskRegisterFragmentContract.View view, String viewConfigurationIdentifier) {
        this.view = new WeakReference<>(view);
        this.viewConfigurationIdentifier = viewConfigurationIdentifier;
        viewsHelper = ConfigurableViewsLibrary.getInstance().getConfigurableViewsHelper();
        interactor = new TaskRegisterFragmentInteractor(RevealApplication.getInstance().getAppExecutors(), this);
        locationUtils = new LocationUtils(view.getContext());
        locationUtils.requestLocationUpdates(this);
    }

    @Override
    public void processViewConfigurations() {
        if (!StringUtils.isBlank(this.viewConfigurationIdentifier)) {
            ViewConfiguration viewConfiguration = viewsHelper.getViewConfiguration(this.viewConfigurationIdentifier);
            if (viewConfiguration != null) {
                RegisterConfiguration config = (RegisterConfiguration) viewConfiguration.getMetadata();
                visibleColumns = viewsHelper.getRegisterActiveColumns(this.viewConfigurationIdentifier);
            }
        }
    }

    @Override
    public void initializeQueries(String mainCondition) {

        getView().initializeAdapter(visibleColumns);
        lastLocation = locationUtils.getLastLocation();
        if (lastLocation == null) {//if location client has not initialized use last location passed from map
            lastLocation = getView().getLastLocation();
        }

        interactor.findTasks(getMainCondition(), lastLocation);

        getView().showProgressView();
    }

    @Override
    public void startSync() {
        Utils.startImmediateSync();
    }

    @Override
    public void searchGlobally(String uniqueId) {
        //do nothing, tasks not searchable globally
    }

    /**
     * Gets the where clause for the task register, filters by operational area and campaign
     *
     * @return pair of filter clause and values for filter
     */
    private Pair<String, String[]> getMainCondition() {
        Location operationalArea = Utils.getOperationalAreaLocation(PreferencesUtil.getInstance().getCurrentOperationalArea());
        String whereClause = String.format("%s = ? AND %s = ?", DatabaseKeys.GROUPID, DatabaseKeys.CAMPAIGN_ID);
        return new Pair<>(whereClause, new String[]{operationalArea.getId(), PreferencesUtil.getInstance().getCurrentCampaignId()});
    }

    private TaskRegisterFragmentContract.View getView() {
        return view.get();
    }

    @Override
    public void onTasksFound(List<TaskDetails> tasks, int structuresWithinBuffer) {
        if (recalculateDistance) {//there was a location update when tasks were being retrieved recalculate distance and order
            interactor.calculateDistanceFromUser(tasks, lastLocation);
            recalculateDistance = false;
        } else {
            this.tasks = tasks;
            getView().setTaskDetails(tasks);
            getView().setTotalPatients(structuresWithinBuffer);
            getView().hideProgressView();
        }

    }


    @Override
    public void onLocationChanged(android.location.Location location) {
        if (!location.equals(lastLocation)) {
            if (lastLocation == null && tasks == null) {//tasks not yet retrieved from db
                recalculateDistance = true;
            } else if (location.distanceTo(lastLocation) >= Constants.REFRESH_MAP_MINIMUM_DISTANCE) {
                interactor.calculateDistanceFromUser(tasks, location);
            }
            lastLocation = location;
        }

    }

    @Override
    public void onDestroy() {
        locationUtils.stopLocationClient();
    }

    @Override
    public void onDrawerClosed() {
        interactor.findTasks(getMainCondition(), lastLocation);
    }
}
