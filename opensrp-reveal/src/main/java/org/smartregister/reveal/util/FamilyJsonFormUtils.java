package org.smartregister.reveal.util;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.commonregistry.CommonPersonObjectClient;
import org.smartregister.family.util.DBConstants;
import org.smartregister.family.util.JsonFormUtils;
import org.smartregister.family.util.Utils;
import org.smartregister.location.helper.LocationHelper;
import org.smartregister.reveal.util.FamilyConstants.DatabaseKeys;
import org.smartregister.util.FormUtils;
import org.smartregister.view.LocationPickerView;

/**
 * Created by samuelgithengi on 5/24/19.
 */
public class FamilyJsonFormUtils extends JsonFormUtils {

    private static final String TAG = FamilyJsonFormUtils.class.getName();


    private LocationPickerView locationPickerView;

    private FormUtils formUtils;

    private LocationHelper locationHelper;

    public FamilyJsonFormUtils(LocationPickerView locationPickerView, FormUtils formUtils, LocationHelper locationHelper) {
        this.locationPickerView = locationPickerView;
        this.formUtils = formUtils;
        this.locationHelper = locationHelper;
        locationPickerView.init();
    }

    public FamilyJsonFormUtils(Context context) throws Exception {
        this(new LocationPickerView(context), FormUtils.getInstance(context), LocationHelper.getInstance());
    }

    public JSONObject getAutoPopulatedJsonEditFormString(String formName, Context context, CommonPersonObjectClient client, String eventType) {
        try {
            JSONObject form = formUtils.getFormJson(formName);
            if (form != null) {
                form.put(ENTITY_ID, client.getCaseId());
                form.put(ENCOUNTER_TYPE, eventType);

                JSONObject metadata = form.getJSONObject(METADATA);
                String lastLocationId = locationHelper.getOpenMrsLocationId(locationPickerView.getSelectedItem());

                metadata.put(ENCOUNTER_LOCATION, lastLocationId);

                form.put(CURRENT_OPENSRP_ID, Utils.getValue(client.getColumnmaps(), DBConstants.KEY.UNIQUE_ID, false));

                //inject opensrp id into the form
                JSONObject stepOne = form.getJSONObject(STEP1);
                JSONArray jsonArray = stepOne.getJSONArray(FIELDS);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);

                    processPopulatableFields(client, jsonObject);

                }

                return form;
            }
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }

        return null;
    }

    protected static void processPopulatableFields(CommonPersonObjectClient client, JSONObject jsonObject) throws JSONException {
        switch (jsonObject.getString(KEY)) {
            case DatabaseKeys.FAMILY_NAME:
                jsonObject.put(VALUE, Utils.getValue(client.getColumnmaps(), DBConstants.KEY.FIRST_NAME, false));
                break;
            case DBConstants.KEY.VILLAGE_TOWN:
                jsonObject.put(VALUE, Utils.getValue(client.getColumnmaps(), DBConstants.KEY.VILLAGE_TOWN, false));
                break;
            case DatabaseKeys.HOUSE_NUMBER:
                jsonObject.put(VALUE, Utils.getValue(client.getColumnmaps(), DatabaseKeys.HOUSE_NUMBER, false));
                break;
            case DBConstants.KEY.STREET:
                jsonObject.put(VALUE, Utils.getValue(client.getColumnmaps(), DBConstants.KEY.STREET, false));
                break;
            case DBConstants.KEY.LANDMARK:
                jsonObject.put(VALUE, Utils.getValue(client.getColumnmaps(), DBConstants.KEY.LANDMARK, false));
                break;
            default:
                JsonFormUtils.processPopulatableFields(client, jsonObject);
                break;
        }
    }
}
