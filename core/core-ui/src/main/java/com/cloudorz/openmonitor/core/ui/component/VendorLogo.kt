package com.cloudorz.openmonitor.core.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
 * Qualcomm/Snapdragon uses the bundled PNG; all other vendors
 * render a colored badge with an abbreviated label.
 */
@Composable
fun VendorLogo(
    vendor: String,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier,
) {
    val normalized = vendor.lowercase()
    when {
        "qualcomm" in normalized || "snapdragon" in normalized ->
            Image(
                painter = painterResource(R.drawable.ic_snapdragon),
                contentDescription = vendor,
                modifier = modifier.size(size),
            )
        "mediatek" in normalized ->
            VendorBadge("MTK", Color(0xFF0052CC), size, modifier)
        "samsung" in normalized || "exynos" in normalized ->
            VendorBadge("EXY", Color(0xFF1428A0), size, modifier)
        "hisilicon" in normalized || "kirin" in normalized ->
            VendorBadge("KIRIN", Color(0xFF221E1F), size, modifier)
        "google" in normalized || "tensor" in normalized ->
            VendorBadge("G", Color(0xFF4285F4), size, modifier)
        "unisoc" in normalized ->
            VendorBadge("UNISOC", Color(0xFF00539B), size, modifier)
        "rockchip" in normalized ->
            VendorBadge("RK", Color(0xFF0071BC), size, modifier)
        "xiaomi" in normalized || "xring" in normalized ->
            VendorBadge("MI", Color(0xFFFF6900), size, modifier)
        "intel" in normalized ->
            VendorBadge("Intel", Color(0xFF0071C5), size, modifier)
        "nvidia" in normalized ->
            VendorBadge("NV", Color(0xFF76B900), size, modifier)
        "allwinner" in normalized ->
            VendorBadge("AW", Color(0xFF0084C8), size, modifier)
        "amlogic" in normalized ->
            VendorBadge("AML", Color(0xFFE2231A), size, modifier)
        "marvell" in normalized ->
            VendorBadge("MRV", Color(0xFF004B8D), size, modifier)
        "amd" in normalized ->
            VendorBadge("AMD", Color(0xFFED1C24), size, modifier)
    }
}

/**
 * Displays a device OEM brand logo using extracted PNG assets.
 * Falls back to a colored text badge for unknown brands.
 * Sized to [size] (default 56dp) to be visually prominent, like DevCheck's device card.
 */
@Composable
fun DeviceBrandLogo(
    brand: String,
    size: Dp = 56.dp,
    modifier: Modifier = Modifier,
) {
    if (brand.isBlank()) return
    val normalized = brand.lowercase()
    val logoRes: Int? = when {
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
        "google" in normalized || "pixel" in normalized -> R.drawable.ic_brand_google
        "sony" in normalized -> R.drawable.ic_brand_sony
        "motorola" in normalized || "moto" in normalized -> R.drawable.ic_brand_moto
        "asus" in normalized -> R.drawable.ic_brand_asus
        "nokia" in normalized -> R.drawable.ic_brand_nokia
        "lg" in normalized -> R.drawable.ic_brand_lg
        "lenovo" in normalized -> R.drawable.ic_brand_lenovo
        "meizu" in normalized -> R.drawable.ic_brand_meizu
        "nubia" in normalized -> R.drawable.ic_brand_nubia
        "blackshark" in normalized || "black shark" in normalized -> R.drawable.ic_brand_blackshark
        "sharp" in normalized -> R.drawable.ic_brand_sharp
        "microsoft" in normalized || "surface" in normalized -> R.drawable.ic_brand_ms
        "smartisan" in normalized -> R.drawable.ic_brand_smartisan
        "zte" in normalized -> R.drawable.ic_brand_zte
        "hisense" in normalized -> R.drawable.ic_brand_hisense
        "coolpad" in normalized -> R.drawable.ic_brand_coolpad
        "unihertz" in normalized -> R.drawable.ic_brand_unihertz
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
        val (label, color) = when {
            "nothing" in normalized -> "Nothing" to Color(0xFF1C1B1B)
            "infinix" in normalized -> "Infinix" to Color(0xFF0071CE)
            "tecno" in normalized -> "TECNO" to Color(0xFF0A2463)
            "wiko" in normalized -> "Wiko" to Color(0xFFE50049)
            else -> brand.take(6) to Color(0xFF607D8B)
        }
        val fontSize = when {
            label.length >= 6 -> (size.value * 0.20f).sp
            label.length >= 4 -> (size.value * 0.24f).sp
            else -> (size.value * 0.30f).sp
        }
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
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
