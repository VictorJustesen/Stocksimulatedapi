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
        // Return the last 'count' price points relevant to the interval
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
    private var simulatedTime: Int = 0

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

    fun getHistoricalData(ticker: String, interval: Interval, count: Int): List<Triple<Double, Double, Double>> {
        val stock = Stocks[ticker] ?: throw IllegalArgumentException("Stock not found")
        val file = File("$folderPath/$ticker.txt")
        if (!file.exists()) return emptyList()

        val data = file.readLines()
        val intervalData = data.filter { it.startsWith(interval.name) }.takeLast(count)

        return intervalData.mapNotNull { line ->
            val average = line.substringAfter("Average=").substringBefore(",").toDoubleOrNull()
            val max = line.substringAfter("Max=").substringBefore(",").toDoubleOrNull()
            val min = line.substringAfter("Min=").toDoubleOrNull()
            if (average != null && max != null && min != null) Triple(average, max, min) else null
        }
    }


    @Scheduled(fixedRate = 10)
    fun updateStockPrices() {

        simulatedTime += 1
        Stocks.values.forEach { stock ->
            //debugPrint("Processing stock: ${stock.ticker}")

            val change = (Random.nextDouble() - 0.5) * 0.1 // Change in price
            stock.currentPrice += change
            stock.addHistoricalData(stock.currentPrice)

            if (simulatedTime % 60 == 0) { // Minute
                val relevantData = stock.getAggregatedData(Interval.MINUTE, calculateCountForInterval(Interval.MINUTE))

                // Calculate the average, max, and min for the relevant data
                val averagePrice = if (relevantData.isNotEmpty()) relevantData.average() else 0.0
                val maxPrice = relevantData.maxOrNull() ?: 0.0
                val minPrice = relevantData.minOrNull() ?: 0.0

                // Append the average, max, and min values to the file
                val dataString = "${Interval.MINUTE.name}: Average=$averagePrice, Max=$maxPrice, Min=$minPrice\n"
                appendToFile(stock.ticker,dataString)
            }

            if (simulatedTime % (15 * 60) == 0) { // Fifteen minutes
                aggregateDataForInterval(Interval.FIFTEEN_MINUTES,Interval.MINUTE,15, stock)
            }
            if (simulatedTime % (60 * 60) == 0) { // Hour
                aggregateDataForInterval(Interval.HOUR,Interval.FIFTEEN_MINUTES,4, stock)
            }
            if (simulatedTime % (24 * 60 * 60) == 0) { // Day
                aggregateDataForInterval(Interval.DAY,Interval.HOUR,24, stock)
                cleanUpDataFiles()
            }

        }
    }

    private fun appendToFile(ticker: String, data: String) {
        File("$folderPath/$ticker.txt").appendText(data)
        debugPrint("appending ticker: $ticker data:$data")
    }



    private fun aggregateDataForInterval(interval: Interval, earlyInterval: Interval, timesEarlyInterval: Int, stock: Stock) {
            val historicalData = getHistoricalData(stock.ticker, earlyInterval, timesEarlyInterval)

            val averagePrice = historicalData.map { it.first }.average()
            val maxOfMax = historicalData.maxOfOrNull { it.second } ?: 0.0
            val minOfMin = historicalData.minOfOrNull { it.third } ?: 0.0

            val dataString = "${interval.name}: Average=$averagePrice, Max=$maxOfMax, Min=$minOfMin\n"
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


// Run every minute or choose an appropriate interval
fun cleanUpDataFiles() {
    Stocks.keys.forEach { ticker ->
        cleanUpFileForTicker(ticker)
    }
}

private fun cleanUpFileForTicker(ticker: String) {
    val file = File("$folderPath/$ticker.txt")
    if (!file.exists()) return

    val allLines = file.readLines()
    val cleanedLines = allLines.groupBy { it.substringBefore(':') } // Group by interval
        .flatMap { (_, lines) -> lines.takeLast(300) } // Keep only last 300 entries per interval

    file.writeText(cleanedLines.joinToString("\n")) // Rewrite the file with cleaned data
}
}