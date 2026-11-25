package com.apkupdateross.data.apkpure


data class GetAppUpdateResponse(
    val retcode: Int,
    val app_update_response: List<AppUpdateResponse>
)
