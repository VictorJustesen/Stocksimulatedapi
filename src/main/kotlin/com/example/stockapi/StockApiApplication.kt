package com.example.stockapi

import org.springframework.aot.hint.TypeReference.listOf
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

    @RestController
    class StockController(val updateStock: UpdateStock) {

        @RestController
        class StockController(val updateStock: UpdateStock) {

            @GetMapping("/stock/{ticker}/{interval}/{count}")
            fun getHistoricalData(
                @PathVariable ticker: String,
                @PathVariable interval: String,
                @PathVariable count: Int
            ): List<Triple<Double, Double, Double>> {
                val intervalEnum = Interval.valueOf(interval.toUpperCase())
                return updateStock.getHistoricalData(ticker, intervalEnum, count)
            }
        }


    @GetMapping("/group/tickers/{groupName}")
    fun getGroupTickers(@PathVariable groupName: String): List<String> {
        return updateStock.getTickersByGroup(groupName)
    }

}}