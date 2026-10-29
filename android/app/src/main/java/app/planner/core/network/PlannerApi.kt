package app.planner.core.network

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.*

interface PlannerApi {
    @POST("auth/enroll") suspend fun enroll(@Body body: JsonObject): Response<JsonObject>
    @GET("config") suspend fun config(@Header("Authorization") authorization: String): Response<JsonObject>
    @GET("changes") suspend fun changes(@Header("Authorization") authorization: String, @Query("cursor") cursor: String): Response<JsonObject>
    @GET suspend fun get(@Url path: String, @Header("Authorization") authorization: String): Response<JsonObject>
    @POST suspend fun post(@Url path: String, @Header("Authorization") authorization: String, @Header("Idempotency-Key") key: String, @Body body: JsonObject): Response<JsonObject>
    @HTTP(method = "PATCH", hasBody = true) suspend fun patch(@Url path: String, @Header("Authorization") authorization: String, @Header("Idempotency-Key") key: String, @Body body: JsonObject): Response<JsonObject>
    @HTTP(method = "DELETE", hasBody = true) suspend fun delete(@Url path: String, @Header("Authorization") authorization: String, @Body body: JsonObject): Response<JsonObject>
}
