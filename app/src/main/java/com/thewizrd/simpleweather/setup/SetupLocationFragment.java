package com.thewizrd.simpleweather.setup;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.MarginLayoutParamsCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.AutoTransition;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.stepstone.stepper.Step;
import com.stepstone.stepper.StepperLayout;
import com.stepstone.stepper.VerificationError;
import com.thewizrd.shared_resources.AsyncTask;
import com.thewizrd.shared_resources.adapters.LocationQueryAdapter;
import com.thewizrd.shared_resources.controls.LocationQuery;
import com.thewizrd.shared_resources.controls.LocationQueryViewModel;
import com.thewizrd.shared_resources.helpers.OnBackPressedFragmentListener;
import com.thewizrd.shared_resources.helpers.RecyclerOnClickListenerInterface;
import com.thewizrd.shared_resources.helpers.WearableHelper;
import com.thewizrd.shared_resources.locationdata.LocationData;
import com.thewizrd.shared_resources.locationdata.here.HERELocationProvider;
import com.thewizrd.shared_resources.utils.Colors;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.shared_resources.utils.Settings;
import com.thewizrd.shared_resources.utils.StringUtils;
import com.thewizrd.shared_resources.utils.WeatherException;
import com.thewizrd.shared_resources.weatherdata.Weather;
import com.thewizrd.shared_resources.weatherdata.WeatherAPI;
import com.thewizrd.shared_resources.weatherdata.WeatherManager;
import com.thewizrd.simpleweather.App;
import com.thewizrd.simpleweather.R;
import com.thewizrd.simpleweather.fragments.LocationSearchFragment;
import com.thewizrd.simpleweather.wearable.WearableDataListenerService;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SetupLocationFragment extends Fragment implements Step, OnBackPressedFragmentListener {

    private View mSearchFragmentContainer;
    private LocationSearchFragment mSearchFragment;
    private View searchViewContainer;
    private boolean inSearchUI;

    private static final int ANIMATION_DURATION = 240;

    // Views
    private View mMainView;
    private Button gpsFollowButton;
    private ProgressBar progressBar;
    private FusedLocationProviderClient mFusedLocationClient;
    private Location mLocation;
    private LocationCallback mLocCallback;
    private LocationListener mLocListnr;
    private CancellationTokenSource cts;
    private LocationData location = null;

    /**
     * Tracks the status of the location updates request.
     */
    private boolean mRequestingLocationUpdates;

    private AppCompatActivity mActivity;
    private StepperDataManager mDataManager;
    private StepperLayout mStepperLayout;

    private static final int PERMISSION_LOCATION_REQUEST_CODE = 0;

    private WeatherManager wm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_setup_location, container, false);
        mMainView = view;
        wm = WeatherManager.getInstance();

        mStepperLayout = mActivity.findViewById(R.id.stepperLayout);

        searchViewContainer = view.findViewById(R.id.search_bar);
        mSearchFragmentContainer = view.findViewById(R.id.search_fragment_container);
        gpsFollowButton = view.findViewById(R.id.gps_follow);
        progressBar = view.findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);

        // NOTE: Bug: Explicitly set tintmode on Lollipop devices
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP)
            progressBar.setIndeterminateTintMode(PorterDuff.Mode.SRC_IN);

        // Tint drawable in button view
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            for (Drawable drawable : gpsFollowButton.getCompoundDrawables()) {
                if (drawable != null) {
                    Configuration config = mActivity.getResources().getConfiguration();
                    final int currentNightMode = config.uiMode & Configuration.UI_MODE_NIGHT_MASK;

                    drawable.setColorFilter(new PorterDuffColorFilter(
                            currentNightMode == Configuration.UI_MODE_NIGHT_YES ? Colors.WHITE : Colors.SIMPLEBLUE, PorterDuff.Mode.SRC_IN));
                    TextViewCompat.setCompoundDrawablesRelative(gpsFollowButton, drawable, null, null, null);
                }
            }
        }

        /* Event Listeners */
        searchViewContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Setup search UI
                prepareSearchUI();
            }
        });

        mSearchFragmentContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitSearchUi(false);
            }
        });

        gpsFollowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchGeoLocation();
            }
        });

        // Location Listener
        if (WearableHelper.isGooglePlayServicesInstalled()) {
            mFusedLocationClient = new FusedLocationProviderClient(mActivity);
            mLocCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    if (locationResult == null)
                        mLocation = null;
                    else
                        mLocation = locationResult.getLastLocation();

                    if (mLocation == null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                enableControls(true);
                                Toast.makeText(mActivity, R.string.error_retrieve_location, Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        fetchGeoLocation();
                    }

                    stopLocationUpdates();
                }

                @Override
                public void onLocationAvailability(LocationAvailability locationAvailability) {
                    new AsyncTask<Void>().await(new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            return Tasks.await(mFusedLocationClient.flushLocations());
                        }
                    });

                    if (!locationAvailability.isLocationAvailable()) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                stopLocationUpdates();
                                enableControls(true);
                                Toast.makeText(mActivity, R.string.error_retrieve_location, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            };
        } else {
            mLocListnr = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    mLocation = location;
                    fetchGeoLocation();
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {

                }

                @Override
                public void onProviderEnabled(String provider) {

                }

                @Override
                public void onProviderDisabled(String provider) {

                }
            };
        }

        mRequestingLocationUpdates = false;

        // Reset focus
        view.requestFocus();

        // Set default API to HERE
        Settings.setAPI(WeatherAPI.HERE);
        wm.updateAPI();

        if (StringUtils.isNullOrWhitespace(wm.getAPIKey())) {
            // If (internal) key doesn't exist, fallback to Yahoo
            Settings.setAPI(WeatherAPI.YAHOO);
            wm.updateAPI();
            Settings.setPersonalKey(true);
            Settings.setKeyVerified(false);
        } else {
            // If key exists, go ahead
            Settings.setPersonalKey(false);
            Settings.setKeyVerified(true);
        }

        // Get SearchUI state
        if (savedInstanceState != null && savedInstanceState.getBoolean("SearchUI", false)) {
            inSearchUI = true;

            // Restart SearchUI
            prepareSearchUI();
        }

        return view;
    }

    /**
     * Removes location updates from the FusedLocationApi.
     */
    private void stopLocationUpdates() {
        if (!mRequestingLocationUpdates) {
            Logger.writeLine(Log.DEBUG, "SetupLocationFragment: stopLocationUpdates: updates never requested, no-op.");
            return;
        }

        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.
        mFusedLocationClient.removeLocationUpdates(mLocCallback)
                .addOnCompleteListener(mActivity, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        mRequestingLocationUpdates = false;
                    }
                });
    }

    private void ctsCancel() {
        if (cts != null) cts.cancel();
        cts = new CancellationTokenSource();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        mActivity = (AppCompatActivity) context;
        mDataManager = (StepperDataManager) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();

        mActivity = null;
        mDataManager = null;
    }

    @Override
    public void onPause() {
        ctsCancel();
        super.onPause();
        // Remove location updates to save battery.
        stopLocationUpdates();
    }

    @Override
    public void onDestroy() {
        // Cancel pending actions
        ctsCancel();

        super.onDestroy();
        mActivity = null;
        mDataManager = null;
    }

    private void runOnUiThread(Runnable action) {
        if (mActivity != null)
            mActivity.runOnUiThread(action);
    }

    @Override
    public boolean onBackPressed() {
        if (inSearchUI) {
            // We should let the user go back to usual screens with tabs.
            exitSearchUi(false);
            return true;
        }

        return false;
    }

    private void enableControls(final boolean enable) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                searchViewContainer.setEnabled(enable);
                gpsFollowButton.setEnabled(enable);
                progressBar.setVisibility(enable ? View.GONE : View.VISIBLE);
            }
        });
    }

    private RecyclerOnClickListenerInterface recyclerClickInterface = new RecyclerOnClickListenerInterface() {
        @Override
        public void onClick(final View view, final int position) {
            if (mSearchFragment == null)
                return;

            AsyncTask.run(new Runnable() {
                @Override
                public void run() {
                    if (mActivity != null) {
                        // Get selected query view
                        final LocationQueryAdapter adapter = mSearchFragment.getAdapter();
                        LocationQuery v = (LocationQuery) view;
                        LocationQueryViewModel query_vm = null;

                        try {
                            if (!StringUtils.isNullOrEmpty(adapter.getDataset().get(position).getLocationQuery()))
                                query_vm = adapter.getDataset().get(position);
                        } catch (ArrayIndexOutOfBoundsException ex) {
                            query_vm = null;
                        } finally {
                            if (query_vm == null)
                                query_vm = new LocationQueryViewModel();
                        }

                        if (StringUtils.isNullOrWhitespace(query_vm.getLocationQuery())) {
                            // Stop since there is no valid query
                            return;
                        }

                        if (Settings.usePersonalKey() && StringUtils.isNullOrWhitespace(Settings.getAPIKEY()) && wm.isKeyRequired()) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(App.getInstance().getAppContext(), R.string.werror_invalidkey, Toast.LENGTH_SHORT).show();
                                }
                            });
                            return;
                        }

                        // Cancel pending search
                        mSearchFragment.ctsCancel();
                        CancellationToken ctsToken = mSearchFragment.getCancellationTokenSource().getToken();

                        mSearchFragment.showLoading(true);

                        if (ctsToken.isCancellationRequested()) {
                            mSearchFragment.showLoading(false);
                            return;
                        }

                        // Need to get FULL location data for HERE API
                        // Data provided is incomplete
                        if (WeatherAPI.HERE.equals(query_vm.getLocationSource())
                                && query_vm.getLocationLat() == -1 && query_vm.getLocationLong() == -1
                                && query_vm.getLocationTZLong() == null) {
                            final LocationQueryViewModel loc = query_vm;
                            query_vm = new AsyncTask<LocationQueryViewModel>().await(new Callable<LocationQueryViewModel>() {
                                @Override
                                public LocationQueryViewModel call() throws Exception {
                                    return new HERELocationProvider().getLocationfromLocID(loc.getLocationQuery(), loc.getWeatherSource());
                                }
                            });
                        }

                        // Get weather data
                        location = new LocationData(query_vm);
                        if (!location.isValid()) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(App.getInstance().getAppContext(), R.string.werror_noweather, Toast.LENGTH_SHORT).show();
                                    mSearchFragment.showLoading(false);
                                }
                            });
                            return;
                        }
                        Weather weather = Settings.getWeatherData(location.getQuery());
                        if (weather == null) {
                            try {
                                weather = wm.getWeather(location);
                            } catch (final WeatherException wEx) {
                                weather = null;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(App.getInstance().getAppContext(), wEx.getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }

                        if (weather == null) {
                            mSearchFragment.showLoading(false);
                            return;
                        }

                        // We got our data so disable controls just in case
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                adapter.getDataset().clear();
                                adapter.notifyDataSetChanged();
                                if (mSearchFragment != null && mSearchFragment.getView() != null &&
                                        mSearchFragment.getView().findViewById(R.id.recycler_view) instanceof RecyclerView) {
                                    RecyclerView recyclerView = mSearchFragment.getView().findViewById(R.id.recycler_view);
                                    recyclerView.setEnabled(false);
                                }
                            }
                        });

                        // Save weather data
                        Settings.deleteLocations();
                        Settings.addLocation(location);
                        if (wm.supportsAlerts() && weather.getWeatherAlerts() != null)
                            Settings.saveWeatherAlerts(location, weather.getWeatherAlerts());
                        Settings.saveWeatherData(weather);

                        // If we're using search
                        // make sure gps feature is off
                        Settings.setFollowGPS(false);
                        Settings.setWeatherLoaded(true);

                        // Send data for wearables
                        if (mActivity != null) {
                            WearableDataListenerService.enqueueWork(mActivity,
                                    new Intent(mActivity, WearableDataListenerService.class)
                                            .setAction(WearableDataListenerService.ACTION_SENDSETTINGSUPDATE));
                            WearableDataListenerService.enqueueWork(mActivity,
                                    new Intent(mActivity, WearableDataListenerService.class)
                                            .setAction(WearableDataListenerService.ACTION_SENDLOCATIONUPDATE));
                            WearableDataListenerService.enqueueWork(mActivity,
                                    new Intent(mActivity, WearableDataListenerService.class)
                                            .setAction(WearableDataListenerService.ACTION_SENDWEATHERUPDATE));
                        }

                        // Setup complete
                        mDataManager.getArguments().putString("data", location.toJson());
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mStepperLayout.proceed();
                            }
                        });
                    }
                }
            });
        }
    };

    private void fetchGeoLocation() {
        gpsFollowButton.setEnabled(false);

        // Show loading bar
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.VISIBLE);
            }
        });

        AsyncTask.run(new Runnable() {
            @Override
            public void run() {
                try {
                    if (mLocation != null) {
                        LocationQueryViewModel view = null;

                        // Cancel other tasks
                        ctsCancel();
                        CancellationToken ctsToken = cts.getToken();

                        if (ctsToken.isCancellationRequested()) throw new InterruptedException();

                        // Show loading bar
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressBar.setVisibility(View.VISIBLE);
                            }
                        });

                        if (ctsToken.isCancellationRequested()) throw new InterruptedException();

                        view = wm.getLocation(mLocation);

                        if (StringUtils.isNullOrWhitespace(view.getLocationQuery()))
                            view = new LocationQueryViewModel();

                        if (StringUtils.isNullOrWhitespace(view.getLocationQuery())) {
                            enableControls(true);
                            return;
                        }

                        if (Settings.usePersonalKey() && StringUtils.isNullOrWhitespace(Settings.getAPIKEY()) && wm.isKeyRequired()) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(mActivity, R.string.werror_invalidkey, Toast.LENGTH_SHORT).show();
                                }
                            });
                            enableControls(true);
                            return;
                        }

                        if (ctsToken.isCancellationRequested()) throw new InterruptedException();

                        // Get Weather Data
                        location = new LocationData(view, mLocation);
                        if (!location.isValid()) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(mActivity, getString(R.string.werror_noweather), Toast.LENGTH_SHORT).show();
                                }
                            });
                            enableControls(true);
                            return;
                        }

                        if (ctsToken.isCancellationRequested()) throw new InterruptedException();

                        Weather weather = Settings.getWeatherData(location.getQuery());
                        if (weather == null) {
                            if (ctsToken.isCancellationRequested())
                                throw new InterruptedException();

                            try {
                                TaskCompletionSource<Weather> tcs = new TaskCompletionSource<>(ctsToken);
                                tcs.setResult(wm.getWeather(location));
                                weather = Tasks.await(tcs.getTask());
                            } catch (final WeatherException wEx) {
                                weather = null;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(mActivity, wEx.getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }

                        if (weather == null) {
                            enableControls(true);
                            return;
                        }

                        if (ctsToken.isCancellationRequested()) throw new InterruptedException();

                        // We got our data so disable controls just in case
                        enableControls(false);

                        // Save weather data
                        Settings.saveLastGPSLocData(location);
                        Settings.deleteLocations();
                        Settings.addLocation(new LocationData(view));
                        if (wm.supportsAlerts() && weather.getWeatherAlerts() != null)
                            Settings.saveWeatherAlerts(location, weather.getWeatherAlerts());
                        Settings.saveWeatherData(weather);

                        Settings.setFollowGPS(true);
                        Settings.setWeatherLoaded(true);

                        // Send data for wearables
                        if (mActivity != null) {
                            mActivity.startService(new Intent(mActivity, WearableDataListenerService.class)
                                    .setAction(WearableDataListenerService.ACTION_SENDSETTINGSUPDATE));
                            mActivity.startService(new Intent(mActivity, WearableDataListenerService.class)
                                    .setAction(WearableDataListenerService.ACTION_SENDLOCATIONUPDATE));
                            mActivity.startService(new Intent(mActivity, WearableDataListenerService.class)
                                    .setAction(WearableDataListenerService.ACTION_SENDWEATHERUPDATE));
                        }

                        // Setup complete
                        mDataManager.getArguments().putString("data", location.toJson());
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mStepperLayout.proceed();
                            }
                        });
                    } else {
                        updateLocation();
                    }
                } catch (Exception e) {
                    // Restore controls
                    enableControls(true);
                    Settings.setFollowGPS(false);
                    Settings.setWeatherLoaded(false);
                }
            }
        });
    }

    private void updateLocation() {
        if (mActivity != null && ContextCompat.checkSelfPermission(mActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(mActivity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSION_LOCATION_REQUEST_CODE);
            return;
        }

        Location location = null;

        if (WearableHelper.isGooglePlayServicesInstalled()) {
            location = new AsyncTask<Location>().await(new Callable<Location>() {
                @SuppressLint("MissingPermission")
                @Override
                public Location call() throws Exception {
                    Location result = null;
                    try {
                        result = Tasks.await(mFusedLocationClient.getLastLocation(), 5, TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        Logger.writeLine(Log.ERROR, e);
                    }
                    return result;
                }
            });

            /**
             * Request start of location updates. Does nothing if
             * updates have already been requested.
             */
            if (location == null && !mRequestingLocationUpdates) {
                final LocationRequest mLocationRequest = new LocationRequest();
                mLocationRequest.setNumUpdates(1);
                mLocationRequest.setInterval(10000);
                mLocationRequest.setFastestInterval(1000);
                mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                mRequestingLocationUpdates = true;
                mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocCallback, Looper.getMainLooper());
            }
        } else {
            LocationManager locMan = (LocationManager) mActivity.getSystemService(Context.LOCATION_SERVICE);
            boolean isGPSEnabled = false;
            boolean isNetEnabled = false;
            if (locMan != null) {
                isGPSEnabled = locMan.isProviderEnabled(LocationManager.GPS_PROVIDER);
                isNetEnabled = locMan.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            }

            if (isGPSEnabled) {
                location = locMan.getLastKnownLocation(LocationManager.GPS_PROVIDER);

                if (location == null)
                    location = locMan.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

                if (location == null)
                    locMan.requestSingleUpdate(LocationManager.GPS_PROVIDER, mLocListnr, Looper.getMainLooper());
            } else if (isNetEnabled) {
                location = locMan.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

                if (location == null)
                    locMan.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, mLocListnr, null);
            } else {
                enableControls(true);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(mActivity, R.string.error_retrieve_location, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

        if (location != null && !mRequestingLocationUpdates) {
            mLocation = location;
            fetchGeoLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_LOCATION_REQUEST_CODE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    // Do the task you need to do.
                    fetchGeoLocation();
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    enableControls(true);
                    Toast.makeText(mActivity, R.string.error_location_denied, Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }

    @Override
    public void onAttachFragment(@NonNull Fragment childFragment) {
        if (childFragment instanceof LocationSearchFragment) {
            mSearchFragment = (LocationSearchFragment) childFragment;
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        // Save SearchUI state
        outState.putBoolean("SearchUI", inSearchUI);

        super.onSaveInstanceState(outState);
    }

    private void prepareSearchUI() {
        // Hide stepper nav bar
        mStepperLayout.setShowBottomNavigation(false);
        ctsCancel();

        enterSearchUi();
        enterSearchUiTransition(null);
    }

    private void enterSearchUi() {
        inSearchUI = true;
        if (mSearchFragment == null) {
            addSearchFragment();
            return;
        }
        if (mSearchFragment.getRecyclerOnClickListener() == null)
            mSearchFragment.setRecyclerOnClickListener(recyclerClickInterface);
        mSearchFragment.setUserVisibleHint(true);
        final FragmentTransaction transaction = getChildFragmentManager()
                .beginTransaction();
        transaction.show(mSearchFragment);
        transaction.commitAllowingStateLoss();
        getChildFragmentManager().executePendingTransactions();
    }

    private void enterSearchUiTransition(Animation.AnimationListener enterAnimationListener) {
        // SearchViewContainer margin transition
        Transition transition = new AutoTransition();
        transition.setDuration(ANIMATION_DURATION);
        TransitionManager.beginDelayedTransition((ViewGroup) searchViewContainer, transition);
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) searchViewContainer.getLayoutParams();
        MarginLayoutParamsCompat.setMarginEnd(params, 0);
        MarginLayoutParamsCompat.setMarginStart(params, 0);
        searchViewContainer.setLayoutParams(params);

        // SearchViewContainer fade/translation animation
        AnimationSet searchViewAniSet = new AnimationSet(true);
        searchViewAniSet.setInterpolator(new DecelerateInterpolator());
        AlphaAnimation searchViewFadeAni = new AlphaAnimation(1.0f, 0.0f);
        TranslateAnimation searchViewAnimation = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0,
                Animation.RELATIVE_TO_SELF, 0,
                Animation.ABSOLUTE, 0,
                Animation.ABSOLUTE, -(searchViewContainer.getY()));
        searchViewAniSet.setDuration((long) (ANIMATION_DURATION * 1.25));
        searchViewAniSet.setFillEnabled(false);
        searchViewAniSet.setAnimationListener(enterAnimationListener);
        searchViewAniSet.addAnimation(searchViewAnimation);
        searchViewAniSet.addAnimation(searchViewFadeAni);

        // FragmentContainer fade/translation animation
        AnimationSet fragmentAniSet = new AnimationSet(true);
        fragmentAniSet.setInterpolator(new DecelerateInterpolator());
        AlphaAnimation fragFadeAni = new AlphaAnimation(0.0f, 1.0f);
        TranslateAnimation fragmentAnimation = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0,
                Animation.RELATIVE_TO_SELF, 0,
                Animation.ABSOLUTE, searchViewContainer.getY(),
                Animation.ABSOLUTE, 0);
        fragmentAniSet.setDuration(ANIMATION_DURATION);
        fragmentAniSet.setFillEnabled(false);
        fragmentAniSet.addAnimation(fragFadeAni);
        fragmentAniSet.addAnimation(fragmentAnimation);

        mSearchFragmentContainer.startAnimation(fragmentAniSet);
        searchViewContainer.startAnimation(searchViewAniSet);
    }

    private void addSearchFragment() {
        if (mSearchFragment != null) {
            return;
        }
        final FragmentTransaction ft = getChildFragmentManager().beginTransaction();
        final LocationSearchFragment searchFragment = new LocationSearchFragment();
        searchFragment.setRecyclerOnClickListener(recyclerClickInterface);
        searchFragment.setUserVisibleHint(false);
        ft.add(R.id.search_fragment_container, searchFragment);
        ft.commitNowAllowingStateLoss();
    }

    private void removeSearchFragment() {
        mSearchFragment.setUserVisibleHint(false);
        final FragmentTransaction transaction = getChildFragmentManager()
                .beginTransaction();
        transaction.remove(mSearchFragment);
        mSearchFragment = null;
        transaction.commitAllowingStateLoss();
    }

    private void exitSearchUi(boolean skipAnimation) {
        if (mSearchFragment != null) {
            // Exit transition
            if (skipAnimation) {
                removeSearchFragment();
            } else {
                exitSearchUiTransition(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        // Remove fragment once animation ends
                        removeSearchFragment();
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {

                    }
                });
            }
        }

        hideInputMethod(mActivity == null ? null : mActivity.getCurrentFocus());
        mStepperLayout.setShowBottomNavigation(true);
        if (mMainView != null) mMainView.requestFocus();
        inSearchUI = false;
    }

    private void exitSearchUiTransition(Animation.AnimationListener exitAnimationListener) {
        // SearchViewContainer margin transition
        searchViewContainer.setVisibility(View.VISIBLE);
        Transition transition = new AutoTransition();
        transition.setDuration(ANIMATION_DURATION);
        TransitionManager.beginDelayedTransition((ViewGroup) searchViewContainer, transition);
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) searchViewContainer.getLayoutParams();
        int marginHoriz = getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
        MarginLayoutParamsCompat.setMarginEnd(params, marginHoriz);
        MarginLayoutParamsCompat.setMarginStart(params, marginHoriz);
        searchViewContainer.setLayoutParams(params);

        // SearchViewContainer translation animation
        TranslateAnimation searchBarAnimation = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0,
                Animation.RELATIVE_TO_SELF, 0,
                Animation.ABSOLUTE, -searchViewContainer.getY(),
                Animation.ABSOLUTE, 0);
        searchBarAnimation.setDuration(ANIMATION_DURATION);
        searchBarAnimation.setFillEnabled(false);
        searchBarAnimation.setInterpolator(new DecelerateInterpolator());

        // FragmentContainer fade/translation animation
        AnimationSet fragmentAniSet = new AnimationSet(true);
        fragmentAniSet.setInterpolator(new DecelerateInterpolator());
        AlphaAnimation fragFadeAni = new AlphaAnimation(1.0f, 0.0f);
        TranslateAnimation fragmentAnimation = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0,
                Animation.RELATIVE_TO_SELF, 0,
                Animation.ABSOLUTE, 0,
                Animation.ABSOLUTE, searchViewContainer.getY());
        fragmentAniSet.setDuration(ANIMATION_DURATION);
        fragmentAniSet.setFillEnabled(false);
        fragmentAniSet.addAnimation(fragFadeAni);
        fragmentAniSet.addAnimation(fragmentAnimation);
        fragmentAniSet.setAnimationListener(exitAnimationListener);

        mSearchFragmentContainer.startAnimation(fragmentAniSet);
        searchViewContainer.startAnimation(searchBarAnimation);
    }

    private void showInputMethod(View view) {
        if (mActivity != null) {
            InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            if (imm != null && view != null) {
                imm.showSoftInput(view, 0);
            }
        }
    }

    private void hideInputMethod(View view) {
        if (mActivity != null) {
            InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            if (imm != null && view != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    @Nullable
    @Override
    public VerificationError verifyStep() {
        if (mDataManager != null) {
            if (Settings.isWeatherLoaded()) {
                if (location != null)
                    mDataManager.getArguments().putString("data", location.toJson());

                if (inSearchUI) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            exitSearchUi(true);
                        }
                    });
                }
                return null;
            }
        }
        return new VerificationError("Invalid activity");
    }

    @Override
    public void onSelected() {

    }

    @Override
    public void onError(@NonNull VerificationError error) {

    }
}