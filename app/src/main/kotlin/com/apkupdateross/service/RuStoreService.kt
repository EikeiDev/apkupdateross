package com.apkupdateross.service

import com.apkupdateross.data.rustore.RuStoreApiResponse
import com.apkupdateross.data.rustore.RuStoreAppDetails
import com.apkupdateross.data.rustore.RuStoreDownloadBody
import com.apkupdateross.data.rustore.RuStoreDownloadRequest
import com.apkupdateross.data.rustore.RuStoreSearchBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface RuStoreService {

    @GET("applicationData/overallInfo/{packageName}")
    suspend fun getAppDetails(
        @Path("packageName") packageName: String
    ): RuStoreApiResponse<RuStoreAppDetails>

    @POST("applicationData/v2/download-link")
    suspend fun getDownloadLink(
        @Body request: RuStoreDownloadRequest
    ): RuStoreApiResponse<RuStoreDownloadBody>

    @GET("applicationData/apps")
    suspend fun searchApps(
        @Query("query") query: String,
        @Query("pageNumber") page: Int = 0,
        @Query("pageSize") size: Int = 20
    ): RuStoreApiResponse<RuStoreSearchBody>

}
