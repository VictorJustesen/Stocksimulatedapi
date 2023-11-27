package com.example.stockapi

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.io.File
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.Collections.emptyList
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
data class StockGroup(val name: String, val tickers: List<Stock>)

data class PricePoint(val timestamp: Long, val price: Double)

enum class Interval {
    MINUTE, FIFTEEN_MINUTES, HOUR, DAY
}

@Service
class UpdateStock {

    private var lastMinute: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
    private var lastFifteenMinutes: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
    private var lastHour: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
    private var lastDay: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS)

        private val Stocks = ConcurrentHashMap<String, Stock>() // All individual stocks
        private val stockGroups = ConcurrentHashMap<String, StockGroup>() // Groups of stocks
        private val folderPath = "tickers" // Folder to store files

    private val debugMode = false // Set to true to enable debug mode, false to disable

    private fun debugPrint(message: String) {
        if (debugMode) { println(message) } }

        init {


            stockGroups["C25"] = StockGroup("C25", listOf(Stock("NOVO", 750.0)))

            stockGroups["S&P500"] = StockGroup("S&P500", listOf(Stock("AAPL", 150.0)))


            // Add all stocks to the comprehensive list
            stockGroups.values.forEach { group ->
                group.tickers.forEach { stock ->
                    Stocks[stock.ticker] = stock
                    loadHistoricalData(stock.ticker)
                }
            }
        }

    fun getTickersByGroup(groupName: String): List<String> {
        val group = stockGroups[groupName] ?: throw IllegalArgumentException("Stock group not found")
        return group.tickers.map { it.ticker }
    }


    private fun loadHistoricalData(ticker: String) {
        val file = File("$folderPath/$ticker.txt")
        if (!file.exists()) {
            file.createNewFile()
        }

        file.forEachLine { line ->
            // Assuming the average is always present and is the first value
            val averagePart = line.substringAfter("Average=").substringBefore(",")
            averagePart.toDoubleOrNull()?.let { price ->
                Stocks[ticker]?.addHistoricalData(price)
            }
        }
    }

    fun getHistoricalData(ticker: String, interval: Interval, count: Int): List<Double> {
        val stock = Stocks[ticker] ?: throw IllegalArgumentException("Stock not found")
        val file = File("$folderPath/$ticker.txt")
        if (!file.exists()) return emptyList()

        val data = file.readLines()
        val intervalData = data.filter { it.startsWith(interval.name) }.takeLast(count)

        return intervalData.mapNotNull { line ->
            // Extract the average value from the line
            val averagePart = line.substringAfter("Average=").substringBefore(",")
            averagePart.toDoubleOrNull()
        }
    }

    @Scheduled(fixedRate = 1000)
    fun updateStockPrices() {
        Stocks.values.forEach { stock ->
            //debugPrint("Processing stock: ${stock.ticker}")

            val change = (Random.nextDouble() - 0.5) * 0.1 // Change in price
            stock.currentPrice += change
            stock.addHistoricalData(stock.currentPrice)

            if (timeToAggregateMinute()) {
                aggregateDataForAllStocks(Interval.MINUTE)
            }
            if (timeToAggregateFifteenMinutes()) {
                aggregateDataForAllStocks(Interval.FIFTEEN_MINUTES)
            }
            if (timeToAggregateHour()) {
                aggregateDataForAllStocks(Interval.HOUR)
            }
            if (timeToAggregateDay()) {
                aggregateDataForAllStocks(Interval.DAY)
            }
        }
    }

    private fun appendToFile(ticker: String, data: String) {
        File("$folderPath/$ticker.txt").appendText(data)
        debugPrint("appending ticker: $ticker data:$data")
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
    private fun aggregateDataForAllStocks(interval: Interval) {
        Stocks.values.forEach { stock ->
            aggregateDataForInterval(stock, interval)
        }
    }
    private fun aggregateDataForInterval(stock: Stock, interval: Interval) {
        val aggregatedData = stock.getAggregatedData(interval, calculateCountForInterval(interval))

        val averagePrice = if (aggregatedData.isNotEmpty()) aggregatedData.average() else 0.0
        val maxPrice = aggregatedData.maxOrNull() ?: 0.0
        val minPrice = aggregatedData.minOrNull() ?: 0.0

        // Append the average, max, and min values to the file
        val dataString = "${interval.name}: Average=$averagePrice, Max=$maxPrice, Min=$minPrice\n"
        appendToFile(stock.ticker, dataString)
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