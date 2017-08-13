package org.inaturalist.android;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.flurry.android.FlurryAgent;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class TaxonSuggestionsActivity extends AppCompatActivity {
    private static String TAG = "TaxonSuggestionsActivity";

    public static final String OBS_PHOTO_FILENAME = "obs_photo_filename";
    public static final String OBS_PHOTO_URL = "obs_photo_url";
    public static final String LONGITUDE = "longitude";
    public static final String LATITUDE = "latitude";
    public static final String OBSERVED_ON = "observed_on";

    private static final int TAXON_SEARCH_REQUEST_CODE = 302;

    private INaturalistApp mApp;
    private ActivityHelper mHelper;
	private List<BetterJSONObject> mTaxonSuggestions;
    private BetterJSONObject mTaxonCommonAncestor;
    private TaxonSuggestionsReceiver mTaxonSuggestionsReceiver;
    private String mObsPhotoFilename;
    private String mObsPhotoUrl;
    private double mLatitude;
    private double mLongitude;
    private Timestamp mObservedOn;

    private ImageView mObsPhoto;
    private View mBackButton;
    private ViewGroup mSpeciesSearch;
    private TextView mSuggestionsDescription;
    private ListView mSuggestionsList;
    private ProgressBar mLoadingSuggestions;
    private ViewGroup mSuggestionsContainer;
    private TextView mNoNetwork;
    private TextView mCommonAncestorDescription;
    private ListView mCommonAncestorList;

    @Override
    protected void onStart()
    {
        super.onStart();
        FlurryAgent.onStartSession(this, INaturalistApp.getAppContext().getString(R.string.flurry_api_key));
        FlurryAgent.logEvent(this.getClass().getSimpleName());
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        FlurryAgent.onEndSession(this);
    }


    private class TaxonSuggestionsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            unregisterReceiver(mTaxonSuggestionsReceiver);

            BetterJSONObject resultsObject = (BetterJSONObject) intent.getSerializableExtra(INaturalistService.TAXON_SUGGESTIONS);

            if ((resultsObject == null) || (!resultsObject.has("results"))) {
                // Connection error
                mNoNetwork.setVisibility(View.VISIBLE);
                mLoadingSuggestions.setVisibility(View.GONE);
                return;
            }

            mTaxonSuggestions = new ArrayList<>();

            JSONArray suggestions = resultsObject.getJSONArray("results").getJSONArray();

            for (int i = 0; i < suggestions.length(); i++) {
                mTaxonSuggestions.add(new BetterJSONObject(suggestions.optJSONObject(i)));
            }

            mTaxonCommonAncestor = null;
            if (resultsObject.has("common_ancestor")) {
                JSONObject commonAncestor = resultsObject.getJSONObject("common_ancestor");
                if (commonAncestor.has("taxon")) {
                    mTaxonCommonAncestor = new BetterJSONObject(commonAncestor);
                }
            }

            loadSuggestions();
        }
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        requestWindowFeature(Window.FEATURE_PROGRESS);

        super.onCreate(savedInstanceState);

        final ActionBar actionBar = getSupportActionBar();

        actionBar.hide();

        mApp = (INaturalistApp) getApplicationContext();
        mHelper = new ActivityHelper(this);

        Intent intent = getIntent();
        
        if (savedInstanceState == null) {
        	mObsPhotoFilename = intent.getStringExtra(OBS_PHOTO_FILENAME);
            mObsPhotoUrl = intent.getStringExtra(OBS_PHOTO_URL);
            mLongitude = intent.getDoubleExtra(LONGITUDE, 0);
            mLatitude = intent.getDoubleExtra(LATITUDE, 0);
            mObservedOn = (Timestamp) intent.getSerializableExtra(OBSERVED_ON);
        } else {
        	mObsPhotoFilename = savedInstanceState.getString(OBS_PHOTO_FILENAME);
            mObsPhotoUrl = savedInstanceState.getString(OBS_PHOTO_URL);
            mLongitude = savedInstanceState.getDouble(LONGITUDE, 0);
            mLatitude = savedInstanceState.getDouble(LATITUDE, 0);
            mObservedOn = (Timestamp) savedInstanceState.getSerializable(OBSERVED_ON);
            mTaxonSuggestions = loadListFromBundle(savedInstanceState, "mTaxonSuggestions");
        }

        setContentView(R.layout.taxon_suggestions);

        mObsPhoto = (ImageView) findViewById(R.id.observation_photo);
        mBackButton = findViewById(R.id.back);
        mSpeciesSearch = (ViewGroup) findViewById(R.id.species_search);
        mSuggestionsDescription = (TextView) findViewById(R.id.suggestions_description);
        mSuggestionsList = (ListView) findViewById(R.id.suggestions_list);
        mCommonAncestorDescription = (TextView) findViewById(R.id.common_ancestor_description);
        mCommonAncestorList = (ListView) findViewById(R.id.common_ancestor_list);
        mLoadingSuggestions = (ProgressBar) findViewById(R.id.loading_suggestions);
        mSuggestionsContainer = (ViewGroup) findViewById(R.id.suggestions_container);
        mNoNetwork = (TextView) findViewById(R.id.no_network);

        mNoNetwork.setVisibility(View.GONE);

        mLoadingSuggestions.setVisibility(View.VISIBLE);
        mSuggestionsContainer.setVisibility(View.GONE);

        mSpeciesSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(TaxonSuggestionsActivity.this, TaxonSearchActivity.class);
                intent.putExtra(TaxonSearchActivity.SPECIES_GUESS, "");
                intent.putExtra(TaxonSearchActivity.SHOW_UNKNOWN, true);
                startActivityForResult(intent, TAXON_SEARCH_REQUEST_CODE);
            }
        });

        mBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        });
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(OBS_PHOTO_FILENAME, mObsPhotoFilename);
        outState.putString(OBS_PHOTO_URL, mObsPhotoUrl);
        outState.putDouble(LONGITUDE, mLongitude);
        outState.putDouble(LATITUDE, mLatitude);
        outState.putSerializable(OBSERVED_ON, mObservedOn);
        saveListToBundle(outState, mTaxonSuggestions, "mTaxonSuggestions");

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();

        BaseFragmentActivity.safeUnregisterReceiver(mTaxonSuggestionsReceiver, this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mTaxonSuggestions == null) {
            // Get taxon suggestions
            mTaxonSuggestionsReceiver = new TaxonSuggestionsReceiver();
            IntentFilter filter = new IntentFilter(INaturalistService.ACTION_GET_TAXON_SUGGESTIONS_RESULT);
            BaseFragmentActivity.safeRegisterReceiver(mTaxonSuggestionsReceiver, filter, this);

            Intent serviceIntent = new Intent(INaturalistService.ACTION_GET_TAXON_SUGGESTIONS, null, this, INaturalistService.class);
            serviceIntent.putExtra(INaturalistService.OBS_PHOTO_FILENAME, mObsPhotoFilename);
            serviceIntent.putExtra(INaturalistService.OBS_PHOTO_URL, mObsPhotoUrl);
            serviceIntent.putExtra(INaturalistService.LONGITUDE, mLongitude);
            serviceIntent.putExtra(INaturalistService.LATITUDE, mLatitude);
            serviceIntent.putExtra(INaturalistService.OBSERVED_ON, mObservedOn);
            startService(serviceIntent);

            mLoadingSuggestions.setVisibility(View.VISIBLE);
            mSuggestionsContainer.setVisibility(View.GONE);
        } else {
            loadSuggestions();
        }


        RequestCreator request;

        if (mObsPhotoFilename == null) {
            // Load online photo
            request = Picasso.with(this).load(mObsPhotoUrl);
        } else {
            // Load offline (local) photo
            request = Picasso.with(this).load(new File(mObsPhotoFilename));
        }

        request
                .fit()
                .centerCrop()
                .into(mObsPhoto, new Callback() {
                    @Override
                    public void onSuccess() {
                    }
                    @Override
                    public void onError() {
                    }
                });

    }

    private void loadSuggestions() {
        mLoadingSuggestions.setVisibility(View.GONE);
        mSuggestionsContainer.setVisibility(View.VISIBLE);

        TaxonSuggestionAdapter.OnTaxonSuggestion onSuggestion = new TaxonSuggestionAdapter.OnTaxonSuggestion() {
            @Override
            public void onTaxonSelected(JSONObject taxon) {
                // Taxon selected - return that taxon back
                Intent intent = new Intent();
                Bundle bundle = new Bundle();
                bundle.putString(TaxonSearchActivity.ID_NAME, TaxonUtils.getTaxonName(TaxonSuggestionsActivity.this, taxon));
                bundle.putString(TaxonSearchActivity.TAXON_NAME, taxon.optString("name"));
                bundle.putString(TaxonSearchActivity.ICONIC_TAXON_NAME, taxon.optString("iconic_taxon_name"));
                if (taxon.has("default_photo") && !taxon.isNull("default_photo")) bundle.putString(TaxonSearchActivity.ID_PIC_URL, taxon.optJSONObject("default_photo").optString("square_url"));
                bundle.putBoolean(TaxonSearchActivity.IS_CUSTOM, false);
                bundle.putInt(TaxonSearchActivity.TAXON_ID, taxon.optInt("id"));

                intent.putExtras(bundle);
                setResult(RESULT_OK, intent);
                finish();
            }

            @Override
            public void onTaxonDetails(JSONObject taxon) {
                // Show taxon details screen
                Intent intent = new Intent(TaxonSuggestionsActivity.this, TaxonActivity.class);
                intent.putExtra(TaxonActivity.TAXON, new BetterJSONObject(taxon));
                intent.putExtra(TaxonActivity.DOWNLOAD_TAXON, true);
                intent.putExtra(TaxonActivity.TAXON_SUGGESTION, true);
                startActivityForResult(intent, TAXON_SEARCH_REQUEST_CODE);
            }
        };

        if (mTaxonCommonAncestor == null) {
            // No common ancestor
            mSuggestionsDescription.setText(String.format(getString(R.string.were_not_confident), mTaxonSuggestions.size()));
            mCommonAncestorDescription.setVisibility(View.GONE);
            mCommonAncestorList.setVisibility(View.GONE);
        } else {
            // Show common ancestor
            mSuggestionsDescription.setText(String.format(getString(R.string.top_species_suggestions), mTaxonSuggestions.size()));
            List<BetterJSONObject> commonAncestor = new ArrayList<>();
            commonAncestor.add(mTaxonCommonAncestor);
            mCommonAncestorList.setAdapter(new TaxonSuggestionAdapter(this, commonAncestor, onSuggestion));
            mCommonAncestorDescription.setVisibility(View.VISIBLE);
            mCommonAncestorList.setVisibility(View.VISIBLE);
        }

        mSuggestionsList.setAdapter(new TaxonSuggestionAdapter(this, mTaxonSuggestions, onSuggestion));
    }

    private void saveListToBundle(Bundle outState, List<BetterJSONObject> list, String key) {
        if (list != null) {
        	JSONArray arr = new JSONArray(list);
        	outState.putString(key, arr.toString());
        }
    }

    private ArrayList<BetterJSONObject> loadListFromBundle(Bundle savedInstanceState, String key) {
        ArrayList<BetterJSONObject> results = new ArrayList<BetterJSONObject>();

        String obsString = savedInstanceState.getString(key);
        if (obsString != null) {
            try {
                JSONArray arr = new JSONArray(obsString);
                for (int i = 0; i < arr.length(); i++) {
                    results.add(new BetterJSONObject(arr.getJSONObject(i)));
                }

                return results;
            } catch (JSONException exc) {
                exc.printStackTrace();
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == TAXON_SEARCH_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Copy results from taxon search directly back to the caller (e.g. observation editor)
                Intent intent = new Intent();
                Bundle bundle = data.getExtras();
                intent.putExtras(bundle);
                setResult(RESULT_OK, intent);

                finish();
            }
        }
    }
}

