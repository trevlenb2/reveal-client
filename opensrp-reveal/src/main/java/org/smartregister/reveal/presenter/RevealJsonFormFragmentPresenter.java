package org.smartregister.reveal.presenter;

import android.content.Intent;
import android.location.Location;
import android.support.annotation.VisibleForTesting;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mapbox.mapboxsdk.geometry.LatLng;
import com.rengwuxian.materialedittext.MaterialEditText;
import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.fragments.JsonFormFragment;
import com.vijay.jsonwizard.interactors.JsonFormInteractor;
import com.vijay.jsonwizard.presenters.JsonFormFragmentPresenter;
import com.vijay.jsonwizard.utils.ValidationStatus;

import org.smartregister.reveal.R;
import org.smartregister.reveal.activity.RevealJsonFormActivity;
import org.smartregister.reveal.contract.PasswordRequestCallback;
import org.smartregister.reveal.contract.UserLocationContract.UserLocationCallback;
import org.smartregister.reveal.util.LocationUtils;
import org.smartregister.reveal.util.PasswordDialogUtils;
import org.smartregister.reveal.util.Utils;
import org.smartregister.reveal.view.RevealMapView;
import org.smartregister.reveal.widget.GeoWidgetFactory;
import org.smartregister.reveal.widget.RevealToasterNotesFactory;

import io.ona.kujaku.listeners.BaseLocationListener;

/**
 * Created by samuelgithengi on 1/30/19.
 */
public class RevealJsonFormFragmentPresenter extends JsonFormFragmentPresenter implements PasswordRequestCallback, UserLocationCallback {

    private JsonFormFragment formFragment;

    private AlertDialog passwordDialog;

    private RevealJsonFormActivity jsonFormView;

    private RevealMapView mapView;

    private ValidateUserLocationPresenter locationPresenter;

    private LocationUtils locationUtils;

    private Location lastLocation;

    private BaseLocationListener locationListener;

    public RevealJsonFormFragmentPresenter(JsonFormFragment formFragment, JsonFormInteractor jsonFormInteractor) {
        super(formFragment, jsonFormInteractor);
        this.formFragment = formFragment;
        passwordDialog = PasswordDialogUtils.initPasswordDialog(formFragment.getActivity(), this);
        jsonFormView = (RevealJsonFormActivity) formFragment.getActivity();
        locationPresenter = new ValidateUserLocationPresenter(jsonFormView, this);
        locationUtils = new LocationUtils(jsonFormView);
        locationListener = new BaseLocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                lastLocation = location;
            }
        };
        locationUtils.requestLocationUpdates(locationListener);
    }

    @Override
    public void validateAndWriteValues() {
        super.validateAndWriteValues();
        for (View childAt : formFragment.getJsonApi().getFormDataViews()) {
            if (childAt instanceof RevealMapView) {
                RevealMapView mapView = (RevealMapView) childAt;
                ValidationStatus validationStatus = GeoWidgetFactory.validate(formFragment, mapView, this);
                String key = (String) childAt.getTag(com.vijay.jsonwizard.R.id.key);
                String mStepName = this.getView().getArguments().getString("stepName");
                String fieldKey = mStepName + " (" + mStepDetails.optString("title") + ") :" + key;
                if (!validationStatus.isValid()) {
                    getInvalidFields().put(fieldKey, validationStatus);
                } else {
                    getInvalidFields().remove(fieldKey);
                    if (isFormValid() && validateFarStructures()) {
                        validateUserLocation(mapView);
                        return;
                    }
                }
                this.mapView = mapView;
                break;//exit loop, assumption; there will be only 1 map per form.
            } else if (childAt instanceof TextView && ! (childAt instanceof MaterialEditText)) {
                ValidationStatus validationStatus = RevealToasterNotesFactory.validate(formFragment, (TextView) childAt);
                String address = (String) childAt.getTag(com.vijay.jsonwizard.R.id.address);
                if (!validationStatus.isValid()) {
                    getInvalidFields().put(address, validationStatus);
                } else {
                    getInvalidFields().remove(address);
                }
            }
        }
        if (isFormValid()) {// if form is valid and did not have a map, if it had a map view it will be handled above
            onLocationValidated();

        } else {//if form is invalid whether having a map or not
            if (showErrorsOnSubmit()) {
                launchErrorDialog();
                getView().showToast(getView().getContext().getResources().getString(R.string.json_form_error_msg, this.getInvalidFields().size()));
            } else {
                getView().showSnackBar(getView().getContext().getResources().getString(R.string.json_form_error_msg, this.getInvalidFields().size()));
            }
        }
    }

    @VisibleForTesting
    protected boolean validateFarStructures() {
        return Utils.validateFarStructures();
    }


    private void validateUserLocation(RevealMapView mapView) {
        this.mapView = mapView;
        Location location = jsonFormView.getUserCurrentLocation();
        if (location != null) {
            locationPresenter.onGetUserLocation(location);
        } else {
            locationPresenter.requestUserLocation();
        }
    }

    @Override
    public void onSaveClick(LinearLayout mainView) {
        validateAndWriteValues();
    }

    @Override
    public void onLocationValidated() {
        jsonFormView.hideProgressDialog();
        Intent returnIntent = new Intent();
        getView().onFormFinish();
        returnIntent.putExtra("json", getView().getCurrentJsonState());
        Object skipValidation = formFragment.getMainView().getTag(com.vijay.jsonwizard.R.id.skip_validation);
        returnIntent.putExtra(JsonFormConstants.SKIP_VALIDATION, Boolean.valueOf(skipValidation == null ? Boolean.FALSE.toString() : skipValidation.toString()));
        getView().finishWithResult(returnIntent);

    }

    @Override
    public LatLng getTargetCoordinates() {
        return mapView.getMapboxMap().getCameraPosition().target;
    }

    @Override
    public void requestUserPassword() {
        if (passwordDialog != null) {
            passwordDialog.show();
        }
    }

    @Override
    public void onPasswordVerified() {
        onLocationValidated();
    }

    @Override
    public ValidateUserLocationPresenter getLocationPresenter() {
        return locationPresenter;
    }

    public LocationUtils getLocationUtils() {
        return locationUtils;
    }

    public Location getLastLocation() {
        return lastLocation;
    }

    public BaseLocationListener getLocationListener() {
        return locationListener;
    }
}
