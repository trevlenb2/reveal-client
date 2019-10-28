package org.smartregister.reveal.util;

import org.smartregister.SyncConfiguration;
import org.smartregister.SyncFilter;
import org.smartregister.repository.AllSharedPreferences;
import org.smartregister.repository.LocationRepository;
import org.smartregister.reveal.BuildConfig;
import org.smartregister.reveal.application.RevealApplication;

/**
 * Created by samuelgithengi on 11/29/18.
 */
public class RevealSyncConfiguration extends SyncConfiguration {

    private AllSharedPreferences sharedPreferences;

    private LocationRepository locationRepository;

    public RevealSyncConfiguration() {
    }


    public RevealSyncConfiguration(LocationRepository locationRepository, AllSharedPreferences sharedPreferences) {
        this.locationRepository = locationRepository;
        this.sharedPreferences = sharedPreferences;
    }

    @Override
    public int getSyncMaxRetries() {
        return BuildConfig.MAX_SYNC_RETRIES;
    }

    @Override
    public SyncFilter getSyncFilterParam() {
        return SyncFilter.TEAM_ID;
    }

    @Override
    public String getSyncFilterValue() {
        if (sharedPreferences == null) {
            sharedPreferences = RevealApplication.getInstance().getContext().userService().getAllSharedPreferences();
        }
        return sharedPreferences.fetchDefaultTeamId(sharedPreferences.fetchRegisteredANM());
    }

    @Override
    public int getUniqueIdSource() {
        return BuildConfig.OPENMRS_UNIQUE_ID_SOURCE;
    }

    @Override
    public int getUniqueIdBatchSize() {
        return BuildConfig.OPENMRS_UNIQUE_ID_BATCH_SIZE;
    }

    @Override
    public int getUniqueIdInitialBatchSize() {
        return BuildConfig.OPENMRS_UNIQUE_ID_INITIAL_BATCH_SIZE;
    }

    @Override
    public boolean disableSyncToServerIfUserIsDisabled() {
        return true;
    }

    @Override
    public SyncFilter getEncryptionParam() {
        return SyncFilter.TEAM_ID;
    }

    @Override
    public boolean updateClientDetailsTable() {
        return false;
    }

    @Override
    public boolean isSyncSettings() {
        return true;
    }

    @Override
    public int getConnectTimeout() {
        return 300000;
    }

    @Override
    public int getReadTimeout() {
        return 300000;
    }

    @Override
    public boolean isSyncUsingPost() {
        return false;
    }

    @Override
    public String getSettingsSyncFilterValue() {
        if (sharedPreferences == null) {
            sharedPreferences = RevealApplication.getInstance().getContext().userService().getAllSharedPreferences();
        }
        return sharedPreferences.fetchDefaultTeamId(sharedPreferences.fetchRegisteredANM());
    }

    @Override
    public SyncFilter getSettingsSyncFilterParam() {
        return SyncFilter.TEAM_ID;
    }
}
