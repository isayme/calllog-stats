package org.isayme.callstats.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide scope for work that must outlive a stopped service, such as persisting
 * the "monitoring disabled" flag after the user taps stop in the notification.
 */
object AppCoroutineScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
