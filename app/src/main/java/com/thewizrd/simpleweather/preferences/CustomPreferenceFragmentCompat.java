package com.thewizrd.simpleweather.preferences;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.appbar.AppBarLayout;
import com.thewizrd.shared_resources.helpers.OnBackPressedFragmentListener;
import com.thewizrd.shared_resources.utils.Colors;
import com.thewizrd.shared_resources.utils.DarkMode;
import com.thewizrd.shared_resources.utils.Settings;
import com.thewizrd.simpleweather.R;
import com.thewizrd.simpleweather.helpers.ActivityUtils;
import com.thewizrd.simpleweather.helpers.SystemBarColorManager;
import com.thewizrd.simpleweather.helpers.WindowColorManager;

public abstract class CustomPreferenceFragmentCompat extends PreferenceFragmentCompat
        implements OnBackPressedFragmentListener, WindowColorManager {

    private AppBarLayout mAppBarLayout;
    private CoordinatorLayout mRootView;
    protected Toolbar mToolbar;
    protected AppCompatActivity mActivity;
    protected SystemBarColorManager mSysBarColorsIface;

    private Configuration prevConfig;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mActivity = (AppCompatActivity) context;
        mSysBarColorsIface = (SystemBarColorManager) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mActivity = null;
        mSysBarColorsIface = null;
    }

    @Override
    public boolean onBackPressed() {
        return false;
    }

    protected abstract @StringRes
    int getTitle();

    @Override
    public void onResume() {
        super.onResume();

        prevConfig = new Configuration(getResources().getConfiguration());

        // Toolbar
        mToolbar.setTitle(getTitle());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container, Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.fragment_settings, container, false);

        View inflatedView = super.onCreateView(inflater, container, savedInstanceState);

        mRootView = (CoordinatorLayout) root;
        mAppBarLayout = root.findViewById(R.id.app_bar);
        mToolbar = root.findViewById(R.id.toolbar);
        mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mActivity.onBackPressed();
            }
        });

        CoordinatorLayout.LayoutParams lp = new CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setBehavior(new AppBarLayout.ScrollingViewBehavior());
        inflatedView.setLayoutParams(lp);

        root.addView(inflatedView);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updateWindowColors();
    }

    @CallSuper
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);

        if (!hidden && this.isVisible()) {
            updateWindowColors();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        int diff = newConfig.diff(prevConfig);
        if ((diff & ActivityInfo.CONFIG_UI_MODE) != 0) {
            updateWindowColors();
            getListView().setAdapter(getListView().getAdapter());
        }

        prevConfig = new Configuration(newConfig);
    }

    public final void updateWindowColors() {
        updateWindowColors(Settings.getUserThemeMode());
    }

    protected final void updateWindowColors(DarkMode mode) {
        int currentNightMode = AppCompatDelegate.getDefaultNightMode();
        int color = ActivityUtils.getColor(mActivity, R.attr.colorPrimary);
        if (currentNightMode == AppCompatDelegate.MODE_NIGHT_YES) {
            if (mode == DarkMode.AMOLED_DARK) {
                color = Colors.BLACK;
            } else {
                color = ActivityUtils.getColor(mActivity, android.R.attr.colorBackground);
            }
        }
        int bg_color = color != Colors.BLACK ? ActivityUtils.getColor(mActivity, android.R.attr.colorBackground) : color;
        mRootView.setBackgroundColor(bg_color);
        mAppBarLayout.setBackgroundColor(color);
        mRootView.setStatusBarBackgroundColor(color);
        if (mSysBarColorsIface != null) {
            mSysBarColorsIface.setSystemBarColors(Colors.TRANSPARENT, color);
        }
    }
}
