package me.matsumo.pholio.media

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import javax.imageio.ImageIO
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.matsumo.pholio.config.AppConfig
import me.matsumo.pholio.photos.GpsSource
import me.matsumo.pholio.photos.MediaType
import me.matsumo.pholio.photos.TakenAtSource
import me.matsumo.pholio.time.TimeFormats

/**
 * 写真・動画の metadata を抽出する。
 */
class MediaMetadataExtractor(
    private val config: AppConfig,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) {
    private val defaultZoneId = ZoneId.of(config.defaultTimezone)

    /**
     * media file と sidecar から metadata を抽出する。
     */
    fun extract(path: Path, mediaType: MediaType, sidecar: TakeoutSidecar?): ExtractedMetadata {
        val fileMtimeEpochMs = Files.getLastModifiedTime(path).toMillis()
        val base = if (mediaType == MediaType.Video) {
            extractVideo(path)
        } else {
            extractImage(path)
        }
        val takenAtCandidates = listOfNotNull(
            sidecar?.takenAtEpochMs?.let { TakenAtCandidate(it, TakenAtSource.TakeoutJson.dbValue) },
            base.exifTakenAtEpochMs?.let { TakenAtCandidate(it, TakenAtSource.Exif.dbValue) },
            base.videoCreatedAtEpochMs?.let { TakenAtCandidate(it, TakenAtSource.VideoMetadata.dbValue) },
            TakenAtCandidate(fileMtimeEpochMs, TakenAtSource.FileMtime.dbValue),
        )
        val selectedTakenAt = takenAtCandidates.first()
        val gps = sidecar?.gps ?: base.gps

        return base.copy(
            takenAtEpochMs = selectedTakenAt.epochMs,
            takenAtSource = selectedTakenAt.source,
            takeoutTakenAtEpochMs = sidecar?.takenAtEpochMs,
            gps = gps,
        )
    }

    /**
     * Takeout sidecar JSON を parse する。
     */
    fun readSidecar(path: Path, relativePath: String): TakeoutSidecar? {
        val jsonObject = runCatching {
            json.parseToJsonElement(Files.readString(path)).jsonObject
        }.getOrNull() ?: return null
        val takenAtEpochMs = jsonObject["photoTakenTime"]
            ?.jsonObject
            ?.get("timestamp")
            ?.jsonPrimitive
            ?.content
            ?.toLongOrNull()
            ?.times(1000)
        val gps = parseGps(jsonObject["geoData"]?.jsonObject, GpsSource.TakeoutGeoData)
            ?: parseGps(jsonObject["geoDataExif"]?.jsonObject, GpsSource.TakeoutGeoDataExif)
        val title = jsonObject["title"]?.jsonPrimitive?.content

        return TakeoutSidecar(
            relativePath = relativePath,
            title = title,
            takenAtEpochMs = takenAtEpochMs,
            gps = gps,
        )
    }

    private fun extractImage(path: Path): ExtractedMetadata {
        val bufferedImage = runCatching {
            ImageIO.read(path.toFile())
        }.getOrNull()
        val metadata = runCatching {
            ImageMetadataReader.readMetadata(path.toFile())
        }.getOrNull()
        val ifd0 = metadata?.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
        val subIfd = metadata?.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
        val gpsDirectory = metadata?.getFirstDirectoryOfType(GpsDirectory::class.java)
        val orientation = ifd0?.getInteger(ExifIFD0Directory.TAG_ORIENTATION)
        val rawExifDate = subIfd?.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
            ?: subIfd?.getString(ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED)
        val offset: String? = null
        val exifTakenAt = parseExifDate(rawExifDate, offset)
        val gps = gpsDirectory?.geoLocation
            ?.takeIf { !it.isZero }
            ?.let {
                ExtractedGps(
                    lat = it.latitude,
                    lng = it.longitude,
                    alt = gpsDirectory.getDoubleObject(GpsDirectory.TAG_ALTITUDE),
                    source = GpsSource.Exif.dbValue,
                )
            }

        return ExtractedMetadata(
            width = displayWidth(bufferedImage?.width, bufferedImage?.height, orientation),
            height = displayHeight(bufferedImage?.width, bufferedImage?.height, orientation),
            durationMs = null,
            orientation = orientation,
            exifTakenAtEpochMs = exifTakenAt,
            videoCreatedAtEpochMs = null,
            takeoutTakenAtEpochMs = null,
            takenAtEpochMs = null,
            takenAtSource = TakenAtSource.FileMtime.dbValue,
            gps = gps,
            cameraMake = ifd0?.getString(ExifIFD0Directory.TAG_MAKE),
            cameraModel = ifd0?.getString(ExifIFD0Directory.TAG_MODEL),
        )
    }

    private fun extractVideo(path: Path): ExtractedMetadata {
        val ffprobeOutput = runCatching {
            ProcessBuilder(
                config.ffprobePath,
                "-v",
                "error",
                "-print_format",
                "json",
                "-show_format",
                "-show_streams",
                path.toString(),
            ).redirectErrorStream(true).start().let { process ->
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                if (exitCode == 0) output else null
            }
        }.getOrNull()
        val root = ffprobeOutput?.let {
            runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull()
        }
        val videoStream = root?.get("streams")
            ?.let { element -> element as? kotlinx.serialization.json.JsonArray }
            ?.firstOrNull { stream ->
                stream.jsonObject["codec_type"]?.jsonPrimitive?.content == "video"
            }
            ?.jsonObject
        val format = root?.get("format")?.jsonObject
        val width = videoStream?.get("width")?.jsonPrimitive?.content?.toIntOrNull()
        val height = videoStream?.get("height")?.jsonPrimitive?.content?.toIntOrNull()
        val durationMs = format?.get("duration")?.jsonPrimitive?.content?.toDoubleOrNull()?.times(1000)?.toLong()
        val creationTime = format?.get("tags")
            ?.jsonObject
            ?.get("creation_time")
            ?.jsonPrimitive
            ?.content
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

        return ExtractedMetadata(
            width = width,
            height = height,
            durationMs = durationMs,
            orientation = null,
            exifTakenAtEpochMs = null,
            videoCreatedAtEpochMs = creationTime,
            takeoutTakenAtEpochMs = null,
            takenAtEpochMs = null,
            takenAtSource = TakenAtSource.FileMtime.dbValue,
            gps = null,
            cameraMake = null,
            cameraModel = null,
        )
    }

    private fun parseExifDate(rawExifDate: String?, offset: String?): Long? {
        if (rawExifDate == null) {
            return null
        }

        val isoText = if (offset != null) {
            rawExifDate.replaceFirst(':', '-')
                .replaceFirst(':', '-')
                .replace(' ', 'T') + offset
        } else {
            null
        }

        return isoText?.let {
            runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
        } ?: TimeFormats.fromLocalDateTime(rawExifDate, defaultZoneId)
    }

    private fun parseGps(jsonObject: JsonObject?, source: GpsSource): ExtractedGps? {
        if (jsonObject == null) {
            return null
        }

        val lat = jsonObject["latitude"]?.jsonPrimitive?.doubleOrNull ?: return null
        val lng = jsonObject["longitude"]?.jsonPrimitive?.doubleOrNull ?: return null
        val isZeroCoordinate = lat == 0.0 && lng == 0.0

        if (isZeroCoordinate) {
            return null
        }

        return ExtractedGps(
            lat = lat,
            lng = lng,
            alt = jsonObject["altitude"]?.jsonPrimitive?.doubleOrNull,
            source = source.dbValue,
        )
    }

    private fun displayWidth(width: Int?, height: Int?, orientation: Int?): Int? {
        return if (orientation.isRotated()) height else width
    }

    private fun displayHeight(width: Int?, height: Int?, orientation: Int?): Int? {
        return if (orientation.isRotated()) width else height
    }

    private fun Int?.isRotated(): Boolean {
        val isRightAngle = this == 6
        val isLeftAngle = this == 8

        return isRightAngle || isLeftAngle
    }
}

/**
 * 抽出済み media metadata。
 */
data class ExtractedMetadata(
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val orientation: Int?,
    val exifTakenAtEpochMs: Long?,
    val videoCreatedAtEpochMs: Long?,
    val takeoutTakenAtEpochMs: Long?,
    val takenAtEpochMs: Long?,
    val takenAtSource: String,
    val gps: ExtractedGps?,
    val cameraMake: String?,
    val cameraModel: String?,
)

/**
 * 抽出済み GPS 情報。
 */
data class ExtractedGps(
    val lat: Double,
    val lng: Double,
    val alt: Double?,
    val source: String,
)

/**
 * Google Takeout sidecar の抽出結果。
 */
data class TakeoutSidecar(
    val relativePath: String,
    val title: String?,
    val takenAtEpochMs: Long?,
    val gps: ExtractedGps?,
)

/**
 * 撮影日時候補。
 */
private data class TakenAtCandidate(
    val epochMs: Long,
    val source: String,
)
