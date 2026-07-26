package ch.cclerc.luxapp.data

import ch.cclerc.luxcom.luxtrip.ItinerarySharer
import ch.cclerc.luxcom.luxtrip.LuxTripCodec
import ch.cclerc.luxcom.model.trip.Itinerary
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

data class SavedItineraryRecord(
    val id: String,
    val file: File,
    val itinerary: Itinerary
)

sealed class SavedItineraryStorageError(val description: String) : Exception(description) {
    object StorageUnavailable : SavedItineraryStorageError(
        "Impossible d'accéder au stockage local."
    )

    class SaveFailed(message: String) : SavedItineraryStorageError(
        "Impossible d'enregistrer l'itinéraire localement : $message"
    )
}

object SavedItineraryStorage {
    private const val expirationBufferSeconds = 5L * 60L
    private const val lookAheadWindowSeconds = 60L * 60L
    private const val fileExtension = "luxtrip"

    private val storageLock = Any()
    private val cacheLock = Any()
    private var cachedDirectoryFingerprint: Int? = null
    private var cachedRecords: List<SavedItineraryRecord> = emptyList()

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    private val storageDirectory: File?
        get() = runCatching { AppDirectories.dir("SavedItineraries") }
            .getOrNull()
            ?.takeIf { it.isDirectory }

    fun save(itinerary: Itinerary): Result<Unit> = synchronized(storageLock) {
        val directory = storageDirectory ?: return Result.failure(SavedItineraryStorageError.StorageUnavailable)

        cleanupExpiredItineraries(Instant.now())
        removeDuplicateEntries(itinerary)

        return try {
            val encoded = LuxTripCodec.encode(itinerary)
            val filename = "${itinerary.startTime.epochSecond}-${UUID.randomUUID()}.$fileExtension"
            val target = File(directory, filename)
            val temporary = File(directory, "$filename.tmp")
            temporary.writeBytes(encoded)
            if (!temporary.renameTo(target)) {
                target.writeBytes(encoded)
                temporary.delete()
            }
            invalidateCache()
            _changes.tryEmit(Unit)
            Result.success(Unit)
        } catch (error: Throwable) {
            Result.failure(SavedItineraryStorageError.SaveFailed(error.message ?: error.toString()))
        }
    }

    fun isSaved(itinerary: Itinerary, referenceDate: Instant = Instant.now()): Boolean =
        synchronized(storageLock) {
            loadValidRecords(
                referenceDate = referenceDate,
                cleanupExpiredFiles = false,
                notifyOnCleanup = false
            ).any { itinerariesMatch(it.itinerary, itinerary) }
        }

    suspend fun isSavedAsync(itinerary: Itinerary, referenceDate: Instant = Instant.now()): Boolean =
        withContext(Dispatchers.IO) { isSaved(itinerary, referenceDate) }

    fun loadUpcomingItinerary(referenceDate: Instant = Instant.now()): SavedItineraryRecord? =
        synchronized(storageLock) {
            val records = loadValidRecords(
                referenceDate = referenceDate,
                cleanupExpiredFiles = true,
                notifyOnCleanup = true
            )

            val candidates = records.filter { isWithinDisplayWindow(it.itinerary, referenceDate) }

            val upcoming = candidates
                .filter { !it.itinerary.startTime.isBefore(referenceDate) }
                .minByOrNull { it.itinerary.startTime }

            upcoming ?: candidates.minByOrNull { it.itinerary.endTime }
        }

    suspend fun loadUpcomingItineraryAsync(referenceDate: Instant = Instant.now()): SavedItineraryRecord? =
        withContext(Dispatchers.IO) { loadUpcomingItinerary(referenceDate) }

    fun loadItinerary(file: File): Itinerary? = synchronized(storageLock) {
        runCatching {
            val itinerary = LuxTripCodec.decode(file.readBytes())
            if (!ItinerarySharer.validateItinerary(itinerary)) null else itinerary
        }.getOrNull()
    }

    fun cleanupExpiredItineraries(referenceDate: Instant = Instant.now()): Int =
        synchronized(storageLock) {
            removeExpiredRecords(loadAllItineraries(), referenceDate, notify = true).second
        }

    private fun loadAllItineraries(): List<SavedItineraryRecord> {
        val directory = storageDirectory ?: return emptyList()

        val files = directory.listFiles()
            ?.filter { !it.isHidden && it.extension == fileExtension }
            ?: return emptyList()

        val fingerprint = directoryFingerprint(files)
        cachedRecordsIfAvailable(fingerprint)?.let { return it }

        val records = mutableListOf<SavedItineraryRecord>()
        var removedInvalidFile = false

        for (file in files) {
            val itinerary = runCatching { LuxTripCodec.decode(file.readBytes()) }.getOrNull()
            if (itinerary == null || !ItinerarySharer.validateItinerary(itinerary)) {
                if (file.delete()) removedInvalidFile = true
                continue
            }
            records.add(SavedItineraryRecord(id = file.name, file = file, itinerary = itinerary))
        }

        updateCache(records, directoryFingerprint(records.map { it.file }))

        if (removedInvalidFile) {
            _changes.tryEmit(Unit)
        }

        return records
    }

    private fun isWithinDisplayWindow(itinerary: Itinerary, date: Instant): Boolean {
        val showFrom = itinerary.startTime.minusSeconds(lookAheadWindowSeconds)
        val showUntil = itinerary.endTime.plusSeconds(expirationBufferSeconds)
        return !date.isBefore(showFrom) && !date.isAfter(showUntil)
    }

    private fun removeDuplicateEntries(itinerary: Itinerary) {
        val duplicates = loadAllItineraries().filter { itinerariesMatch(it.itinerary, itinerary) }
        if (duplicates.isEmpty()) return

        var removedAny = false
        for (duplicate in duplicates) {
            if (duplicate.file.delete()) removedAny = true
        }

        if (removedAny) invalidateCache()
    }

    private fun itinerariesMatch(lhs: Itinerary, rhs: Itinerary): Boolean =
        lhs.startTime == rhs.startTime &&
            lhs.endTime == rhs.endTime &&
            lhs.legs.firstOrNull()?.from?.name == rhs.legs.firstOrNull()?.from?.name &&
            lhs.legs.lastOrNull()?.to?.name == rhs.legs.lastOrNull()?.to?.name

    private fun loadValidRecords(
        referenceDate: Instant,
        cleanupExpiredFiles: Boolean,
        notifyOnCleanup: Boolean
    ): List<SavedItineraryRecord> {
        val records = loadAllItineraries()

        if (cleanupExpiredFiles) {
            return removeExpiredRecords(records, referenceDate, notifyOnCleanup).first
        }

        return records.filter { !itineraryIsExpired(it.itinerary, referenceDate) }
    }

    private fun removeExpiredRecords(
        records: List<SavedItineraryRecord>,
        referenceDate: Instant,
        notify: Boolean
    ): Pair<List<SavedItineraryRecord>, Int> {
        val expired = records.filter { itineraryIsExpired(it.itinerary, referenceDate) }
        if (expired.isEmpty()) return records to 0

        var removed = 0
        val expiredIds = expired.map { it.id }.toSet()

        for (record in expired) {
            if (record.file.delete()) removed += 1
        }

        val remaining = records.filter { it.id !in expiredIds }
        updateCache(remaining, directoryFingerprint(remaining.map { it.file }))

        if (removed > 0 && notify) {
            _changes.tryEmit(Unit)
        }

        return remaining to removed
    }

    private fun itineraryIsExpired(itinerary: Itinerary, date: Instant): Boolean =
        date.isAfter(itinerary.endTime.plusSeconds(expirationBufferSeconds))

    private fun directoryFingerprint(files: List<File>): Int {
        var result = 1
        for (file in files.sortedBy { it.name }) {
            result = 31 * result + file.name.hashCode()
            result = 31 * result + file.length().hashCode()
            result = 31 * result + file.lastModified().hashCode()
        }
        return result
    }

    private fun cachedRecordsIfAvailable(fingerprint: Int): List<SavedItineraryRecord>? =
        synchronized(cacheLock) {
            if (cachedDirectoryFingerprint != fingerprint) null else cachedRecords
        }

    private fun updateCache(records: List<SavedItineraryRecord>, fingerprint: Int) {
        synchronized(cacheLock) {
            cachedRecords = records
            cachedDirectoryFingerprint = fingerprint
        }
    }

    private fun invalidateCache() {
        synchronized(cacheLock) {
            cachedRecords = emptyList()
            cachedDirectoryFingerprint = null
        }
    }
}
