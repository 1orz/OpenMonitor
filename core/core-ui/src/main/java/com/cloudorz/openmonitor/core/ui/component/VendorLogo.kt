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
 * Displays a colored brand badge for a device manufacturer (OEM).
 * Uses distinctive brand colors with the brand name as text.
 */
@Composable
fun DeviceBrandBadge(
    brand: String,
    modifier: Modifier = Modifier,
) {
    if (brand.isBlank()) return
    val normalized = brand.lowercase()
    val (label, color) = when {
        "oneplus" in normalized -> "1+" to Color(0xFFEB0029)
        "oppo" in normalized -> "OPPO" to Color(0xFF1D9B4E)
        "realme" in normalized -> "realme" to Color(0xFFFFBF00)
        "vivo" in normalized -> "vivo" to Color(0xFF415FFF)
        "xiaomi" in normalized || "redmi" in normalized || "poco" in normalized -> "MI" to Color(0xFFFF6900)
        "samsung" in normalized -> "Samsung" to Color(0xFF1428A0)
        "huawei" in normalized || "honor" in normalized -> "HUAWEI" to Color(0xFFCF0A2C)
        "google" in normalized -> "Google" to Color(0xFF4285F4)
        "sony" in normalized -> "Sony" to Color(0xFF000000)
        "motorola" in normalized || "moto" in normalized -> "moto" to Color(0xFF5C2F91)
        "asus" in normalized -> "ASUS" to Color(0xFF00539B)
        "nokia" in normalized -> "Nokia" to Color(0xFF005AFF)
        "lg" in normalized -> "LG" to Color(0xFFA50034)
        "lenovo" in normalized -> "Lenovo" to Color(0xFFE2231A)
        "meizu" in normalized -> "Meizu" to Color(0xFF0052FF)
        "nothing" in normalized -> "Nothing" to Color(0xFF000000)
        else -> brand.take(6) to Color(0xFF607D8B)
    }
    val fontSize = when {
        label.length >= 6 -> 10.sp
        label.length >= 4 -> 11.sp
        else -> 13.sp
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 7.dp, vertical = 3.dp),
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
