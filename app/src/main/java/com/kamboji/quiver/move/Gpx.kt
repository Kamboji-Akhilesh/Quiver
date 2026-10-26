package com.kamboji.quiver.move

import java.time.Instant

/**
 * GPX 1.1 export — the format Strava, Google Earth and every map tool import.
 * Uploading Move's GPX to Strava is also the fairest accuracy check: Strava
 * recomputes distance/pace from exactly the points Move recorded.
 */
object Gpx {
    fun write(name: String, type: MoveType, points: List<Fix>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="Quiver" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        points.firstOrNull()?.let { append("  <metadata><time>").append(Instant.ofEpochMilli(it.t)).append("</time></metadata>\n") }
        append("  <trk>\n")
        append("    <name>").append(escape(name)).append("</name>\n")
        append("    <type>").append(type.gpxType).append("</type>\n")
        append("    <trkseg>\n")
        for (p in points) {
            append("      <trkpt lat=\"").append(p.lat).append("\" lon=\"").append(p.lon).append("\">")
            p.altM?.let { append("<ele>").append("%.1f".format(java.util.Locale.ROOT, it)).append("</ele>") }
            append("<time>").append(Instant.ofEpochMilli(p.t)).append("</time></trkpt>\n")
        }
        append("    </trkseg>\n  </trk>\n</gpx>\n")
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
