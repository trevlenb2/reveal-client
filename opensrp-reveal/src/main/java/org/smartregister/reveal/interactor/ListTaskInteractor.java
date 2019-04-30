package org.smartregister.reveal.interactor;

import android.util.Log;

import com.mapbox.geojson.Feature;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.domain.Location;
import org.smartregister.domain.Task;
import org.smartregister.reveal.application.RevealApplication;
import org.smartregister.reveal.contract.ListTaskContract;
import org.smartregister.reveal.model.CardDetails;
import org.smartregister.reveal.model.MosquitoHarvestCardDetails;
import org.smartregister.reveal.model.SprayCardDetails;
import org.smartregister.reveal.util.Constants.GeoJSON;
import org.smartregister.reveal.util.GeoJsonUtils;
import org.smartregister.reveal.util.Utils;

import java.util.List;
import java.util.Map;

import static org.smartregister.reveal.util.Constants.Intervention.IRS;
import static org.smartregister.reveal.util.Constants.Intervention.LARVAL_DIPPING;
import static org.smartregister.reveal.util.Constants.Intervention.MOSQUITO_COLLECTION;
import static org.smartregister.reveal.util.Constants.Tables.LARVAL_DIPPINGS_TABLE;
import static org.smartregister.reveal.util.Constants.Tables.MOSQUITO_COLLECTIONS_TABLE;

/**
 * Created by samuelgithengi on 11/27/18.
 */
public class ListTaskInteractor extends BaseInteractor {

    private static final String TAG = ListTaskInteractor.class.getCanonicalName();

    public ListTaskInteractor(ListTaskContract.Presenter presenter) {
        super(presenter);
    }


    public void fetchInterventionDetails(String interventionType, String featureId, boolean isForForm) {

        String sql = "SELECT status, start_date, end_date FROM %s WHERE id=?";
        if (IRS.equals(interventionType)) {
            sql = "SELECT spray_status, not_sprayed_reason, not_sprayed_other_reason, property_type, spray_date," +
                    " spray_operator, family_head_name FROM sprayed_structures WHERE id=?";
        } else if (MOSQUITO_COLLECTION.equals(interventionType)) {
            sql = String.format(sql, MOSQUITO_COLLECTIONS_TABLE);
        } else if (LARVAL_DIPPING.equals(interventionType)) {
            sql = String.format(sql, LARVAL_DIPPINGS_TABLE);
        }

        final String SQL = sql;
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                SQLiteDatabase db = RevealApplication.getInstance().getRepository().getWritableDatabase();
                Cursor cursor = db.rawQuery(SQL, new String[]{featureId});

                CardDetails cardDetails = null;
                try {
                    if (cursor.moveToFirst()) {
                        cardDetails = createCardDetails(cursor, interventionType);
                    }
                } catch (Exception e) {
                    Log.e(TAG, Log.getStackTraceString(e));
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }

                // run on ui thread
                final CardDetails CARD_DETAILS = cardDetails;
                appExecutors.mainThread().execute(new Runnable() {
                    @Override
                    public void run() {
                        if (isForForm) {
                            presenterCallBack.onInterventionFormDetailsFetched(CARD_DETAILS);
                        } else {
                            presenterCallBack.onCardDetailsFetched(CARD_DETAILS);
                        }
                    }
                });
            }
        };

        appExecutors.diskIO().execute(runnable);
    }

    private CardDetails createCardDetails(Cursor cursor, String interventionType) {
        CardDetails cardDetails = null;
        if (MOSQUITO_COLLECTION.equals(interventionType) || LARVAL_DIPPING.equals(interventionType)) {
            cardDetails = createMosquitoHarvestCardDetails(cursor, interventionType);
        } else if (IRS.equals(interventionType)) {
            cardDetails = createSprayCardDetails(cursor);
        }
        return cardDetails;
    }

    private SprayCardDetails createSprayCardDetails(Cursor cursor) {
        String reason = cursor.getString(cursor.getColumnIndex("not_sprayed_reason"));
        if ("other".equals(reason)) {
            reason = cursor.getString(cursor.getColumnIndex("not_sprayed_other_reason"));
        }
        return new SprayCardDetails(
                cursor.getString(cursor.getColumnIndex("spray_status")),
                cursor.getString(cursor.getColumnIndex("property_type")),
                cursor.getString(cursor.getColumnIndex("spray_date")),
                cursor.getString(cursor.getColumnIndex("spray_operator")),
                cursor.getString(cursor.getColumnIndex("family_head_name")),
                reason
        );
    }

    private MosquitoHarvestCardDetails createMosquitoHarvestCardDetails(Cursor cursor, String interventionType) {
        return new MosquitoHarvestCardDetails(
                cursor.getString(cursor.getColumnIndex("status")),
                cursor.getString(cursor.getColumnIndex("start_date")),
                cursor.getString(cursor.getColumnIndex("end_date")),
                interventionType
        );
    }

    public void fetchLocations(String campaign, String operationalArea) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                final JSONObject featureCollection = createFeatureCollection();
                Location operationalAreaLocation = Utils.getOperationalAreaLocation(operationalArea);
                try {
                    if (operationalAreaLocation != null) {
                        Map<String, Task> tasks = taskRepository.getTasksByCampaignAndGroup(campaign, operationalAreaLocation.getId());
                        List<Location> structures = structureRepository.getLocationsByParentId(operationalAreaLocation.getId());
                        featureCollection.put(GeoJSON.FEATURES, new JSONArray(GeoJsonUtils.getGeoJsonFromStructuresAndTasks(structures, tasks)));
                        Log.d(TAG, "features:" + featureCollection.toString());

                    }
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
                appExecutors.mainThread().execute(new Runnable() {
                    @Override
                    public void run() {
                        if (operationalAreaLocation != null) {
                            operationalAreaId = operationalAreaLocation.getId();
                            Feature operationalAreaFeature = Feature.fromJson(gson.toJson(operationalAreaLocation));
                            getPresenter().onStructuresFetched(featureCollection, operationalAreaFeature);
                        } else {
                            getPresenter().onStructuresFetched(featureCollection, null);
                        }
                    }
                });
            }
        };

        appExecutors.diskIO().execute(runnable);
    }

    private JSONObject createFeatureCollection() {
        JSONObject featureCollection = new JSONObject();
        try {
            featureCollection.put(GeoJSON.TYPE, GeoJSON.FEATURE_COLLECTION);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating feature collection");
            return null;
        }
        return featureCollection;
    }

    private ListTaskContract.Presenter getPresenter() {
        return (ListTaskContract.Presenter) presenterCallBack;
    }
}
