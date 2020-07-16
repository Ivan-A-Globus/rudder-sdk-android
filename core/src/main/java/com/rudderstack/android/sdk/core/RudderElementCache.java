package com.rudderstack.android.sdk.core;

import android.app.Application;

import java.util.Map;
import java.util.UUID;

/*
 * RudderContext is populated once and cached through out the lifecycle
 * */
class RudderElementCache {
    private static RudderContext cachedContext;

    private RudderElementCache() {
        // stop instantiating
    }

    static void initiate(Application application) {
        if (cachedContext == null) {
            RudderLogger.logDebug("RudderElementCache: initiating RudderContext");
            cachedContext = new RudderContext(application);
            if(cachedContext.getAnonymousId() == null) {
                RudderPreferenceManager preferenceManger = RudderPreferenceManager.getInstance(application);
                preferenceManger.saveAnonymousId(UUID.randomUUID().toString());
            }
        }
    }

    static RudderContext getCachedContext() {
        return cachedContext;
    }

    static void reset() {
        cachedContext.updateTraits(null);
        persistTraits();
    }

    static void persistTraits() {
        cachedContext.persistTraits();
    }

    static void updateTraits(Map<String, Object> traits) {
        cachedContext.updateTraitsMap(traits);
    }
}

