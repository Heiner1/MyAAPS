package info.nightscout.androidaps.plugins.pump.omnipod.dialogs;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;

import javax.inject.Inject;

import info.nightscout.androidaps.activities.NoSplashAppCompatActivity;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.OmnipodHistoryRecord;
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpHistoryEntryGroup;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.utils.ProfileUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.R;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.db.PodHistoryEntryType;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

public class PodHistoryActivity extends NoSplashAppCompatActivity {

    @Inject AAPSLogger aapsLogger;
    @Inject OmnipodUtil omnipodUtil;
    @Inject ResourceHelper resourceHelper;
    @Inject DatabaseHelperInterface databaseHelper;

    private Spinner historyTypeSpinner;
    private TextView statusView;
    private RecyclerView recyclerView;
    private LinearLayoutManager linearLayoutManager;

    static TypeList showingType = null;
    static PumpHistoryEntryGroup selectedGroup = PumpHistoryEntryGroup.All;
    List<OmnipodHistoryRecord> fullHistoryList = new ArrayList<>();
    List<OmnipodHistoryRecord> filteredHistoryList = new ArrayList<>();

    RecyclerViewAdapter recyclerViewAdapter;
    boolean manualChange = false;

    List<TypeList> typeListFull;


    public PodHistoryActivity() {
        super();
    }


    private void prepareData() {
        GregorianCalendar gc = new GregorianCalendar();
        gc.add(Calendar.HOUR_OF_DAY, -24);

        databaseHelper.getAllOmnipodHistoryRecordsFromTimestamp(gc.getTimeInMillis(), false);

        fullHistoryList.addAll(databaseHelper.getAllOmnipodHistoryRecordsFromTimestamp(gc.getTimeInMillis(), true));
    }


    private void filterHistory(PumpHistoryEntryGroup group) {

        this.filteredHistoryList.clear();

        aapsLogger.debug(LTag.PUMP, "Items on full list: {}", fullHistoryList.size());

        if (group == PumpHistoryEntryGroup.All) {
            this.filteredHistoryList.addAll(fullHistoryList);
        } else {
            for (OmnipodHistoryRecord pumpHistoryEntry : fullHistoryList) {
                if (PodHistoryEntryType.getByCode(pumpHistoryEntry.getPodEntryTypeCode()).getGroup() == group) {
                    this.filteredHistoryList.add(pumpHistoryEntry);
                }
            }
        }

        if (this.recyclerViewAdapter != null) {
            this.recyclerViewAdapter.setHistoryList(this.filteredHistoryList);
            this.recyclerViewAdapter.notifyDataSetChanged();
        }

        aapsLogger.debug(LTag.PUMP, "Items on filtered list: {}", filteredHistoryList.size());
    }


    @Override
    protected void onResume() {
        super.onResume();
        filterHistory(selectedGroup);
        setHistoryTypeSpinner();
    }


    private void setHistoryTypeSpinner() {
        this.manualChange = true;

        for (int i = 0; i < typeListFull.size(); i++) {
            if (typeListFull.get(i).entryGroup == selectedGroup) {
                historyTypeSpinner.setSelection(i);
                break;
            }
        }

        SystemClock.sleep(200);
        this.manualChange = false;
    }


    @Override
    protected void onPause() {
        super.onPause();
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.omnipod_pod_history_activity);

        historyTypeSpinner = findViewById(R.id.omnipod_historytype);
        statusView = findViewById(R.id.omnipod_historystatus);
        recyclerView = findViewById(R.id.omnipod_history_recyclerview);
        recyclerView.setHasFixedSize(true);

        linearLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(linearLayoutManager);

        prepareData();

        recyclerViewAdapter = new RecyclerViewAdapter(filteredHistoryList);
        recyclerView.setAdapter(recyclerViewAdapter);

        statusView.setVisibility(View.GONE);

        typeListFull = getTypeList(PumpHistoryEntryGroup.getTranslatedList(resourceHelper));

        ArrayAdapter<TypeList> spinnerAdapter = new ArrayAdapter<>(this, R.layout.spinner_centered, typeListFull);
        historyTypeSpinner.setAdapter(spinnerAdapter);

        historyTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (manualChange)
                    return;
                TypeList selected = (TypeList) historyTypeSpinner.getSelectedItem();
                showingType = selected;
                selectedGroup = selected.entryGroup;
                filterHistory(selectedGroup);
            }


            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if (manualChange)
                    return;
                filterHistory(PumpHistoryEntryGroup.All);
            }
        });

    }


    private List<TypeList> getTypeList(List<PumpHistoryEntryGroup> list) {

        ArrayList<TypeList> typeList = new ArrayList<>();

        for (PumpHistoryEntryGroup pumpHistoryEntryGroup : list) {
            typeList.add(new TypeList(pumpHistoryEntryGroup));
        }

        return typeList;
    }

    public static class TypeList {

        PumpHistoryEntryGroup entryGroup;
        String name;

        TypeList(PumpHistoryEntryGroup entryGroup) {
            this.entryGroup = entryGroup;
            this.name = entryGroup.getTranslated();
        }

        @NotNull
        @Override
        public String toString() {
            return name;
        }
    }

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.HistoryViewHolder> {

        List<OmnipodHistoryRecord> historyList;

        RecyclerViewAdapter(List<OmnipodHistoryRecord> historyList) {
            this.historyList = historyList;
        }


        public void setHistoryList(List<OmnipodHistoryRecord> historyList) {
            this.historyList = historyList;
            Collections.sort(this.historyList);
        }


        @NotNull
        @Override
        public HistoryViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.omnipod_pod_history_item, //
                    viewGroup, false);
            return new HistoryViewHolder(v);
        }


        @Override
        public void onBindViewHolder(@NotNull HistoryViewHolder holder, int position) {
            OmnipodHistoryRecord record = historyList.get(position);

            if (record != null) {
                holder.timeView.setText(record.getDateTimeString());
                holder.typeView.setText(PodHistoryEntryType.getByCode(record.getPodEntryTypeCode()).getResourceId());
                setValue(record, holder.valueView);
            }
        }


        private void setValue(OmnipodHistoryRecord historyEntry, TextView valueView) {
            //valueView.setText("");

            if (historyEntry.isSuccess()) {
                PodHistoryEntryType entryType = PodHistoryEntryType.getByCode(historyEntry.getPodEntryTypeCode());
                switch (entryType) {

                    case SetTemporaryBasal: {
                        TempBasalPair tempBasalPair = omnipodUtil.getGsonInstance().fromJson(historyEntry.getData(), TempBasalPair.class);
                        valueView.setText(resourceHelper.gs(R.string.omnipod_cmd_tbr_value, tempBasalPair.getInsulinRate(), tempBasalPair.getDurationMinutes()));
                    }
                    break;

                    case FillCannulaSetBasalProfile:
                    case SetBasalSchedule: {
                        if (historyEntry.getData() != null) {
                            setProfileValue(historyEntry.getData(), valueView);
                        }
                    }
                    break;

                    case SetBolus: {
                        if (historyEntry.getData().contains(";")) {
                            String[] splitVal = historyEntry.getData().split(";");
                            valueView.setText(resourceHelper.gs(R.string.omnipod_cmd_bolus_value_with_carbs, Double.valueOf(splitVal[0]), Double.valueOf(splitVal[1])));
                        } else {
                            valueView.setText(resourceHelper.gs(R.string.omnipod_cmd_bolus_value, Double.valueOf(historyEntry.getData())));
                        }
                    }
                    break;

                    case GetPodStatus:
                    case GetPodInfo:
                    case SetTime:
                    case PairAndPrime:
                    case CancelTemporaryBasal:
                    case CancelTemporaryBasalForce:
                    case ConfigureAlerts:
                    case CancelBolus:
                    case DeactivatePod:
                    case ResetPodState:
                    case AcknowledgeAlerts:
                    case SuspendDelivery:
                    case ResumeDelivery:
                    case UnknownEntryType:
                    default:
                        valueView.setText("");
                        break;

                }
            } else {
                valueView.setText(historyEntry.getData());
            }

        }

        private void setProfileValue(String data, TextView valueView) {
            aapsLogger.debug(LTag.PUMP, "Profile json:\n" + data);

            try {
                Profile.ProfileValue[] profileValuesArray = omnipodUtil.getGsonInstance().fromJson(data, Profile.ProfileValue[].class);
                valueView.setText(ProfileUtil.getBasalProfilesDisplayable(profileValuesArray, PumpType.Insulet_Omnipod));
            } catch (Exception e) {
                aapsLogger.error(LTag.PUMP, "Problem parsing Profile json. Ex: {}, Data:\n{}", e.getMessage(), data);
                valueView.setText("");
            }
        }


        @Override
        public int getItemCount() {
            return historyList.size();
        }


        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }


        class HistoryViewHolder extends RecyclerView.ViewHolder {

            TextView timeView;
            TextView typeView;
            TextView valueView;

            HistoryViewHolder(View itemView) {
                super(itemView);
                timeView = itemView.findViewById(R.id.omnipod_history_time);
                typeView = itemView.findViewById(R.id.omnipod_history_source);
                valueView = itemView.findViewById(R.id.omnipod_history_description);
            }
        }
    }

}
