package com.galaxy.systemmonitor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceInfoTest {

    @Test
    fun `samsung model codes map to marketing names`() {
        assertEquals("Galaxy S25 Ultra", DeviceNames.marketName("samsung", "SM-S9380"))
        assertEquals("Galaxy M14 5G", DeviceNames.marketName("samsung", "SM-M146B"))
        // Some builds report underscores instead of hyphens.
        assertEquals("Galaxy S25 Ultra", DeviceNames.marketName("samsung", "SM_S938B"))
        assertNull(DeviceNames.marketName("samsung", "SM-X999"))
        assertNull(DeviceNames.marketName("Google", "SM-S9380"))
    }

    @Test
    fun `soc codes map to chip names`() {
        assertEquals("Snapdragon 8 Elite for Galaxy", DeviceNames.socName("samsung", "SM8750"))
        assertEquals("Snapdragon 8 Elite", DeviceNames.socName("Xiaomi", "SM8750"))
        assertEquals("Exynos 1330", DeviceNames.socName("samsung", "s5e8535"))
        assertNull(DeviceNames.socName("samsung", "SM1234"))
    }

    @Test
    fun `one ui property is formatted`() {
        assertEquals("7.0", DeviceNames.oneUiVersion("70000"))
        assertEquals("8.1", DeviceNames.oneUiVersion("80100"))
        assertNull(DeviceNames.oneUiVersion(""))
        assertNull(DeviceNames.oneUiVersion("abc"))
    }

    @Test
    fun `core list is parsed`() {
        assertEquals((0..7).toList(), CpuTopology.parseCoreList("0-7\n"))
        assertEquals(listOf(0, 1, 2, 3, 6), CpuTopology.parseCoreList("0-3,6"))
        assertEquals(emptyList<Int>(), CpuTopology.parseCoreList(null))
    }

    @Test
    fun `frequencies are converted and garbage rejected`() {
        assertEquals(4_473, CpuTopology.khzToMhz("4473600\n"))
        assertNull(CpuTopology.khzToMhz("0"))
        assertNull(CpuTopology.khzToMhz("Permission denied"))
        assertNull(CpuTopology.khzToMhz(null))
    }

    @Test
    fun `cores are grouped into clusters, fastest first`() {
        val max = (0..5).associateWith { 3_532 as Int? } + (6..7).associateWith { 4_473 as Int? }
        val min = (0..7).associateWith { 384 as Int? }
        val clusters = CpuTopology.clusters(max, min)
        assertEquals(2, clusters.size)
        assertEquals(listOf(6, 7), clusters[0].cores)
        assertEquals(4_473, clusters[0].maxMhz)
        assertEquals((0..5).toList(), clusters[1].cores)
        assertEquals(384, clusters[1].minMhz)
    }
}
