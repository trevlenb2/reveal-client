package org.smartregister.reveal.model;

import org.json.JSONObject;
import org.smartregister.commonregistry.CommonPersonObject;
import org.smartregister.family.domain.FamilyEventClient;
import org.smartregister.family.model.BaseFamilyProfileModel;
import org.smartregister.family.util.JsonFormUtils;
import org.smartregister.family.util.Utils;
import org.smartregister.reveal.BuildConfig;
import org.smartregister.reveal.util.Constants;
import org.smartregister.reveal.util.PreferencesUtil;
import org.smartregister.util.FormUtils;

import static org.smartregister.reveal.util.Constants.DatabaseKeys.LAST_NAME;
import static org.smartregister.reveal.util.FamilyConstants.RELATIONSHIP.RESIDENCE;

/**
 * Created by samuelgithengi on 4/12/19.
 */
public class FamilyProfileModel extends BaseFamilyProfileModel {

    private String structureId;

    private FamilyEventClient eventClient;

    private CommonPersonObject familyHeadPersonObject;

    public FamilyProfileModel(String familyName) {
        super(familyName);
    }

    @Override
    public FamilyEventClient processMemberRegistration(String jsonString, String familyBaseEntityId) {
        eventClient = super.processMemberRegistration(jsonString, familyBaseEntityId);
        tagEventClientDetails(eventClient);
        return eventClient;
    }

    @Override
    public FamilyEventClient processFamilyRegistrationForm(String jsonString, String familyBaseEntityId) {
        eventClient = super.processFamilyRegistrationForm(jsonString, familyBaseEntityId);
        tagEventClientDetails(eventClient);
        return eventClient;
    }

    @Override
    public FamilyEventClient processUpdateMemberRegistration(String jsonString, String familyBaseEntityId) {
        eventClient = super.processUpdateMemberRegistration(jsonString, familyBaseEntityId);
        tagEventClientDetails(eventClient);
        return eventClient;
    }

    private void tagEventClientDetails(FamilyEventClient eventClient) {
        if (eventClient == null)
            return;
        if (structureId != null) {
            eventClient.getClient().addAttribute(RESIDENCE, structureId);
            eventClient.getEvent().addDetails(Constants.Properties.LOCATION_ID, structureId);
        }
        eventClient.getEvent().addDetails(Constants.Properties.APP_VERSION_NAME, BuildConfig.VERSION_NAME);
        eventClient.getEvent().setLocationId(org.smartregister.reveal.util.Utils.getOperationalAreaLocation(PreferencesUtil.getInstance().getCurrentOperationalArea()).getId());
    }

    public void setStructureId(String structureId) {
        this.structureId = structureId;
    }

    public String getStructureId() {
        return structureId;
    }

    @Override
    public JSONObject getFormAsJson(String formName, String entityId, String currentLocationId) throws Exception {

        JSONObject form = FormUtils.getInstance(Utils.context().applicationContext()).getFormJson(formName);
        if (form == null) {
            return null;
        }
        form = JsonFormUtils.getFormAsJson(form, formName, entityId, currentLocationId);

        if (formName.equals(Utils.metadata().familyMemberRegister.formName)) {
            JsonFormUtils.updateJsonForm(form, familyHeadPersonObject.getColumnmaps().get(LAST_NAME));
        }

        return form;
    }

    public void setFamilyHeadPersonObject(CommonPersonObject familyHeadPersonObject) {
        this.familyHeadPersonObject = familyHeadPersonObject;
    }

    public CommonPersonObject getFamilyHeadPersonObject() {
        return familyHeadPersonObject;
    }
}
