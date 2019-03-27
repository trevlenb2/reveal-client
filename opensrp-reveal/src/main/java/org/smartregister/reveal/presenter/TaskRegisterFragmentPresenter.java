package org.smartregister.reveal.presenter;

import android.support.v4.util.Pair;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.json.JSONObject;
import org.smartregister.configurableviews.ConfigurableViewsLibrary;
import org.smartregister.configurableviews.helper.ConfigurableViewsHelper;
import org.smartregister.configurableviews.model.View;
import org.smartregister.configurableviews.model.ViewConfiguration;
import org.smartregister.domain.Location;
import org.smartregister.domain.Task;
import org.smartregister.reveal.R;
import org.smartregister.reveal.contract.TaskRegisterFragmentContract;
import org.smartregister.reveal.interactor.TaskRegisterFragmentInteractor;
import org.smartregister.reveal.model.TaskDetails;
import org.smartregister.reveal.repository.RevealMappingHelper;
import org.smartregister.reveal.util.Constants;
import org.smartregister.reveal.util.Constants.DatabaseKeys;
import org.smartregister.reveal.util.LocationUtils;
import org.smartregister.reveal.util.PreferencesUtil;
import org.smartregister.reveal.util.RevealJsonFormUtils;
import org.smartregister.reveal.util.Utils;
import org.smartregister.util.DateTimeTypeConverter;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.ona.kujaku.listeners.BaseLocationListener;

import static org.smartregister.reveal.util.Constants.Intervention.BCC;
import static org.smartregister.reveal.util.Constants.Intervention.LARVAL_DIPPING;

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

    private PreferencesUtil prefsUtil;
    private RevealMappingHelper mappingHelper;

    public TaskRegisterFragmentPresenter(TaskRegisterFragmentContract.View view, String viewConfigurationIdentifier) {
        this(view, viewConfigurationIdentifier, null, new LocationUtils(view.getContext()));
        this.interactor = new TaskRegisterFragmentInteractor(this);
    }

    @VisibleForTesting
    protected TaskRegisterFragmentPresenter(TaskRegisterFragmentContract.View view, String viewConfigurationIdentifier,
                                            TaskRegisterFragmentInteractor interactor, LocationUtils locationUtils) {
        this.view = new WeakReference<>(view);
        this.viewConfigurationIdentifier = viewConfigurationIdentifier;
        this.interactor = interactor;
        this.locationUtils = locationUtils;
        viewsHelper = ConfigurableViewsLibrary.getInstance().getConfigurableViewsHelper();
        prefsUtil = PreferencesUtil.getInstance();
        mappingHelper = new RevealMappingHelper();

    }


    @Override
    public void processViewConfigurations() {
        if (!StringUtils.isBlank(this.viewConfigurationIdentifier)) {
            ViewConfiguration viewConfiguration = viewsHelper.getViewConfiguration(this.viewConfigurationIdentifier);
            if (viewConfiguration != null) {
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

        getView().showProgressView();

        interactor.findTasks(getMainCondition(), lastLocation, getOperationalAreaCenter());

    }

    private android.location.Location getOperationalAreaCenter() {
        Location operationalAreaLocation = Utils.getOperationalAreaLocation(prefsUtil.getCurrentOperationalArea());
        if (operationalAreaLocation == null)
            return null;
        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                .registerTypeAdapter(DateTime.class, new DateTimeTypeConverter()).create();
        return mappingHelper.getCenter(gson.toJson(operationalAreaLocation.getGeometry()));
    }

    @Override
    public void startSync() {
        Utils.startImmediateSync();
    }

    @Override
    public void searchGlobally(String uniqueId) {//do nothing, tasks not searchable globally
    }

    /**
     * Gets the where clause for the task register, filters by operational area and campaign
     *
     * @return pair of filter clause and values for filter
     */
    private Pair<String, String[]> getMainCondition() {
        Location operationalArea = Utils.getOperationalAreaLocation(prefsUtil.getCurrentOperationalArea());
        String whereClause = String.format("%s = ? AND %s = ?", DatabaseKeys.GROUPID, DatabaseKeys.CAMPAIGN_ID);
        return new Pair<>(whereClause, new String[]{operationalArea == null ?
                null : operationalArea.getId(), prefsUtil.getCurrentCampaignId()});
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
            if (tasks == null) {
                getView().displayNotification(R.string.fetching_structure_title,
                        R.string.fetch_location_and_structures_failed, prefsUtil.getCurrentOperationalArea());
                getView().setTaskDetails(new ArrayList<>());
            } else if (tasks.isEmpty()) {
                getView().displayNotification(R.string.fetching_structure_title, R.string.no_structures_found);
                getView().setTaskDetails(tasks);
            } else {
                getView().setTaskDetails(tasks);
            }
            getView().setTotalTasks(structuresWithinBuffer);
            getView().hideProgressDialog();
            getView().hideProgressView();
        }

    }


    @Override
    public void onLocationChanged(android.location.Location location) {
        if (!location.equals(lastLocation)) {
            if (lastLocation == null && tasks == null) {//tasks not yet retrieved from db
                recalculateDistance = true;
            } else if (lastLocation != null && location.distanceTo(lastLocation) >= Constants.REFRESH_MAP_MINIMUM_DISTANCE) {
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
        getView().showProgressDialog(R.string.fetching_structures_title, R.string.fetching_structures_message);
        interactor.findTasks(getMainCondition(), lastLocation, getOperationalAreaCenter());
    }

    @Override
    public void onTaskSelected(TaskDetails details) {
        if (details != null) {
            //TODO remove this condition once BCC and Larval dipping forms are implemented
            if (BCC.equals(details.getTaskCode()) || LARVAL_DIPPING.equals(details.getTaskCode())) {
                getView().displayToast(String.format("To open %s form for %s",
                        details.getTaskCode(), details.getTaskId()));

            } else if (Task.TaskStatus.COMPLETED.name().equals(details.getTaskStatus())) {
                //TODO implement functionality to link to structure details once its implemented
                getView().displayToast(String.format("To open structure details view for %s",
                        details.getFamilyName()));
            } else {
                getView().showProgressDialog(R.string.opening_form_title, R.string.opening_form_message);
                interactor.getStructure(details);
            }
        }
    }

    @Override
    public void onStructureFound(Location structure, TaskDetails details) {
        String formName = RevealJsonFormUtils.getFormName(null, details.getTaskCode());
        JSONObject formJSON = getView().getJsonFormUtils().getFormJSON(getView().getContext(), formName, details, structure);
        getView().startForm(formJSON);
        getView().hideProgressDialog();
    }
}
