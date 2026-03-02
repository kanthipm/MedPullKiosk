package com.medpull.kiosk.data.repository

import com.medpull.kiosk.data.models.InventoryItem
import com.medpull.kiosk.data.remote.sheets.GoogleSheetsApiService
import com.medpull.kiosk.utils.Constants
class InventoryRepository(
    private val sheetsApiService: GoogleSheetsApiService
) {
    private var cachedItems: List<InventoryItem>? = null
    private var lastFetchTime: Long = 0L

    suspend fun getInventory(forceRefresh: Boolean = false): Result<List<InventoryItem>> {
        val now = System.currentTimeMillis()
        val cacheValid = cachedItems != null && (now - lastFetchTime) < CACHE_DURATION_MS

        if (!forceRefresh && cacheValid) {
            return Result.success(cachedItems!!)
        }

        return try {
            val response = sheetsApiService.getValues(
                spreadsheetId = Constants.GoogleSheets.SPREADSHEET_ID,
                range = Constants.GoogleSheets.SHEET_RANGE,
                apiKey = Constants.GoogleSheets.API_KEY
            )

            val rows = response.values ?: emptyList()
            // Skip header row
            val items = rows.drop(1).mapNotNull { row -> parseRow(row) }

            cachedItems = items
            lastFetchTime = now
            Result.success(items)
        } catch (e: Exception) {
            // Return stale cache on network failure
            cachedItems?.let { return Result.success(it) }
            Result.failure(e)
        }
    }

    private fun parseRow(row: List<String>): InventoryItem? {
        if (row.size < 6) return null
        return try {
            InventoryItem(
                location = row.getOrElse(0) { "" }.trim(),
                itemType = row.getOrElse(1) { "" }.trim(),
                category = row.getOrElse(2) { "" }.trim(),
                boxLabel = row.getOrElse(3) { "" }.trim(),
                quantity = row.getOrElse(4) { "0" }.trim().toIntOrNull() ?: 0,
                threshold = row.getOrElse(5) { "0" }.trim().toIntOrNull() ?: 0,
                expirationDates = row.getOrElse(6) { "" }.trim(),
                additionalDescriptor = row.getOrElse(7) { "" }.trim()
            )
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes
    }
}
