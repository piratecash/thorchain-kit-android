package io.horizontalsystems.thorchainkit.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class TransactionSyncState(
    val lastTimestamp: Long,
    // Midgard pagination token to resume fetching history OLDER than what is stored —
    // set when a sync round hits its page cap before connecting to already-synced
    // history, so old history is backfilled instead of silently skipped
    val backfillPageToken: String? = null,
    @PrimaryKey val id: String = ""
)
