package com.apkupdateross

import android.content.res.Configuration
import com.apkupdateross.ui.component.isTelevisionUiMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvSupportTest {
    @Test
    fun televisionModeIsDetectedWithOtherUiModeFlagsPresent() {
        val uiMode = Configuration.UI_MODE_TYPE_TELEVISION or Configuration.UI_MODE_NIGHT_YES
        assertTrue(isTelevisionUiMode(uiMode))
    }

    @Test
    fun phoneAndCarModesAreNotTreatedAsTelevision() {
        assertFalse(isTelevisionUiMode(Configuration.UI_MODE_TYPE_NORMAL))
        assertFalse(isTelevisionUiMode(Configuration.UI_MODE_TYPE_CAR))
    }
}
