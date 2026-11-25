package com.apkupdateross.service

import com.apkupdateross.data.github.GitHubRelease
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface CodebergService {

    @GET("api/v1/repos/{user}/{repo}/releases")
    suspend fun getReleases(
        @Path("user") user: String,
        @Path("repo") repo: String,
        @Header("Authorization") authHeader: String? = null,
        @Query("limit") limit: Int = 50
    ): List<GitHubRelease>
}
