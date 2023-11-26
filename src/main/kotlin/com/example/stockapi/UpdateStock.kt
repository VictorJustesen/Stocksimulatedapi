package com.example.stockapi

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.io.File
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

data class Stock(val ticker: String, val originalPrice: Double) {
    var currentPrice: Double = originalPrice
    private val historicalData = mutableListOf<PricePoint>()

    fun addHistoricalData(price: Double) {
        historicalData.add(PricePoint(System.currentTimeMillis(), price))
    }

    fun getAggregatedData(interval: Interval, count: Int): List<Double> {
        // Simplified for demonstration
        return historicalData.takeLast(count).map { it.price }
    }
}

data class PricePoint(val timestamp: Long, val price: Double)

enum class Interval {
    MINUTE, FIFTEEN_MINUTES, HOUR, DAY
}

@Service
class UpdateStock {

    private val stocks = ConcurrentHashMap<String, Stock>()
    private val folderPath = "tickers" // Folder to store files
    private var lastMinute: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
    private var lastFifteenMinutes: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
    private var lastHour: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
    private var lastDay: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS)

    init {
        File(folderPath).mkdirs() // Create the folder if it doesn't exist
        // Initialize with some stocks
        stocks["AAPL"] = Stock("AAPL", 150.0)
        // Load historical data from file for each stock
        stocks.keys.forEach { loadHistoricalData(it) }
    }

    private fun loadHistoricalData(ticker: String) {
        val file = File("$folderPath/$ticker.txt")
        if (!file.exists()) {
            file.createNewFile()
        }

        file.forEachLine { line ->
            line.toDoubleOrNull()?.let { price ->
                stocks[ticker]?.addHistoricalData(price)
            }
        }
    }

    fun getHistoricalData(ticker: String, interval: Interval, count: Int): List<Double> {
        val stock = stocks[ticker] ?: throw IllegalArgumentException("Stock not found")
        val file = File("$folderPath/$ticker.txt")
        if (!file.exists()) return emptyList()

        val data = file.readLines()
        val intervalData = data.filter { it.startsWith(interval.name) }.takeLast(count)
        return intervalData.flatMap { it.removePrefix("${interval.name}:").split(",").mapNotNull { price -> price.toDoubleOrNull() } }
    }

    @Scheduled(fixedRate = 1000)
    fun updateStockPrices() {
        stocks.values.forEach { stock ->
            val change = (Random.nextDouble() - 0.5) * 0.1 // Change in price
            stock.currentPrice += change
            stock.addHistoricalData(stock.currentPrice)

            // Check for aggregation triggers
            if (timeToAggregateMinute()) {
                aggregateDataForInterval(stock, Interval.MINUTE)
            }
            if (timeToAggregateFifteenMinutes()) {
                aggregateDataForInterval(stock, Interval.FIFTEEN_MINUTES)
            }
            if (timeToAggregateHour()) {
                aggregateDataForInterval(stock, Interval.HOUR)
            }
            if (timeToAggregateDay()) {
                aggregateDataForInterval(stock, Interval.DAY)
            }
        }
    }

    private fun appendToFile(ticker: String, data: String) {
        File("$folderPath/$ticker.txt").appendText(data)
    }

    private fun timeToAggregateMinute(): Boolean {
        val currentMinute = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
        return currentMinute != lastMinute.also { lastMinute = currentMinute }
    }

    private fun timeToAggregateFifteenMinutes(): Boolean {
        val currentFifteenMinutes = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
        if (currentFifteenMinutes.minute % 15 == 0 && currentFifteenMinutes != lastFifteenMinutes) {
            lastFifteenMinutes = currentFifteenMinutes
            return true
        }
        return false
    }

    private fun timeToAggregateHour(): Boolean {
        val currentHour = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
        return currentHour != lastHour.also { lastHour = currentHour }
    }

    private fun timeToAggregateDay(): Boolean {
        val currentDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS)
        return currentDay != lastDay.also { lastDay = currentDay }
    }

    private fun aggregateDataForInterval(stock: Stock, interval: Interval) {
        val aggregatedData = stock.getAggregatedData(interval, calculateCountForInterval(interval))
        val averagePrice = if (aggregatedData.isNotEmpty()) aggregatedData.average() else 0.0
        appendToFile(stock.ticker, "${interval.name}:$averagePrice\n")
    }

    private fun calculateCountForInterval(interval: Interval): Int {
        return when (interval) {
            Interval.MINUTE -> 60 // 60 seconds
            Interval.FIFTEEN_MINUTES -> 15 * 60 // 15 minutes
            Interval.HOUR -> 4 // 4 fifteen-minute intervals
            Interval.DAY -> 24 // 24 hours
        }
    }
}