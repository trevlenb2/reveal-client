package org.smartregister.reveal.sync;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.smartregister.domain.Location;
import org.smartregister.domain.Task;
import org.smartregister.domain.db.Client;
import org.smartregister.domain.db.Event;
import org.smartregister.domain.db.EventClient;
import org.smartregister.domain.db.Obs;
import org.smartregister.domain.jsonmapping.ClientClassification;
import org.smartregister.repository.BaseRepository;
import org.smartregister.repository.EventClientRepository;
import org.smartregister.repository.StructureRepository;
import org.smartregister.repository.TaskRepository;
import org.smartregister.reveal.application.RevealApplication;
import org.smartregister.reveal.util.Constants.BusinessStatus;
import org.smartregister.reveal.util.Constants.JsonForm;
import org.smartregister.reveal.util.Constants.StructureType;
import org.smartregister.reveal.util.PreferencesUtil;
import org.smartregister.reveal.util.Utils;
import org.smartregister.sync.ClientProcessorForJava;

import java.util.List;

import static org.smartregister.reveal.util.Constants.Action.STRUCTURE_TASK_SYNCED;
import static org.smartregister.reveal.util.Constants.END_DATE;
import static org.smartregister.reveal.util.Constants.Intervention.IRS;
import static org.smartregister.reveal.util.Constants.Intervention.MOSQUITO_COLLECTION;
import static org.smartregister.reveal.util.Constants.MOSQUITO_COLLECTION_EVENT;
import static org.smartregister.reveal.util.Constants.Properties.TASK_IDENTIFIER;
import static org.smartregister.reveal.util.Constants.SPRAY_EVENT;
import static org.smartregister.reveal.util.Constants.START_DATE;
import static org.smartregister.reveal.util.Constants.STATUS;

/**
 * Created by samuelgithengi on 12/7/18.
 */
public class RevealClientProcessor extends ClientProcessorForJava {


    private static final String TAG = RevealClientProcessor.class.getCanonicalName();
    private static RevealClientProcessor instance;

    private EventClientRepository eventClientRepository;

    private TaskRepository taskRepository;

    private StructureRepository structureRepository;

    public RevealClientProcessor(Context context) {
        super(context);
        eventClientRepository = RevealApplication.getInstance().getContext().getEventClientRepository();
        taskRepository = RevealApplication.getInstance().getTaskRepository();
        structureRepository = RevealApplication.getInstance().getStructureRepository();
    }


    public static RevealClientProcessor getInstance(Context context) {
        if (instance == null) {
            instance = new RevealClientProcessor(context);
        }

        return instance;
    }

    @Override
    public synchronized void processClient(List<EventClient> eventClientList) {
        processClient(eventClientList, false);
    }

    public void processClient(List<EventClient> eventClients, boolean localEvents) {
        ClientClassification clientClassification = assetJsonToJava("ec_client_classification.json", ClientClassification.class);
        if (clientClassification == null) {
            return;
        }

        Location operationalArea = Utils.getOperationalAreaLocation(PreferencesUtil.getInstance().getCurrentOperationalArea());
        String operationalAreaLocationId = operationalArea == null ? null : operationalArea.getId();
        boolean hasSyncedEventsInTarget = false;
        if (!eventClients.isEmpty()) {
            String operationalAreaId = null;
            for (EventClient eventClient : eventClients) {
                Event event = eventClient.getEvent();
                if (event == null || event.getEventType() == null) {
                    continue;
                }

                String eventType = event.getEventType();
                if (eventType.equals(SPRAY_EVENT)) {
                    operationalAreaId = processSprayEvent(event, clientClassification, localEvents);
                } else if (eventType.equals(MOSQUITO_COLLECTION_EVENT)) {
                    operationalAreaId = processMosquitoCollectionEvent(event, clientClassification, localEvents);
                } else {
                    Client client = eventClient.getClient();
                    //iterate through the events
                    if (client != null) {
                        try {
                            processEvent(event, client, clientClassification);
                        } catch (Exception e) {
                            Log.d(TAG, e.getMessage());
                        }

                    }
                }
                if (!hasSyncedEventsInTarget && operationalAreaLocationId != null &&
                        operationalAreaLocationId.equals(operationalAreaId)) {
                    hasSyncedEventsInTarget = true;
                }
            }
        }

        if (hasSyncedEventsInTarget) {
            Intent intent = new Intent(STRUCTURE_TASK_SYNCED);
            LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
        }
    }

    private String processSprayEvent(Event event, ClientClassification clientClassification, boolean localEvents) {
        String operationalAreaId = null;
        if (event.getDetails() != null && event.getDetails().get(TASK_IDENTIFIER) != null) {
            operationalAreaId = updateTask(event, localEvents, IRS);

            Location structure = structureRepository.getLocationById(event.getBaseEntityId());
            if (structure != null) {
                Obs structureType = event.findObs(null, false, JsonForm.STRUCTURE_TYPE);
                if (structureType != null) {
                    structure.getProperties().setType(structureType.getValue().toString());
                    structureRepository.addOrUpdate(structure);
                }
                if (operationalAreaId == null) {
                    operationalAreaId = structure.getProperties().getParentId();
                }
            }

            try {
                Client client = new Client(event.getBaseEntityId());
                processEvent(event, client, clientClassification);
            } catch (Exception e) {
                Log.e(TAG, "Error processing spray event", e);
            }
        } else {
            Log.w(TAG, String.format("Spray Event %s does not have task details", event.getEventId()));
        }
        return operationalAreaId;
    }

    private String processMosquitoCollectionEvent(Event event, ClientClassification clientClassification, boolean localEvents) {
        String operationalAreaId = null;
        if (event.getDetails() != null && event.getDetails().get(TASK_IDENTIFIER) != null) {
            operationalAreaId = updateTask(event, localEvents, MOSQUITO_COLLECTION);

            try {
                Obs startDateObs = event.findObs(null, false, JsonForm.TRAP_SET_DATE);
                Obs endDateObs = event.findObs(null, false, JsonForm.TRAP_FOLLOW_UP_DATE);

                Object startDate = startDateObs == null ? null : startDateObs.getValue();
                Object endDate = endDateObs == null ? null : endDateObs.getValue();

                event.getDetails().put(START_DATE, startDate == null ? null : startDate.toString());
                event.getDetails().put(END_DATE, endDate == null ? null : endDate.toString());

                RevealClientProcessor clientProcessor = getInstance(RevealApplication.getInstance().getApplicationContext());
                event.getDetails().put(STATUS, clientProcessor.calculateBusinessStatus(event));

                Client client = new Client(event.getBaseEntityId());
                processEvent(event, client, clientClassification);
            } catch (Exception e) {
                Log.e(TAG, "Error processing spray event", e);
            }
        }
        return operationalAreaId;
    }

    private String updateTask(Event event, boolean localEvents, String interventionType) {
        String taskIdentifier = event.getDetails().get(TASK_IDENTIFIER);
        Task task = taskRepository.getTaskByIdentifier(taskIdentifier);
        String operationalAreaId = null;
        if (task != null) {
            task.setBusinessStatus(calculateBusinessStatus(event));
            task.setStatus(Task.TaskStatus.COMPLETED);
            // update task sync status to unsynced if it was already synced,
            // ignore if task status is created so that it will be created on server
            if (localEvents && BaseRepository.TYPE_Synced.equals(task.getSyncStatus())) {
                task.setSyncStatus(BaseRepository.TYPE_Unsynced);
            } else if (!localEvents) {
                // for events synced from server and task exists mark events as being fully synced
                eventClientRepository.markEventAsSynced(event.getFormSubmissionId());
            }
            taskRepository.addOrUpdate(task);
            operationalAreaId = task.getGroupIdentifier();
        } else {
            eventClientRepository.markEventAsTaskUnprocessed(event.getFormSubmissionId());
        }
        return operationalAreaId;
    }

    public String calculateBusinessStatus(Event event) {
        Obs businessStatusObs = event.findObs(null, false, JsonForm.BUSINESS_STATUS);
        if (businessStatusObs != null) {
            return businessStatusObs.getValue().toString();
        } else {
            //supported only for backward compatibility, business status now being calculated on form
            Obs structureType = event.findObs(null, false, JsonForm.STRUCTURE_TYPE);
            if (structureType != null && !StructureType.RESIDENTIAL.equals(structureType.getValue().toString())) {
                return BusinessStatus.NOT_SPRAYABLE;
            } else {
                Obs sprayStatus = event.findObs(null, false, JsonForm.SPRAY_STATUS);
                return sprayStatus == null ? null : sprayStatus.getValue().toString();
            }
        }
    }
}
