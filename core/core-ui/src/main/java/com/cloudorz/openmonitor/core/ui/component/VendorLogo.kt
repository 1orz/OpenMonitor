package com.cloudorz.openmonitor.core.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudorz.openmonitor.core.ui.R

/**
 * Displays a vendor logo for a known SoC manufacturer.
 * Uses bundled PNG assets for all recognized vendors;
 * falls back to a colored text badge for unknown ones.
 */
@Composable
fun VendorLogo(
    vendor: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val normalized = vendor.lowercase()
    val logoRes: Int? = when {
        "qualcomm" in normalized || "snapdragon" in normalized -> R.drawable.ic_snapdragon
        "mediatek" in normalized -> R.drawable.ic_vendor_mediatek
        "samsung" in normalized || "exynos" in normalized -> R.drawable.ic_vendor_exynos
        "hisilicon" in normalized || "kirin" in normalized -> R.drawable.ic_vendor_kirin
        "unisoc" in normalized -> R.drawable.ic_vendor_unisoc
        "rockchip" in normalized -> R.drawable.ic_vendor_rockchip
        "intel" in normalized -> R.drawable.ic_vendor_intel
        "nvidia" in normalized || "tegra" in normalized -> R.drawable.ic_vendor_tegra
        "allwinner" in normalized -> R.drawable.ic_vendor_allwinner
        "amlogic" in normalized -> R.drawable.ic_vendor_amlogic
        "marvell" in normalized -> R.drawable.ic_vendor_marvell
        "amd" in normalized -> R.drawable.ic_vendor_amd
        "jlq" in normalized -> R.drawable.ic_vendor_jlq
        "realtek" in normalized -> R.drawable.ic_vendor_realtek
        "xiaomi" in normalized || "xring" in normalized -> null // text badge below
        "google" in normalized || "tensor" in normalized -> null
        else -> null
    }

    if (logoRes != null) {
        Image(
            painter = painterResource(logoRes),
            contentDescription = vendor,
            modifier = modifier.size(size),
        )
    } else {
        // Text badge fallback for vendors without PNG
        val (label, color) = when {
            "xiaomi" in normalized || "xring" in normalized -> "MI" to Color(0xFFFF6900)
            "google" in normalized || "tensor" in normalized -> "G" to Color(0xFF4285F4)
            "broadcom" in normalized -> "BRCM" to Color(0xFFCC0000)
            "sony" in normalized -> "Sony" to Color(0xFF000000)
            else -> vendor.take(4) to Color(0xFF607D8B)
        }
        VendorBadge(label, color, size, modifier)
    }
}

/**
 * Displays a device OEM brand logo using bundled PNG assets.
 * Falls back to a colored text badge for unknown brands.
 */
@Composable
fun DeviceBrandLogo(
    brand: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    if (brand.isBlank()) return
    val normalized = brand.lowercase()
    val logoRes: Int? = when {
        // Chinese / Asian major brands
        "oneplus" in normalized -> R.drawable.ic_brand_oneplus
        "realme" in normalized -> R.drawable.ic_brand_realme
        "oppo" in normalized -> R.drawable.ic_brand_oppo
        "iqoo" in normalized -> R.drawable.ic_brand_iqoo
        "vivo" in normalized -> R.drawable.ic_brand_vivo
        "poco" in normalized -> R.drawable.ic_brand_poco
        "redmi" in normalized -> R.drawable.ic_brand_redmi
        "xiaomi" in normalized || "mi " in normalized || normalized == "mi" ->
            R.drawable.ic_brand_mi
        "samsung" in normalized -> R.drawable.ic_brand_samsung
        "honor" in normalized -> R.drawable.ic_brand_honor
        "huawei" in normalized -> R.drawable.ic_brand_huawei
        "meizu" in normalized -> R.drawable.ic_brand_meizu
        "nubia" in normalized -> R.drawable.ic_brand_nubia
        "blackshark" in normalized || "black shark" in normalized -> R.drawable.ic_brand_blackshark
        "smartisan" in normalized -> R.drawable.ic_brand_smartisan
        "coolpad" in normalized -> R.drawable.ic_brand_coolpad
        "zte" in normalized -> R.drawable.ic_brand_zte
        "hisense" in normalized -> R.drawable.ic_brand_hisense
        "gionee" in normalized -> R.drawable.ic_brand_gionee
        "leeco" in normalized || "letv" in normalized -> R.drawable.ic_brand_leeco
        "bbk" in normalized -> R.drawable.ic_brand_bbk
        "infinix" in normalized -> R.drawable.ic_brand_infinix
        "tecno" in normalized -> R.drawable.ic_brand_tecno
        "itel" in normalized -> R.drawable.ic_brand_itel

        // Global brands
        "google" in normalized || "pixel" in normalized -> R.drawable.ic_brand_google
        "sony" in normalized -> R.drawable.ic_brand_sony
        "motorola" in normalized || "moto" in normalized -> R.drawable.ic_brand_moto
        "asus" in normalized -> R.drawable.ic_brand_asus
        "nokia" in normalized -> R.drawable.ic_brand_nokia
        "lg" in normalized -> R.drawable.ic_brand_lg
        "lenovo" in normalized -> R.drawable.ic_brand_lenovo
        "sharp" in normalized -> R.drawable.ic_brand_sharp
        "microsoft" in normalized || "surface" in normalized -> R.drawable.ic_brand_ms
        "htc" in normalized -> R.drawable.ic_brand_htc
        "razer" in normalized -> R.drawable.ic_brand_razer
        "fairphone" in normalized -> R.drawable.ic_brand_fairphone
        "nothing" in normalized -> R.drawable.ic_brand_nothing
        "panasonic" in normalized -> R.drawable.ic_brand_panasonic
        "fujitsu" in normalized -> R.drawable.ic_brand_fujitsu
        "kyocera" in normalized -> R.drawable.ic_brand_kyocera
        "blackberry" in normalized -> R.drawable.ic_brand_blackberry
        "acer" in normalized -> R.drawable.ic_brand_acer
        "alcatel" in normalized -> R.drawable.ic_brand_alcatel
        "tcl" in normalized -> R.drawable.ic_brand_tcl
        "unihertz" in normalized -> R.drawable.ic_brand_unihertz

        // Smaller / regional brands
        "umidigi" in normalized -> R.drawable.ic_brand_umidigi
        "ulefone" in normalized -> R.drawable.ic_brand_ulefone
        "doogee" in normalized -> R.drawable.ic_brand_doogee
        "cubot" in normalized -> R.drawable.ic_brand_cubot
        "oukitel" in normalized -> R.drawable.ic_brand_oukitel
        "blackview" in normalized -> R.drawable.ic_brand_blackview
        "elephone" in normalized -> R.drawable.ic_brand_elephone
        "leagoo" in normalized -> R.drawable.ic_brand_leagoo
        "blu" in normalized -> R.drawable.ic_brand_blu
        "micromax" in normalized -> R.drawable.ic_brand_micromax
        "wileyfox" in normalized -> R.drawable.ic_brand_wileyfox
        "vsmart" in normalized -> R.drawable.ic_brand_vsmart
        "wingtech" in normalized -> R.drawable.ic_brand_wingtech
        "condor" in normalized -> R.drawable.ic_brand_condor
        "orbic" in normalized -> R.drawable.ic_brand_orbic
        "mobicel" in normalized -> R.drawable.ic_brand_mobicel
        "moxee" in normalized -> R.drawable.ic_brand_moxee
        "cricket" in normalized -> R.drawable.ic_brand_cricket
        "maxwest" in normalized -> R.drawable.ic_brand_maxwest
        "general mobile" in normalized || "general_mobile" in normalized ->
            R.drawable.ic_brand_general_mobile
        "omix" in normalized -> R.drawable.ic_brand_omix
        "oteeto" in normalized -> R.drawable.ic_brand_oteeto
        "vgo" in normalized -> R.drawable.ic_brand_vgo_tel
        "shift" in normalized -> R.drawable.ic_brand_shift
        else -> null
    }

    if (logoRes != null) {
        Image(
            painter = painterResource(logoRes),
            contentDescription = brand,
            modifier = modifier.size(size),
        )
    } else {
        // Fallback: colored text badge
        val label = brand.take(6)
        val fontSize = when {
            label.length >= 6 -> (size.value * 0.20f).sp
            label.length >= 4 -> (size.value * 0.24f).sp
            else -> (size.value * 0.30f).sp
        }
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF607D8B)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = TextStyle(
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
            )
        }
    }
}

@Composable
private fun VendorBadge(label: String, color: Color, size: Dp, modifier: Modifier) {
    val fontSize = when {
        label.length >= 5 -> (size.value * 0.22f).sp
        label.length >= 3 -> (size.value * 0.26f).sp
        else -> (size.value * 0.32f).sp
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            ),
        )
    }
}
