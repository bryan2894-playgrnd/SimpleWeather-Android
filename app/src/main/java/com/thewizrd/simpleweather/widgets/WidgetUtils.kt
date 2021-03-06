package com.thewizrd.simpleweather.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.util.SparseArray
import androidx.annotation.ColorInt
import androidx.core.content.edit
import com.google.gson.reflect.TypeToken
import com.thewizrd.shared_resources.Constants
import com.thewizrd.shared_resources.helpers.ContextUtils
import com.thewizrd.shared_resources.locationdata.LocationData
import com.thewizrd.shared_resources.utils.Colors
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simpleweather.App
import com.thewizrd.simpleweather.utils.ArrayUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.*

object WidgetUtils {
    // Shared Settings
    private val settingsMgr = App.instance.settingsManager
    private val widgetPrefs =
        App.instance.appContext.getSharedPreferences("appwidgets", Context.MODE_PRIVATE)

    // Widget Prefs
    private const val CurrentPrefsVersion = 5

    // Keys
    private const val KEY_VERSION = "key_version"
    private const val KEY_LOCATIONDATA = "key_locationdata"
    private const val KEY_LOCATIONQUERY = "key_locationquery"
    private const val KEY_WIDGETBACKGROUND = "key_widgetbackground"
    private const val KEY_WIDGETBACKGROUNDSTYLE = "key_widgetbackgroundstyle"
    private const val KEY_HIDELOCATIONNAME = "key_hidelocationname"
    private const val KEY_HIDESETTINGSBUTTON = "key_hidesettingsbutton"
    private const val KEY_CLOCKAPP = "key_clockapp"
    private const val KEY_CALENDARAPP = "key_calendarapp"
    private const val KEY_FORECASTOPTION = "key_fcastoption"
    private const val KEY_TAP2SWITCH = "key_tap2switch"
    private const val KEY_USETIMEZONE = "key_usetimezone"
    private const val KEY_BGCOLORCODE = "key_bgcolorcode"
    private const val KEY_TXTCOLORCODE = "key_txtcolorcode"

    private const val FORECAST_LENGTH = 3 // 3-day
    private const val MEDIUM_FORECAST_LENGTH = 4 // 4-day
    private const val WIDE_FORECAST_LENGTH = 5 // 5-day

    init {
        init()
    }

    enum class WidgetBackground(val value: Int) {
        CURRENT_CONDITIONS(0),
        TRANSPARENT(1),
        CUSTOM(2);

        companion object {
            private val map = SparseArray<WidgetBackground>()

            init {
                for (background in values()) {
                    map.put(background.value, background)
                }
            }

            fun valueOf(value: Int): WidgetBackground {
                return map[value]
            }
        }
    }

    enum class WidgetBackgroundStyle(val value: Int) {
        FULLBACKGROUND(0),
        PANDA(1),
        PENDINGCOLOR(2),
        DARK(3),
        LIGHT(4);

        companion object {
            private val map = SparseArray<WidgetBackgroundStyle>()

            init {
                for (style in values()) {
                    map.put(style.value, style)
                }
            }

            fun valueOf(value: Int): WidgetBackgroundStyle {
                // NOTE: set default since we removed a style here
                return map[value, PANDA]
            }
        }
    }

    enum class ForecastOption(val value: Int) {
        FULL(0),
        DAILY(1),
        HOURLY(2);

        companion object {
            private val map = SparseArray<ForecastOption>()

            init {
                for (opt in values()) {
                    map.put(opt.value, opt)
                }
            }

            fun valueOf(value: Int): ForecastOption {
                return map[value]
            }
        }
    }

    private fun init() {
        if (getVersion() < CurrentPrefsVersion) {
            when (getVersion()) {
                // TODO: remove this old migration path
                2 -> {
                    runBlocking(Dispatchers.Default) {
                        if (settingsMgr.isWeatherLoaded()) {
                            val homeLocation = settingsMgr.getHomeData()
                            val widgetMap = widgetPrefs.all
                            for (key in widgetMap.keys) {
                                if (key == homeLocation!!.query) {
                                    widgetPrefs.edit(true) {
                                        putString(Constants.KEY_GPS, widgetMap[key]?.toString())
                                        remove(key)
                                    }
                                    break
                                }
                            }
                        }
                    }
                }
                3 -> {
                    // Migrate color options
                    val widgetIds = getAllWidgetIds()
                    for (appWidgetId in widgetIds) {
                        val prefs = getPreferences(appWidgetId)
                        val value = prefs.getString(KEY_WIDGETBACKGROUND, null)
                        if (value != null) {
                            when (value.toIntOrNull() ?: -1) {
                                0 -> { /* no-op */
                                }
                                1 -> {
                                    setWidgetBackground(appWidgetId, WidgetBackground.CUSTOM.value)
                                    setBackgroundColor(appWidgetId, Colors.WHITE)
                                    setTextColor(appWidgetId, Colors.BLACK)
                                }
                                2 -> {
                                    setWidgetBackground(appWidgetId, WidgetBackground.CUSTOM.value)
                                    setBackgroundColor(appWidgetId, Colors.BLACK)
                                    setTextColor(appWidgetId, Colors.WHITE)
                                }
                                4 -> {
                                    setWidgetBackground(appWidgetId, WidgetBackground.CUSTOM.value)
                                }
                                else -> { /* 3 - Old: Transparent */
                                    setWidgetBackground(
                                        appWidgetId,
                                        WidgetBackground.TRANSPARENT.value
                                    )
                                }
                            }
                        }
                    }
                }
                4 -> {
                    // Migrate color options
                    val widgetIds4x1 = getWidgetIds(WidgetType.Widget4x1)
                    for (appWidgetId in widgetIds4x1) {
                        setWidgetBackground(appWidgetId, WidgetBackground.TRANSPARENT.value)
                    }
                }
            }

            // Set to latest version
            setVersion(CurrentPrefsVersion)
        }
    }

    private fun getVersion(): Int {
        return widgetPrefs.getString(KEY_VERSION, CurrentPrefsVersion.toString())!!.toInt()
    }

    private fun setVersion(value: Int) {
        widgetPrefs.edit {
            putString(KEY_VERSION, value.toString())
        }
    }

    private fun getAllWidgetIds(): IntArray {
        val mAppWidgetManager = AppWidgetManager.getInstance(App.instance.appContext)
        val mAppWidget1x1 = WeatherWidgetProvider1x1.Info.getInstance()
        val mAppWidget2x2 = WeatherWidgetProvider2x2.Info.getInstance()
        val mAppWidget4x1 = WeatherWidgetProvider4x1.Info.getInstance()
        val mAppWidget4x2 = WeatherWidgetProvider4x2.Info.getInstance()
        val mAppWidget4x1G = WeatherWidgetProvider4x1.Info.getInstance()
        val mAppWidget4x1N = WeatherWidgetProvider4x1Notification.Info.getInstance()
        val mAppWidget4x2C = WeatherWidgetProvider4x2Clock.Info.getInstance()
        val mAppWidget4x2BC = WeatherWidgetProvider4x2Huawei.Info.getInstance()

        return ArrayUtils.concat(
            mAppWidgetManager.getAppWidgetIds(mAppWidget1x1.componentName),
            mAppWidgetManager.getAppWidgetIds(mAppWidget2x2.componentName),
            mAppWidgetManager.getAppWidgetIds(mAppWidget4x1.componentName),
            mAppWidgetManager.getAppWidgetIds(mAppWidget4x2.componentName),
            mAppWidgetManager.getAppWidgetIds(mAppWidget4x1G.componentName),
            mAppWidgetManager.getAppWidgetIds(mAppWidget4x1N.componentName),
            mAppWidgetManager.getAppWidgetIds(mAppWidget4x2C.componentName),
            mAppWidgetManager.getAppWidgetIds(mAppWidget4x2BC.componentName)
        )
    }

    private fun getWidgetIds(widgetType: WidgetType): IntArray {
        val mAppWidgetManager = AppWidgetManager.getInstance(App.instance.appContext)

        return when (widgetType) {
            WidgetType.Unknown -> IntArray(0)
            WidgetType.Widget1x1 -> {
                mAppWidgetManager.getAppWidgetIds(WeatherWidgetProvider1x1.Info.getInstance().componentName)
            }
            WidgetType.Widget2x2 -> {
                mAppWidgetManager.getAppWidgetIds(WeatherWidgetProvider2x2.Info.getInstance().componentName)
            }
            WidgetType.Widget4x1 -> {
                mAppWidgetManager.getAppWidgetIds(WeatherWidgetProvider4x1.Info.getInstance().componentName)
            }
            WidgetType.Widget4x2 -> {
                mAppWidgetManager.getAppWidgetIds(WeatherWidgetProvider4x2.Info.getInstance().componentName)
            }
            WidgetType.Widget4x1Google -> mAppWidgetManager.getAppWidgetIds(
                WeatherWidgetProvider4x1Google.Info.getInstance().componentName
            )
            WidgetType.Widget4x1Notification -> mAppWidgetManager.getAppWidgetIds(
                WeatherWidgetProvider4x1Notification.Info.getInstance().componentName
            )
            WidgetType.Widget4x2Clock -> mAppWidgetManager.getAppWidgetIds(
                WeatherWidgetProvider4x2Clock.Info.getInstance().componentName
            )
            WidgetType.Widget4x2Huawei -> mAppWidgetManager.getAppWidgetIds(
                WeatherWidgetProvider4x2Huawei.Info.getInstance().componentName
            )
        }
    }

    fun getWidgetProviderInfoFromType(widgetType: WidgetType): WidgetProviderInfo? {
        return when (widgetType) {
            WidgetType.Unknown -> null
            WidgetType.Widget1x1 -> WeatherWidgetProvider1x1.Info.getInstance()
            WidgetType.Widget2x2 -> WeatherWidgetProvider2x2.Info.getInstance()
            WidgetType.Widget4x1 -> WeatherWidgetProvider4x1.Info.getInstance()
            WidgetType.Widget4x2 -> WeatherWidgetProvider4x2.Info.getInstance()
            WidgetType.Widget4x1Google -> WeatherWidgetProvider4x1Google.Info.getInstance()
            WidgetType.Widget4x1Notification -> WeatherWidgetProvider4x1Notification.Info.getInstance()
            WidgetType.Widget4x2Clock -> WeatherWidgetProvider4x2Clock.Info.getInstance()
            WidgetType.Widget4x2Huawei -> WeatherWidgetProvider4x2Huawei.Info.getInstance()
        }
    }

    fun addWidgetId(location_query: String, widgetId: Int) {
        val listJson = widgetPrefs.getString(location_query, "")
        if (listJson.isNullOrBlank()) {
            val newlist = listOf(widgetId)
            saveIds(location_query, newlist)
        } else {
            val intArrListType = object : TypeToken<ArrayList<Int>>() {}.type
            val idList = JSONParser.deserializer<ArrayList<Int>>(listJson, intArrListType)
            if (idList != null && !idList.contains(widgetId)) {
                idList.add(widgetId)
                saveIds(location_query, idList)
            }
        }

        cleanupWidgetData()
        cleanupWidgetIds()
    }

    fun removeWidgetId(location_query: String, widgetId: Int) {
        val listJson = widgetPrefs.getString(location_query, "")
        if (!listJson.isNullOrBlank()) {
            val intArrListType = object : TypeToken<ArrayList<Int>>() {}.type
            val idList = JSONParser.deserializer<ArrayList<Int>>(listJson, intArrListType)
            if (idList?.contains(widgetId) == true) {
                idList.remove(Integer.valueOf(widgetId))
                if (idList.size == 0) {
                    widgetPrefs.edit(true) {
                        remove(location_query)
                    }
                } else {
                    saveIds(location_query, idList)
                }
            }
        }
        deletePreferences(widgetId)
    }

    private fun deletePreferences(widgetId: Int) {
        getPreferences(widgetId).edit(true) {
            clear()
        }

        val context = App.instance.appContext

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.deleteSharedPreferences(String.format(Locale.ROOT, "appwidget_%d", widgetId))
        } else {
            val parentPath = context.filesDir.parent
            val sharedPrefsPath =
                String.format(Locale.ROOT, "%s/shared_prefs/appwidget_%d.xml", parentPath, widgetId)
            val sharedPrefsFile = File(sharedPrefsPath)

            if (sharedPrefsFile.exists() &&
                sharedPrefsFile.canWrite() && sharedPrefsFile.parentFile?.canWrite() == true
            ) {
                sharedPrefsFile.delete()
            }
        }
    }

    fun updateWidgetIds(oldQuery: String?, newLocation: LocationData) {
        val listJson = widgetPrefs.getString(oldQuery, "")
        widgetPrefs.edit(true) {
            remove(oldQuery)
            putString(newLocation.query, listJson)
        }

        for (id in getWidgetIds(newLocation.query)) {
            saveLocationData(id, newLocation)
        }

        cleanupWidgetData()
        cleanupWidgetIds()
    }

    fun getWidgetIds(location_query: String?): List<Int> {
        val listJson = widgetPrefs.getString(location_query, "")
        if (!listJson.isNullOrBlank()) {
            val intArrListType = object : TypeToken<ArrayList<Int>>() {}.type
            val idList = JSONParser.deserializer<ArrayList<Int>>(listJson, intArrListType)
            if (idList != null) {
                return idList
            }
        }

        return emptyList()
    }

    fun exists(location_query: String?): Boolean {
        val listJson = widgetPrefs.getString(location_query, "")
        if (!listJson.isNullOrBlank()) {
            val intArrListType = object : TypeToken<ArrayList<Int>>() {}.type
            val idList = JSONParser.deserializer<ArrayList<Int>>(listJson, intArrListType)
            if (!idList.isNullOrEmpty()) {
                return true
            }
        }
        return false
    }

    fun exists(appWidgetId: Int): Boolean {
        val locData = getLocationData(appWidgetId)
        if (locData != null) {
            val listJson = widgetPrefs.getString(locData.query, "")
            if (!listJson.isNullOrBlank()) {
                val intArrListType = object : TypeToken<ArrayList<Int>>() {}.type
                val idList = JSONParser.deserializer<ArrayList<Int>>(listJson, intArrListType)
                if (idList != null) {
                    return idList.contains(appWidgetId)
                }
            }
        }

        return false
    }

    private fun saveIds(key: String, idList: List<Int>): Boolean {
        val json = JSONParser.serializer(idList, ArrayList::class.java)
        return widgetPrefs.edit().putString(key, json).commit()
    }

    private fun getPreferences(appWidgetId: Int): SharedPreferences {
        return App.instance.appContext.getSharedPreferences(
            String.format(
                Locale.ROOT,
                "appwidget_%d",
                appWidgetId
            ), Context.MODE_PRIVATE
        )
    }

    fun saveLocationData(appWidgetId: Int, location: LocationData?) {
        getPreferences(appWidgetId).edit(true) {
            val locJson = JSONParser.serializer(location, LocationData::class.java)
            if (locJson != null) putString(KEY_LOCATIONDATA, locJson)
        }
    }

    fun getLocationData(appWidgetId: Int): LocationData? {
        val prefs = getPreferences(appWidgetId)
        val locDataJson = prefs.getString(KEY_LOCATIONDATA, null)

        return locDataJson?.let {
            JSONParser.deserializer(it, LocationData::class.java)
        }
    }

    fun cleanupWidgetIds() {
        GlobalScope.launch(Dispatchers.Default) {
            val locs = ArrayList(settingsMgr.getLocationData())
            settingsMgr.getLastGPSLocData()?.let {
                locs.add(it)
            }
            val currLocQueries = ArrayList<String>(locs.size)
            for (loc in locs) {
                currLocQueries.add(loc!!.query)
            }
            val widgetMap = widgetPrefs.all
            widgetPrefs.edit(true) {
                for (key in widgetMap.keys) {
                    if (KEY_VERSION != key && Constants.KEY_GPS != key && !currLocQueries.contains(
                            key
                        )
                    ) {
                        remove(key)
                    }
                }
            }
        }
    }

    fun cleanupWidgetData() {
        GlobalScope.launch(Dispatchers.IO) {
            val currentIds = getAllWidgetIds()

            val context = App.instance.appContext
            val parentPath = context.filesDir.parent
            val sharedPrefsPath = String.format(Locale.ROOT, "%s/shared_prefs", parentPath)
            val sharedPrefsFolder = File(sharedPrefsPath)
            val appWidgetFiles = sharedPrefsFolder.listFiles { dir, name ->
                val lowerCaseName = name.toLowerCase(Locale.ROOT)
                lowerCaseName.startsWith("appwidget_") && lowerCaseName.endsWith(".xml")
            } ?: emptyArray()

            for (file in appWidgetFiles) {
                val fileName = file.name
                var idString = ""
                if (!fileName.isNullOrBlank()) {
                    idString = fileName.replace("appwidget_", "")
                        .replace(".xml", "")

                    try {
                        val id = Integer.valueOf(idString)

                        if (!currentIds.contains(id) &&
                            file.exists() && file.canWrite() && file.parentFile?.canWrite() == true
                        ) {
                            file.delete()
                        }
                    } catch (ex: Exception) {
                        Logger.writeLine(Log.ERROR, ex)
                    }
                }
            }
        }
    }

    fun isGPS(widgetId: Int): Boolean {
        val listJson = widgetPrefs.getString(Constants.KEY_GPS, "")
        if (!listJson.isNullOrBlank()) {
            val intArrListType = object : TypeToken<ArrayList<Int>>() {}.type
            val idList = JSONParser.deserializer<ArrayList<Int>>(listJson, intArrListType)
            if (!idList.isNullOrEmpty()) {
                return idList.contains(widgetId)
            }
        }

        return false
    }

    fun deleteWidget(id: Int) {
        if (isGPS(id)) {
            removeWidgetId(Constants.KEY_GPS, id)
        } else {
            val locData = getLocationData(id)
            if (locData != null) {
                removeWidgetId(locData.query, id)
            }
        }
    }

    fun remapWidget(oldId: Int, newId: Int) {
        if (isGPS(oldId)) {
            removeWidgetId(Constants.KEY_GPS, oldId)
            addWidgetId(Constants.KEY_GPS, newId)
        } else {
            val locData = getLocationData(oldId)
            if (locData != null) {
                removeWidgetId(locData.query, oldId)
                addWidgetId(locData.query, newId)
                saveLocationData(newId, locData)
            }
        }
    }

    /**
     * Returns number of cells needed for given size of the widget.
     *
     * @param size Widget size in dp.
     * @return Size in number of cells.
     */
    fun getCellsForSize(size: Int): Int {
        // The hardwired sizes in this function come from the hardwired formula found in
        // Android's UI guidelines for widget design:
        // http://developer.android.com/guide/practices/ui_guidelines/widget_design.html
        return (size + 30) / 70
    }

    fun getForecastLength(widgetType: WidgetType, cellWidth: Int): Int {
        if (widgetType != WidgetType.Widget4x1 && widgetType != WidgetType.Widget4x2) return 0

        return when {
            cellWidth >= 5 -> {
                WIDE_FORECAST_LENGTH + 1
            }
            cellWidth < 2 -> {
                1
            }
            else -> {
                cellWidth + 1
            }
        }
    }

    fun getWidgetTypeFromID(appWidgetId: Int): WidgetType {
        val providerInfo =
            AppWidgetManager.getInstance(App.instance.appContext).getAppWidgetInfo(appWidgetId)

        if (providerInfo != null) {
            when (providerInfo.initialLayout) {
                WeatherWidgetProvider1x1.Info.getInstance().widgetLayoutId -> {
                    return WidgetType.Widget1x1
                }
                WeatherWidgetProvider2x2.Info.getInstance().widgetLayoutId -> {
                    return WidgetType.Widget2x2
                }
                WeatherWidgetProvider4x1.Info.getInstance().widgetLayoutId -> {
                    return WidgetType.Widget4x1
                }
                WeatherWidgetProvider4x2.Info.getInstance().widgetLayoutId -> {
                    return WidgetType.Widget4x2
                }
                WeatherWidgetProvider4x1Google.Info.getInstance().widgetLayoutId -> {
                    return WidgetType.Widget4x1Google
                }
                WeatherWidgetProvider4x1Notification.Info.getInstance().widgetLayoutId -> {
                    return WidgetType.Widget4x1Notification
                }
                WeatherWidgetProvider4x2Clock.Info.getInstance().widgetLayoutId -> {
                    return WidgetType.Widget4x2Clock
                }
                WeatherWidgetProvider4x2Huawei.Info.getInstance().widgetLayoutId -> {
                    return WidgetType.Widget4x2Huawei
                }
            }
        }

        return WidgetType.Unknown
    }

    fun getWidgetBackground(widgetId: Int): WidgetBackground {
        val prefs = getPreferences(widgetId)

        var value = prefs.getString(KEY_WIDGETBACKGROUND, "1")
        if (value.isNullOrBlank()) value = "1"

        return WidgetBackground.valueOf(value.toInt())
    }

    fun setWidgetBackground(widgetId: Int, value: Int) {
        getPreferences(widgetId).edit(true) {
            putString(KEY_WIDGETBACKGROUND, value.toString())
        }
    }

    fun getBackgroundStyle(widgetId: Int): WidgetBackgroundStyle {
        val prefs = getPreferences(widgetId)

        var value = prefs.getString(KEY_WIDGETBACKGROUNDSTYLE, "1")
        if (value.isNullOrBlank()) value = "1"

        return WidgetBackgroundStyle.valueOf(value.toInt())
    }

    fun setBackgroundStyle(widgetId: Int, value: Int) {
        getPreferences(widgetId).edit(true) {
            putString(KEY_WIDGETBACKGROUNDSTYLE, value.toString())
        }
    }

    fun isClockWidget(widgetType: WidgetType): Boolean {
        return widgetType == WidgetType.Widget2x2 || widgetType == WidgetType.Widget4x2 || widgetType == WidgetType.Widget4x2Clock || widgetType == WidgetType.Widget4x2Huawei
    }

    fun isDateWidget(widgetType: WidgetType): Boolean {
        return widgetType == WidgetType.Widget2x2 || widgetType == WidgetType.Widget4x2 || widgetType == WidgetType.Widget4x1Google || widgetType == WidgetType.Widget4x2Clock || widgetType == WidgetType.Widget4x2Huawei
    }

    fun isForecastWidget(widgetType: WidgetType): Boolean {
        return widgetType == WidgetType.Widget4x1 || widgetType == WidgetType.Widget4x2
    }

    fun isBackgroundOptionalWidget(widgetType: WidgetType): Boolean {
        return widgetType == WidgetType.Widget2x2 || widgetType == WidgetType.Widget4x2
    }

    fun isLocationNameOptionalWidget(widgetType: WidgetType): Boolean {
        return widgetType == WidgetType.Widget1x1 || widgetType == WidgetType.Widget4x1 || widgetType == WidgetType.Widget4x1Google || widgetType == WidgetType.Widget4x2Clock
    }

    @ColorInt
    fun getTextColor(appWidgetId: Int, background: WidgetBackground): Int {
        return if (background == WidgetBackground.CUSTOM) {
            getTextColor(appWidgetId)
        } else {
            Colors.WHITE
        }
    }

    @ColorInt
    fun getPanelTextColor(
        appWidgetId: Int,
        background: WidgetBackground,
        style: WidgetBackgroundStyle?,
        isNightMode: Boolean
    ): Int {
        return if (background == WidgetBackground.CUSTOM) {
            getTextColor(appWidgetId)
        } else if (style == WidgetBackgroundStyle.DARK) {
            Colors.WHITE
        } else if (style == WidgetBackgroundStyle.LIGHT) {
            Colors.BLACK
        } else if (background == WidgetBackground.TRANSPARENT) {
            Colors.WHITE
        } else if (background == WidgetBackground.CURRENT_CONDITIONS) {
            if (style == WidgetBackgroundStyle.PANDA) {
                if (isNightMode) Colors.WHITE else Colors.BLACK
            } else {
                Colors.WHITE
            }
        } else {
            Colors.WHITE
        }
    }

    @ColorInt
    fun getBackgroundColor(appWidgetId: Int, background: WidgetBackground): Int {
        return if (background == WidgetBackground.CUSTOM) {
            getBackgroundColor(appWidgetId)
        } else {
            Colors.TRANSPARENT
        }
    }

    @ColorInt
    fun getBackgroundColor(widgetId: Int): Int {
        val prefs = getPreferences(widgetId)
        return prefs.getInt(KEY_BGCOLORCODE, Color.TRANSPARENT)
    }

    fun setBackgroundColor(widgetId: Int, @ColorInt value: Int) {
        getPreferences(widgetId).edit(true) {
            putInt(KEY_BGCOLORCODE, value)
        }
    }

    @ColorInt
    fun getTextColor(widgetId: Int): Int {
        val prefs = getPreferences(widgetId)
        return prefs.getInt(KEY_TXTCOLORCODE, Color.WHITE)
    }

    fun setTextColor(widgetId: Int, @ColorInt value: Int) {
        getPreferences(widgetId).edit(true) {
            putInt(KEY_TXTCOLORCODE, value)
        }
    }

    fun isLocationNameHidden(widgetId: Int): Boolean {
        val prefs = getPreferences(widgetId)
        return prefs.getBoolean(KEY_HIDELOCATIONNAME, false)
    }

    fun setLocationNameHidden(widgetId: Int, value: Boolean) {
        getPreferences(widgetId).edit(true) {
            putBoolean(KEY_HIDELOCATIONNAME, value)
        }
    }

    fun isSettingsButtonHidden(widgetId: Int): Boolean {
        val prefs = getPreferences(widgetId)
        return prefs.getBoolean(KEY_HIDESETTINGSBUTTON, false)
    }

    fun setSettingsButtonHidden(widgetId: Int, value: Boolean) {
        getPreferences(widgetId).edit(true) {
            putBoolean(KEY_HIDESETTINGSBUTTON, value)
        }
    }

    fun getOnClickClockApp(): String? {
        val prefs = App.instance.preferences
        return prefs.getString(KEY_CLOCKAPP, null)
    }

    fun setOnClickClockApp(value: String?) {
        App.instance.preferences.edit(true) {
            putString(KEY_CLOCKAPP, value)
        }
    }

    fun getOnClickCalendarApp(): String? {
        val prefs = App.instance.preferences
        return prefs.getString(KEY_CALENDARAPP, null)
    }

    fun setOnClickCalendarApp(value: String?) {
        App.instance.preferences.edit(true) {
            putString(KEY_CALENDARAPP, value)
        }
    }

    fun getClockAppComponent(context: Context): ComponentName? {
        val key = getOnClickClockApp()

        if (key != null) {
            val data = key.split("/").toTypedArray()
            if (data.size == 2) {
                val pkgName = data[0]
                val activityName = data[1]

                if (pkgName.isNotBlank() && activityName.isNotBlank()) {
                    val componentName = ComponentName(pkgName, activityName)
                    if (ContextUtils.verifyActivityInfo(context, componentName)) {
                        return componentName
                    }
                }
            }

            // App not available
            setOnClickClockApp(null)
        }

        return null
    }

    fun getCalendarAppComponent(context: Context): ComponentName? {
        val key = getOnClickCalendarApp()

        if (key != null) {
            val data = key.split("/").toTypedArray()
            if (data.size == 2) {
                val pkgName = data[0]
                val activityName = data[1]

                if (pkgName.isNotBlank() && activityName.isNotBlank()) {
                    val componentName = ComponentName(pkgName, activityName)
                    if (ContextUtils.verifyActivityInfo(context, componentName)) {
                        return componentName
                    }
                }
            }

            // App not available
            setOnClickCalendarApp(null)
        }

        return null
    }

    fun getForecastOption(widgetId: Int): ForecastOption {
        val prefs = getPreferences(widgetId)

        var value = prefs.getString(KEY_FORECASTOPTION, "0")
        if (value.isNullOrBlank()) value = "0"

        return ForecastOption.valueOf(value.toInt())
    }

    fun setForecastOption(widgetId: Int, value: Int) {
        getPreferences(widgetId).edit(true) {
            putString(KEY_FORECASTOPTION, value.toString())
        }
    }

    fun isTap2Switch(widgetId: Int): Boolean {
        val prefs = getPreferences(widgetId)
        return prefs.getBoolean(KEY_TAP2SWITCH, true)
    }

    fun setTap2Switch(widgetId: Int, value: Boolean) {
        getPreferences(widgetId).edit(true) {
            putBoolean(KEY_TAP2SWITCH, value)
        }
    }

    fun useTimeZone(widgetId: Int): Boolean {
        val prefs = getPreferences(widgetId)
        return prefs.getBoolean(KEY_USETIMEZONE, false)
    }

    fun setUseTimeZone(widgetId: Int, value: Boolean) {
        getPreferences(widgetId).edit(true) {
            putBoolean(KEY_USETIMEZONE, value)
        }
    }
}