package com.example.stockapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
@EnableScheduling
class StockApiApplication

fun main(args: Array<String>) {
    runApplication<StockApiApplication>(*args)
}

@RestController
class StockController(val updateStock: UpdateStock) {

    @GetMapping("/stock/{ticker}/{interval}/{count}")
    fun getHistoricalData(
        @PathVariable ticker: String,
        @PathVariable interval: String,
        @PathVariable count: Int
    ): List<List<Double>> {
        val intervalEnum = Interval.valueOf(interval.toUpperCase())
        val historicalData = updateStock.getHistoricalData(ticker, intervalEnum, count)

        return historicalData.map { dataLine ->
            // Ensure dataLine is a String
            val line = dataLine.toString()

            val average = line.substringAfter("Average=").substringBefore(",").toDoubleOrNull() ?: 0.0
            val max = line.substringAfter("Max=").substringBefore(",").toDoubleOrNull() ?: 0.0
            val min = line.substringAfter("Min=").substringBefore("\n").toDoubleOrNull() ?: 0.0

            listOf(average, max, min)
        }
    }


    @GetMapping("/group/tickers/{groupName}")
    fun getGroupTickers(@PathVariable groupName: String): List<String> {
        return updateStock.getTickersByGroup(groupName)
    }

}