package com.rudderstack.android.sdk.core;

import com.google.gson.annotations.SerializedName;

class RudderLibraryInfo {
    @SerializedName("name")
    private String name = "android.custom";
    @SerializedName("version")
    private String version = "1";
}
