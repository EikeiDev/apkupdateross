package com.apkupdateross.service

import com.apkupdateross.data.github.GitHubRelease
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface GitHubService {

    @GET("/repos/{user}/{repo}/releases")
    suspend fun getReleases(
        @Path("user") user: String = "EikeiDev",
        @Path("repo") repo: String = "apkupdateross",
        @Header("Authorization") authHeader: String? = null
    ): List<GitHubRelease>

}
