package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.utils.isParallelExportMemorySafe
import com.moody.moodyvideoeditor.utils.isParallelExportThermallySafe
import android.os.PowerManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportResourcePolicyTest {

    @Test
    fun disablesParallelExportOnRedmiClassMemoryBudget() {
        val totalMemoryBytes = 6L * 1024L * 1024L * 1024L
        val availableMemoryBytes = 4L * 1024L * 1024L * 1024L

        assertFalse(
            isParallelExportMemorySafe(
                totalMemoryBytes,
                availableMemoryBytes,
                lowMemory = false
            )
        )
    }

    @Test
    fun enablesParallelExportOnlyWithSufficientTotalAndAvailableMemory() {
        val totalMemoryBytes = 8L * 1024L * 1024L * 1024L

        assertTrue(
            isParallelExportMemorySafe(
                totalMemoryBytes,
                2L * 1024L * 1024L * 1024L,
                lowMemory = false
            )
        )
        assertFalse(
            isParallelExportMemorySafe(
                totalMemoryBytes,
                1L * 1024L * 1024L * 1024L,
                lowMemory = false
            )
        )
    }

    @Test
    fun disablesParallelExportWhenSystemReportsLowMemory() {
        assertFalse(
            isParallelExportMemorySafe(
                12L * 1024L * 1024L * 1024L,
                6L * 1024L * 1024L * 1024L,
                lowMemory = true
            )
        )
    }

    @Test
    fun disablesParallelExportAtModerateOrHigherThermalStatus() {
        assertTrue(
            isParallelExportThermallySafe(PowerManager.THERMAL_STATUS_LIGHT)
        )
        assertFalse(
            isParallelExportThermallySafe(PowerManager.THERMAL_STATUS_MODERATE)
        )
        assertFalse(
            isParallelExportThermallySafe(PowerManager.THERMAL_STATUS_SEVERE)
        )
        assertFalse(isParallelExportThermallySafe(null))
    }
}
