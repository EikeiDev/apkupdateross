package com.apkupdateross.data.ui

enum class InstallFailureReason {
	NONE,
	CANCELLED,
	SIGNATURE_CONFLICT,
	STORAGE_FULL,
	INCOMPATIBLE,
	DOWNGRADE,
	PARSE_ERROR,
	UNKNOWN
}

data class AppInstallStatus(
	val success: Boolean,
	val id: Int,
	val snack: Boolean = true,
	val reason: InstallFailureReason = InstallFailureReason.NONE,
	val message: String = ""
)

fun classifyInstallFailure(statusCode: Int, statusMessage: String?): InstallFailureReason {
	val msg = statusMessage.orEmpty().uppercase()
	return when {
		statusCode == 3 || msg.contains("ABORTED") || msg.contains("CANCEL") ->
			InstallFailureReason.CANCELLED
		statusCode == 5 || msg.contains("UPDATE_INCOMPATIBLE") || msg.contains("INCONSISTENT_CERTIFICATES") || msg.contains("CONFLICT") ->
			InstallFailureReason.SIGNATURE_CONFLICT
		statusCode == 6 || msg.contains("INSUFFICIENT_STORAGE") || msg.contains("STORAGE") ->
			InstallFailureReason.STORAGE_FULL
		msg.contains("VERSION_DOWNGRADE") || msg.contains("DOWNGRADE") ->
			InstallFailureReason.DOWNGRADE
		statusCode == 7 || msg.contains("OLDER_SDK") || msg.contains("NO_MATCHING_ABIS") || msg.contains("INCOMPATIBLE") ->
			InstallFailureReason.INCOMPATIBLE
		statusCode == 4 || msg.contains("INVALID_APK") || msg.contains("PARSE") || msg.contains("CORRUPT") ->
			InstallFailureReason.PARSE_ERROR
		else -> InstallFailureReason.UNKNOWN
	}
}
