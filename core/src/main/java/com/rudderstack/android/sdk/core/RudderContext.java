package com.rudderstack.android.sdk.core;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.rudderstack.android.sdk.core.util.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.rudderstack.android.sdk.core.util.Utils.isOnClassPath;

public class RudderContext {
    @SerializedName("app")
    private RudderApp app;
    @SerializedName("traits")
    private Map<String, Object> traits;
    @SerializedName("library")
    private RudderLibraryInfo libraryInfo;
    @SerializedName("os")
    private RudderOSInfo osInfo;
    @SerializedName("screen")
    private RudderScreenInfo screenInfo;
    @SerializedName("userAgent")
    private String userAgent;
    @SerializedName("locale")
    private String locale;
    @SerializedName("device")
    private RudderDeviceInfo deviceInfo;
    @SerializedName("network")
    private RudderNetwork networkInfo;
    @SerializedName("timezone")
    private String timezone;
    @SerializedName("externalId")
    private List<Map<String, Object>> externalIds = null;

    private static transient String _anonymousId;

    private RudderContext() {
        // stop instantiating without application instance.
        // cachedContext is used every time, once initialized
    }

    RudderContext(Application application, String anonymousId, String advertisingId) {
        if (TextUtils.isEmpty(anonymousId)) {
            anonymousId = Utils.getDeviceId(application);
        }
        _anonymousId = anonymousId;

        this.app = new RudderApp(application);

        // get saved traits from prefs. if not present create new one and save
        RudderPreferenceManager preferenceManger = RudderPreferenceManager.getInstance(application);
        String traitsJson = preferenceManger.getTraits();
        RudderLogger.logDebug(String.format(Locale.US, "Traits from persistence storage%s", traitsJson));
        if (traitsJson == null) {
            RudderTraits traits = new RudderTraits(anonymousId);
            this.traits = Utils.convertToMap(new Gson().toJson(traits));
            this.persistTraits();
            RudderLogger.logDebug("New traits has been saved");
        } else {
            this.traits = Utils.convertToMap(traitsJson);
            RudderLogger.logDebug("Using old traits from persistence");
        }

        // get saved external Ids from prefs. if not present set it to null
        String externalIdsJson = preferenceManger.getExternalIds();
        RudderLogger.logDebug(String.format(Locale.US, "ExternalIds from persistence storage%s", externalIdsJson));
        if (externalIdsJson != null) {
            this.externalIds = Utils.convertToList(externalIdsJson);
            RudderLogger.logDebug("Using old externalIds from persistence");
        }

        this.screenInfo = new RudderScreenInfo(application);
        this.userAgent = System.getProperty("http.agent");
        this.deviceInfo = new RudderDeviceInfo(advertisingId);
        this.networkInfo = new RudderNetwork(application);
        this.osInfo = new RudderOSInfo();
        this.libraryInfo = new RudderLibraryInfo();
        this.locale = Locale.getDefault().getLanguage() + "-" + Locale.getDefault().getCountry();
        this.timezone = Utils.getTimeZone();
    }

    void updateTraits(RudderTraits traits) {
        // if traits is null reset the traits to a new one with only anonymousId
        if (traits == null) {
            traits = new RudderTraits();
        }

        // convert the whole traits to map and take care of the extras
        Map<String, Object> traitsMap = Utils.convertToMap(new Gson().toJson(traits));
        if (traits.getExtras() != null) {
            traitsMap.putAll(traits.getExtras());
        }

        // update traits object here
        this.traits = traitsMap;
    }

    void persistTraits() {
        // persist updated traits to sharedPreference
        try {
            if (RudderClient.getApplication() != null) {
                RudderPreferenceManager preferenceManger = RudderPreferenceManager.getInstance(RudderClient.getApplication());
                preferenceManger.saveTraits(new Gson().toJson(this.traits));
            }
        } catch (NullPointerException ex) {
            RudderLogger.logError(ex);
        }
    }

    public Map<String, Object> getTraits() {
        return traits;
    }

    void updateTraitsMap(Map<String, Object> traits) {
        this.traits = traits;
    }

    String getDeviceId() {
        return deviceInfo.getDeviceId();
    }

    // set the push token as passed by the developer
    void putDeviceToken(String token) {
        if (token != null && !token.isEmpty()) {
            this.deviceInfo.setToken(token);
        }
    }

    // set the values provided by the user
    void updateWithAdvertisingId(String advertisingId) {
        if (advertisingId == null || advertisingId.isEmpty()) {
            this.deviceInfo.setAdTrackingEnabled(false);
        } else {
            this.deviceInfo.setAdTrackingEnabled(true);
            this.deviceInfo.setAdvertisingId(advertisingId);
        }
    }

    void updateDeviceWithAdId() {
        if (isOnClassPath("com.google.android.gms.ads.identifier.AdvertisingIdClient")) {
            // This needs to be done each time since the settings may have been updated.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        boolean available = getGooglePlayServicesAdvertisingID();
                        if (!available) {
                            available = getAmazonFireAdvertisingID();
                        }
                        if (!available) {
                            RudderLogger.logDebug("Unable to collect advertising ID from Amazon Fire OS and Google Play Services.");
                        }
                    } catch (Exception e) {
                        RudderLogger.logError("Unable to collect advertising ID from Google Play Services or Amazon Fire OS.");
                    }
                }
            }).start();
        } else {
            RudderLogger.logDebug(
                    "Not collecting advertising ID because "
                            + "com.google.android.gms.ads.identifier.AdvertisingIdClient "
                            + "was not found on the classpath.");
        }
    }

    private boolean getGooglePlayServicesAdvertisingID() throws Exception {
        if (RudderClient.getApplication() == null) {
            return false;
        }

        Object advertisingInfo = Class.forName("com.google.android.gms.ads.identifier.AdvertisingIdClient")
                .getMethod("getAdvertisingIdInfo", Context.class).invoke(null, RudderClient.getApplication());

        if (advertisingInfo == null) {
            return false;
        }

        Boolean isLimitAdTrackingEnabled = (Boolean) advertisingInfo.getClass()
                .getMethod("isLimitAdTrackingEnabled").invoke(advertisingInfo);

        if (isLimitAdTrackingEnabled == null || isLimitAdTrackingEnabled) {
            RudderLogger.logDebug("Not collecting advertising ID because isLimitAdTrackingEnabled (Google Play Services) is true.");
            this.deviceInfo.setAdTrackingEnabled(false);
            return false;
        }

        if (this.deviceInfo.getAdvertisingId() == null || this.deviceInfo.getAdvertisingId().isEmpty()) {
            // set the values if and only if the values are not set
            // if value exists, it must have been set by the developer. don't overwrite
            this.deviceInfo.setAdvertisingId((String) advertisingInfo.getClass().getMethod("getId").invoke(advertisingInfo));
            this.deviceInfo.setAdTrackingEnabled(true);
        }

        return true;
    }

    private boolean getAmazonFireAdvertisingID() throws Exception {
        if (RudderClient.getApplication() == null) {
            return false;
        }

        ContentResolver contentResolver = RudderClient.getApplication().getContentResolver();

        boolean limitAdTracking = Settings.Secure.getInt(contentResolver, "limit_ad_tracking") != 0;

        if (limitAdTracking) {
            RudderLogger.logDebug("Not collecting advertising ID because limit_ad_tracking (Amazon Fire OS) is true.");
            this.deviceInfo.setAdTrackingEnabled(false);
            return false;
        }

        if (this.deviceInfo.getAdvertisingId() == null || this.deviceInfo.getAdvertisingId().isEmpty()) {
            // set the values if and only if the values are not set
            // if value exists, it must have been set by the developer. don't overwrite
            this.deviceInfo.setAdvertisingId(Settings.Secure.getString(contentResolver, "advertising_id"));
            this.deviceInfo.setAdTrackingEnabled(true);
        }

        return true;
    }

    /**
     * Getter method for Advertising ID
     *
     * @return The Advertising ID if available, returns null otherwise.
     */
    @Nullable
    public String getAdvertisingId() {
        if (this.deviceInfo == null) {
            return null;
        }
        return this.deviceInfo.getAdvertisingId();
    }

    /**
     * Getter method for Ad Tracking Status.
     *
     * @return true or false, depending on whether ad tracking is enabled or disabled.
     */
    public boolean isAdTrackingEnabled() {
        if (this.deviceInfo == null) {
            return false;
        }
        return this.deviceInfo.isAdTrackingEnabled();
    }

    /**
     * @return ExternalIds for the current session
     */
    @Nullable
    public List<Map<String, Object>> getExternalIds() {
        return externalIds;
    }

    void updateExternalIds(@Nullable List<Map<String, Object>> externalIds) {
        try {
            RudderPreferenceManager preferenceManger = null;
            Application application = RudderClient.getApplication();
            if (application != null) {
                preferenceManger = RudderPreferenceManager.getInstance(application);
            }

            // update local variable
            this.externalIds = externalIds;

            if (preferenceManger != null) {
                if (externalIds == null) {
                    // clear persistence storage : RESET call
                    preferenceManger.clearExternalIds();
                } else {
                    // update persistence storage
                    preferenceManger.saveExternalIds(new Gson().toJson(this.externalIds));
                }
            }
        } catch (NullPointerException ex) {
            RudderLogger.logError(ex);
        }
    }

    static String getAnonymousId() {
        return _anonymousId;
    }

    RudderContext copy() {
        RudderContext copy = new RudderContext();

        copy.app = this.app;
        if (this.traits != null) {
            copy.traits = new HashMap<>(this.traits);
        }
        copy.libraryInfo = this.libraryInfo;
        copy.osInfo = this.osInfo;
        copy.screenInfo = this.screenInfo;
        copy.userAgent = this.userAgent;
        copy.locale = this.locale;
        copy.deviceInfo = this.deviceInfo;
        copy.networkInfo = this.networkInfo;
        copy.timezone = this.timezone;
        if (this.externalIds != null) {
            copy.externalIds = new ArrayList<>(this.externalIds);
        }

        return copy;
    }

    public Map<String, Object> getMap() {
        Map<String, Object> map = new HashMap<>();

        map.put("app", this.app);
        map.put("traits", this.traits);
        map.put("library", this.libraryInfo);
        map.put("os", this.osInfo);
        map.put("screen", this.screenInfo);
        map.put("userAgent", this.userAgent);
        map.put("locale", this.locale);
        map.put("device", this.deviceInfo);
        map.put("network", this.networkInfo);
        map.put("timezone", this.timezone);
        map.put("externalId", this.externalIds);

        return map;
    }
}
