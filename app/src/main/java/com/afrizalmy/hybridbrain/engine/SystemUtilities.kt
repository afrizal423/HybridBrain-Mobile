package com.afrizalmy.hybridbrain.engine

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.afrizalmy.hybridbrain.data.model.HardwareInfo
import java.io.File

object SystemUtilities {

    fun detectHardware(context: Context): HardwareInfo {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val availRamMb = memInfo.availMem / (1024 * 1024)

        val osName = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val cpuAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: Build.CPU_ABI

        // Deteksi hardware accelerator
        val hardware = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else Build.HARDWARE

        val hasNpu = hardware.contains("npu") || hardware.contains("apu") || board.contains("tensor") || board.contains("snapdragon")
        val delegateName = if (hasNpu) "NPU / NNAPI Neural Accelerator" else "CPU (ARM Neon / XNNPACK Optimized)"
        val gpuOrNpu = if (hasNpu) "Neural Engine / GPU ($soc)" else "Adreno / Mali GPU ($cpuAbi)"

        val badgeLabel = "⚡ $delegateName"

        return HardwareInfo(
            os = osName,
            cpu = "$soc ($cpuAbi)",
            ramAvailableMb = availRamMb,
            ramTotalMb = totalRamMb,
            gpuOrNpu = gpuOrNpu,
            delegateName = delegateName,
            badgeLabel = badgeLabel
        )
    }

    fun suggestAutoRouting(query: String): String? {
        val clean = query.trim().lowercase()

        val webKeywords = listOf(
            "terbaru", "hari ini", "berita", "kurs", "cuaca", "saham",
            "browsing", "link web", "internet", "update 2026", "harga terkini"
        )

        val isWebQuery = webKeywords.any { clean.contains(it) }
        val questionMarks = query.count { it == '?' }
        val words = clean.split("\\s+".toRegex()).size
        val isComplex = questionMarks >= 4 || words > 100 || query.contains("\n1.")

        return when {
            isWebQuery -> "💡 Tip Smart Routing: Pertanyaan ini memerlukan informasi terkini. Pertimbangkan beralih ke Mode Gemini Cloud (BYOK) untuk penalaran web lebih luas."
            isComplex -> "💡 Tip Smart Routing: Pertanyaan memiliki struktur kompleks. Mode Gemini Cloud direkomendasikan untuk analisis multi-tahap mendalam."
            else -> null
        }
    }
}
