package org.smartregister.reveal.task;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.domain.Location;
import org.smartregister.domain.Setting;
import org.smartregister.reporting.view.ProgressIndicatorView;
import org.smartregister.reporting.view.TableView;
import org.smartregister.reveal.BuildConfig;
import org.smartregister.reveal.R;
import org.smartregister.reveal.application.RevealApplication;
import org.smartregister.reveal.model.IndicatorDetails;
import org.smartregister.reveal.model.TaskDetails;
import org.smartregister.reveal.util.Constants.CONFIGURATION;
import org.smartregister.reveal.util.Country;
import org.smartregister.reveal.util.IndicatorUtils;
import org.smartregister.reveal.util.PreferencesUtil;
import org.smartregister.reveal.util.Utils;
import org.smartregister.reveal.view.ListTasksActivity;

import java.util.Arrays;
import java.util.List;

import timber.log.Timber;

/**
 * Created by ndegwamartin on 2019-09-23.
 */
public class IndicatorsCalculatorTask extends AsyncTask<Void, Void, IndicatorDetails> {

    private final PreferencesUtil prefsUtil;
    private final SQLiteDatabase sqLiteDatabase;
    private LinearLayout indicatorParentView;
    private ProgressIndicatorView progressIndicator;
    private ProgressIndicatorView progressIndicator2;
    private ProgressIndicatorView progressIndicator3;
    protected Activity activity;
    private List<TaskDetails> tasks;
    private TableView tableView;
    private TableLayout tempTableLayoutView;
    private TableLayout tempTableLayoutViewNG;


    public IndicatorsCalculatorTask(Activity context, List<TaskDetails> tasks) {
        this.activity = context;
        this.tasks = tasks;
        prefsUtil = PreferencesUtil.getInstance();
        sqLiteDatabase = RevealApplication.getInstance().getRepository().getReadableDatabase();

    }

    @Override
    protected void onPreExecute() {
        indicatorParentView = activity.findViewById(R.id.progressIndicatorViewsParent);
        progressIndicator = activity.findViewById(R.id.progressIndicatorView);
        progressIndicator2 = activity.findViewById(R.id.progressIndicatorView2);
        progressIndicator3 = activity.findViewById(R.id.progressIndicatorView3);
        tableView = activity.findViewById(R.id.tableView);
        tempTableLayoutView = activity.findViewById(R.id.tempTableView);
        tempTableLayoutViewNG = activity.findViewById(R.id.tempTableViewNG);


    }

    @Override
    protected IndicatorDetails doInBackground(Void... params) {
        IndicatorDetails indicatorDetails = null;

        if ((BuildConfig.BUILD_COUNTRY == Country.ZAMBIA && !Utils.isZambiaIRSLite()) || BuildConfig.BUILD_COUNTRY == Country.SENEGAL || BuildConfig.BUILD_COUNTRY == Country.SENEGAL_EN) {
            indicatorDetails = IndicatorUtils.processIndicators(this.tasks);
            indicatorDetails.setSprayIndicatorList(IndicatorUtils.populateSprayIndicators(this.activity, indicatorDetails));
        } else if (BuildConfig.BUILD_COUNTRY == Country.NAMIBIA) {
            Location operationalArea = Utils.getOperationalAreaLocation(prefsUtil.getCurrentOperationalArea());
            indicatorDetails = IndicatorUtils.getNamibiaIndicators(operationalArea.getId(), prefsUtil.getCurrentPlanId(), sqLiteDatabase);
            indicatorDetails.setTarget(calculateTarget());
            indicatorDetails.setSprayIndicatorList(IndicatorUtils.populateNamibiaSprayIndicators(this.activity, indicatorDetails));
        } else if(BuildConfig.BUILD_COUNTRY == Country.RWANDA || BuildConfig.BUILD_COUNTRY == Country.RWANDA_EN){
            indicatorDetails  = IndicatorUtils.processRwandaIndicators(this.tasks);
            indicatorDetails.setSprayIndicatorList(IndicatorUtils.populateRwandaIndicators(this.activity,indicatorDetails));
        } else if(BuildConfig.BUILD_COUNTRY == Country.NIGERIA){
            indicatorDetails = IndicatorUtils.processIndicatorsNigeria(this.tasks);
            indicatorDetails.setSprayIndicatorList(IndicatorUtils.populateNigeriaIndicators(this.activity,indicatorDetails));
        }
        return indicatorDetails;

    }

    public int calculateTarget() {
        int target = 0;
        String operationalId = Utils.getCurrentLocationId();
        Setting metadata = RevealApplication.getInstance().getSettingsRepository().getSetting(CONFIGURATION.JURISDICTION_METADATA);
        if (metadata != null) {
            try {
                JSONArray settings = new JSONObject(metadata.getValue()).getJSONArray(CONFIGURATION.SETTINGS);
                for (int i = 0; i < settings.length(); i++) {
                    JSONObject setting = settings.getJSONObject(i);
                    if (setting.getString(CONFIGURATION.KEY).equalsIgnoreCase(operationalId)) {
                        target = setting.getInt(CONFIGURATION.VALUE);
                        break;
                    }
                }
            } catch (JSONException e) {
                Timber.e(e);
            }
        }
        return target;
    }


    @Override
    protected void onPostExecute(IndicatorDetails indicatorDetails) {
        if (progressIndicator == null || indicatorDetails == null) {
            Timber.w("progress indicator or indicators is null");
            return;
        }

        if(Utils.isZambiaIRSLite()) {
            indicatorParentView.setVisibility(View.GONE);
        }

        if (BuildConfig.BUILD_COUNTRY == Country.ZAMBIA || BuildConfig.BUILD_COUNTRY == Country.SENEGAL || BuildConfig.BUILD_COUNTRY == Country.SENEGAL_EN) {
            progressIndicator.setProgress(indicatorDetails.getProgress());
            progressIndicator.setTitle(this.activity.getString(R.string.n_percent, indicatorDetails.getProgress()));

            int totalFound = indicatorDetails.getSprayed() + indicatorDetails.getNotSprayed();
            int progress2 = indicatorDetails.getTotalStructures() > 0 ? Math.round(totalFound * 100 / indicatorDetails.getTotalStructures()) : 0;

            progressIndicator2.setProgress(progress2);
            progressIndicator2.setTitle(this.activity.getString(R.string.n_percent, progress2));

            int progress3 = totalFound > 0 ? Math.round((indicatorDetails.getSprayed() * 100 / totalFound)) : 0;

            progressIndicator3.setProgress(progress3);
            progressIndicator3.setTitle(this.activity.getString(R.string.n_percent, progress3));
        } else if (BuildConfig.BUILD_COUNTRY == Country.NAMIBIA) {

            progressIndicator3.setSubTitle(activity.getString(R.string.target_coverage));
            int targetCoverage = indicatorDetails.getTarget() == 0 ? 100 : (int) (indicatorDetails.getSprayed() * 100.0 / indicatorDetails.getTarget());
            progressIndicator3.setProgress(targetCoverage);
            progressIndicator3.setTitle(activity.getString(R.string.n_percent, targetCoverage));

            progressIndicator2.setSubTitle(activity.getString(R.string.found_coverage));
            int coverage = (int) (indicatorDetails.getSprayed() * 100.0 / indicatorDetails.getFoundStructures());
            progressIndicator2.setProgress(coverage);
            progressIndicator2.setTitle(this.activity.getString(R.string.n_percent, coverage));

            progressIndicator.setVisibility(View.GONE);
        } else if(BuildConfig.BUILD_COUNTRY == Country.RWANDA || BuildConfig.BUILD_COUNTRY == Country.RWANDA_EN){
            progressIndicator.setVisibility(View.GONE);
            progressIndicator2.setVisibility(View.GONE);


            progressIndicator3.setTitle(activity.getString(R.string.click_to_show_indicators));
            progressIndicator3.setSubTitle("");
        } else if(BuildConfig.BUILD_COUNTRY == Country.NIGERIA){

        int totalStructures = indicatorDetails.getTotalStructures() - indicatorDetails.getIneligible();
        int  visited = totalStructures - indicatorDetails.getNotVisited();
        int foundCoverage = totalStructures > 0 ? Math.round(visited * 100 / totalStructures) : 0;
        progressIndicator.setSubTitle(this.activity.getString(R.string.found_coverage));
        progressIndicator.setProgress(foundCoverage);
        progressIndicator.setTitle(this.activity.getString(R.string.n_percent,foundCoverage));


        int distributionCoverage = indicatorDetails.getFoundStructures() > 0 ? Math.round(indicatorDetails.getCompleteDrugDistribution() * 100 / indicatorDetails.getFoundStructures()):0;
        progressIndicator2.setSubTitle(this.activity.getString(R.string.distribution_coverage));
        progressIndicator2.setProgress(distributionCoverage);
        progressIndicator2.setTitle(this.activity.getString(R.string.n_percent,distributionCoverage));


        int individualsComplete =  indicatorDetails.getChildrenEligible() > 0 ? Math.round(indicatorDetails.getTotalIndividualTreated()* 100 /indicatorDetails.getChildrenEligible()): 0;
        progressIndicator3.setSubTitle(this.activity.getString(R.string.and_individuals_complete));
        progressIndicator3.setProgress(individualsComplete);
        progressIndicator3.setTitle(this.activity.getString(R.string.n_percent,individualsComplete));

    }

        if(BuildConfig.BUILD_COUNTRY == Country.RWANDA_EN || BuildConfig.BUILD_COUNTRY == Country.RWANDA){
            tempTableLayoutViewNG.setVisibility(View.GONE);
            tableView.setVisibility(View.GONE);
            populateTableView(Arrays.asList(R.id.health_education_ages_5_to_15_years_cell,
                                            R.id.health_education_16_years_and_above_cell,
                                            R.id.vitamina_total_6_to_11_months_cell,
                                            R.id.vitamina_total_12_to_59_months_cell,
                                            R.id.alb_meb_total_12_to_59_months_cell,
                                            R.id.alb_meb_total_5_to_15_years_cell,
                                            R.id.alb_meb_total_16_years_and_above_cell,
                                            R.id.pzq_total_5_to_15_years_cell,
                                            R.id.pzq_total_16_years_and_above_cell),indicatorDetails.getSprayIndicatorList(),tempTableLayoutView);
        } else if(BuildConfig.BUILD_COUNTRY == Country.NIGERIA) {
            tempTableLayoutView.setVisibility(View.GONE);
            tableView.setVisibility(View.GONE);
            populateTableView(Arrays.asList(R.id.total_structures_cell,
                    R.id.structures_visited_cell,
                    R.id.structures_not_visited_cell,
                    R.id.structure_confirmed_eligible_cell,
                    R.id.structure_complete_drug_distribution_cell,
                    R.id.structure_partial_drug_distribution_cell,
                    R.id.individual_total_number_of_children_eligible_3_to_49_mos_cell,
                    R.id.individual_total_treated_3_to_59_mos_cell),indicatorDetails.getSprayIndicatorList(),tempTableLayoutViewNG);
        } else {
            tempTableLayoutView.setVisibility(View.GONE);
            tempTableLayoutViewNG.setVisibility(View.GONE);
            tableView.setTableData(Arrays.asList(new String[]{this.activity.getString(R.string.indicator), this.activity.getString(R.string.value)}), indicatorDetails.getSprayIndicatorList());
        }

        //Show or hide depending on plan

        ((View) progressIndicator.getParent()).setVisibility(Utils.getInterventionLabel() == R.string.irs || BuildConfig.BUILD_COUNTRY == Country.RWANDA  || BuildConfig.BUILD_COUNTRY == Country.RWANDA_EN || Country.NIGERIA == BuildConfig.BUILD_COUNTRY  ? View.VISIBLE : View.GONE);

        if (activity instanceof ListTasksActivity)
            ((ListTasksActivity) activity).positionMyLocationAndLayerSwitcher();
    }



    private void populateTableView(List<Integer> cellResourceIdentifiers,List<String> sprayIndicatorList,TableLayout tableLayout){
        for(Integer resourceId : cellResourceIdentifiers){
            TextView textView = tableLayout.findViewById(resourceId);
            textView.setText(sprayIndicatorList.get(cellResourceIdentifiers.indexOf(resourceId) * 2 + 1));
        }
    }
}
