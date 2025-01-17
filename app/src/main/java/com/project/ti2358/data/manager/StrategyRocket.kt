package com.project.ti2358.data.manager

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import com.project.ti2358.MainActivity
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.service.toMoney
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import kotlin.math.abs

@KoinApiExtension
class StrategyRocket() : KoinComponent {
    private val stockManager: StockManager by inject()
    private val strategySpeaker: StrategySpeaker by inject()
    private val strategyTelegram: StrategyTelegram by inject()

    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()
    var rocketStocks: MutableList<RocketStock> = mutableListOf()
    var cometStocks: MutableList<RocketStock> = mutableListOf()

    private var started: Boolean = false

    fun process(): MutableList<Stock> {
        val all = stockManager.getWhiteStocks()

        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks.clear()
        stocks.addAll(all.filter { it.getPriceNow() > min && it.getPriceNow() < max })

        return stocks
    }

    fun startStrategy() {
        rocketStocks.clear()
        cometStocks.clear()
        process()
        started = true
    }

    fun stopStrategy() {
        started = false
    }

    @Synchronized
    fun processStrategy(stock: Stock) {
        if (!started) return
        if (stock !in stocks) return

        val percentRocket = SettingsManager.getRocketChangePercent()
        val minutesRocket = SettingsManager.getRocketChangeMinutes()
        val volumeRocket = SettingsManager.getRocketChangeVolume()

        if (stock.minuteCandles.isNotEmpty()) {
            val firstCandle: Candle?
            val lastCandle = stock.minuteCandles.last()
            var fromIndex = 0
            if (stock.minuteCandles.size >= minutesRocket) {
                fromIndex = stock.minuteCandles.size - minutesRocket
                firstCandle = stock.minuteCandles[fromIndex]
            } else {
                fromIndex = 0
                firstCandle = stock.minuteCandles.first()
            }

            val deltaMinutes = ((lastCandle.time.time - firstCandle.time.time) / 60.0 / 1000.0).toInt()
            if (deltaMinutes > minutesRocket) { // если дальше настроек, игнорим
                return
            }

            var volume = 0
            for (i in fromIndex until stock.minuteCandles.size) {
                volume += stock.minuteCandles[i].volume
            }

            val changePercent = lastCandle.closingPrice / firstCandle.openingPrice * 100.0 - 100.0
            if (volume >= volumeRocket && abs(changePercent) >= abs(percentRocket)) {

                // если уже есть в списке, то не дублировать
                if (rocketStocks.find { it.stock.ticker == stock.ticker && ((Calendar.getInstance().time.time - it.fireTime) / 60.0 / 1000.0).toInt() < 5 } != null) return
                if (cometStocks.find { it.stock.ticker == stock.ticker && ((Calendar.getInstance().time.time - it.fireTime) / 60.0 / 1000.0).toInt() < 5 } != null) return

                val rocketStock = RocketStock(stock, firstCandle.openingPrice, lastCandle.closingPrice, deltaMinutes, volume, changePercent, lastCandle.time.time)
                rocketStock.process()
                if (changePercent > 0) {
                    rocketStocks.add(0, rocketStock)
                } else {
                    cometStocks.add(0, rocketStock)
                }
                createRocket(rocketStock)
            }
        }
    }

    private fun createRocket(rocketStock: RocketStock) {
        val context: Context = TheApplication.application.applicationContext

        val ticker = rocketStock.ticker
        val notificationChannelId = ticker

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                notificationChannelId,
                "Rocket notifications channel $ticker",
                NotificationManager.IMPORTANCE_HIGH
            ).let {
                it.description = notificationChannelId
                it.lightColor = Color.RED
                it.enableVibration(true)
                it.enableLights(true)
                it
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent = Intent(context, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(context, 0, notificationIntent, 0)
        }

        val builder: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, notificationChannelId) else Notification.Builder(context)

        val changePercent = if (rocketStock.changePercent > 0) {
            "+%.2f%%".format(locale = Locale.US, rocketStock.changePercent)
        } else {
            "%.2f%%".format(locale = Locale.US, rocketStock.changePercent)
        }

        strategySpeaker.speakRocket(rocketStock)
        strategyTelegram.sendRocket(rocketStock)

        val title = "$ticker: ${rocketStock.priceFrom.toMoney(rocketStock.stock)} -> ${rocketStock.priceTo.toMoney(rocketStock.stock)} = $changePercent за ${rocketStock.time} мин"

        val notification = builder
            .setSubText("$$ticker $changePercent")
            .setContentTitle(title)
            .setShowWhen(true)
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOnlyAlertOnce(true)
            .setOngoing(false)
            .build()

        val manager = context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
        val uid = kotlin.random.Random.Default.nextInt(0, 100000)
        manager.notify(ticker, uid, notification)

        val alive: Long = SettingsManager.getRocketNotifyAlive().toLong()
        GlobalScope.launch(Dispatchers.Main) {
            delay(1000 * alive)
            manager.cancel(ticker, uid)
        }
    }
}