package edu.illinois.cs.cs125.jeed.server

import com.squareup.moshi.JsonClass
import edu.illinois.cs.cs125.jeed.core.version as JEED_VERSION
import java.time.Instant

@JsonClass(generateAdapter = true)
class Status(
        val started: Instant = Instant.now(),
        var lastJob: Instant? = null,
        val versions: Versions = Versions(JEED_VERSION, VERSION),
        val counts: Counts = Counts()
) {
    data class Versions(val jeed: String, val server: String)
    data class Counts(var submittedJobs: Int = 0, var completedJobs: Int = 0, var savedJobs: Int = 0)
}
val currentStatus = Status()
