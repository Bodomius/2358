package com.project.ti2358.data.api

import com.project.ti2358.data.model.body.LimitOrderBody
import com.project.ti2358.data.model.body.MarketOrderBody
import com.project.ti2358.data.model.dto.LimitOrder
import com.project.ti2358.data.model.dto.MarketOrder
import com.project.ti2358.data.model.dto.Order
import com.project.ti2358.data.model.dto.Orders
import com.project.ti2358.data.model.response.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface OrdersApi {
    @POST("orders/market-order")
    suspend fun placeMarketOrder(
        @Body orderBody: MarketOrderBody,
        @Query("figi") figi: String,
        @Query("brokerAccountId") brokerAccountId: String? = null
    ): Response<MarketOrder>

    @POST("orders/limit-order")
    suspend fun placeLimitOrder(
        @Body orderBody: LimitOrderBody,
        @Query("figi") figi: String,
        @Query("brokerAccountId") brokerAccountId: String? = null
    ): Response<LimitOrder>

    @GET("orders")
    suspend fun orders(
        @Query("brokerAccountId") brokerAccountId: String? = null
    ): Response<List<Order>>

    @POST("orders/cancel")
    suspend fun cancel(
        @Query("orderId") orderId: String? = null,
        @Query("brokerAccountId") brokerAccountId: String? = null
    ): Response<Any?>
}