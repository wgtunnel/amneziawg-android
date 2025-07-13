plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.nmcp)
}

nmcpAggregation {
    centralPortal {
        username = getLocalProperty("MAVEN_CENTRAL_USER")
        password = getLocalProperty("MAVEN_CENTRAL_PASS")
        // publish manually from the portal
        publishingType = "USER_MANAGED"
        // or if you want to publish automatically
        publishingType = "AUTOMATIC"
    }

    // Publish all projects that apply the 'maven-publish' plugin
    publishAllProjectsProbablyBreakingProjectIsolation()
}