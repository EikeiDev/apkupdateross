package com.apkupdateross

import com.apkupdateross.data.ui.InstallFailureReason
import com.apkupdateross.data.ui.classifyInstallFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class InstallFailureClassifierTest {

	@Test
	fun classifiesPackageInstallerAndPmInstallErrors() {
		assertEquals(InstallFailureReason.CANCELLED, classifyInstallFailure(3, "INSTALL_FAILED_ABORTED"))
		assertEquals(InstallFailureReason.SIGNATURE_CONFLICT, classifyInstallFailure(5, ""))
		assertEquals(
			InstallFailureReason.SIGNATURE_CONFLICT,
			classifyInstallFailure(-1, "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Signatures do not match]")
		)
		assertEquals(
			InstallFailureReason.STORAGE_FULL,
			classifyInstallFailure(6, "INSTALL_FAILED_INSUFFICIENT_STORAGE")
		)
		assertEquals(
			InstallFailureReason.DOWNGRADE,
			classifyInstallFailure(-1, "Failure [INSTALL_FAILED_VERSION_DOWNGRADE]")
		)
		assertEquals(
			InstallFailureReason.INCOMPATIBLE,
			classifyInstallFailure(-1, "Failure [INSTALL_FAILED_NO_MATCHING_ABIS]")
		)
		assertEquals(
			InstallFailureReason.PARSE_ERROR,
			classifyInstallFailure(4, "INSTALL_PARSE_FAILED_NOT_APK")
		)
		assertEquals(
			InstallFailureReason.UNKNOWN,
			classifyInstallFailure(1, "Something else")
		)
	}
}
