package `in`.melo.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

/** Controls Firebase Analytics and Melo's aggregate public-stats telemetry with one privacy setting. */
object UsageAnalytics {
    fun setEnabled(context: Context, enabled: Boolean) {
        val application = context.applicationContext
        if (FirebaseApp.getApps(application).any { it.name == FirebaseApp.DEFAULT_APP_NAME }) {
            FirebaseAnalytics.getInstance(application).setAnalyticsCollectionEnabled(enabled)
        }
        MeloTelemetry.setEnabled(enabled)
    }
}
