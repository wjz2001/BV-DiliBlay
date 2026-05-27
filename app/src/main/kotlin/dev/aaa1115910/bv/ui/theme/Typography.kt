package dev.aaa1115910.bv.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Typography
import dev.aaa1115910.bv.R

//private val notoMathFont = Font(R.font.noto_sans_math_regular, FontWeight.Normal)
//private val notoFontFamily = FontFamily(notoMathFont)

private val sourceHanSansFontFamily = FontFamily(
    Font(R.font.siyuan_regular, FontWeight.Normal),
    Font(R.font.siyuan_bold, FontWeight.Bold)
)

private val dummyTypography = Typography()

val typographyTv = Typography(
    displayLarge = dummyTypography.displayLarge.copy(fontFamily = sourceHanSansFontFamily),
    displayMedium = dummyTypography.displayMedium.copy(fontFamily = sourceHanSansFontFamily),
    displaySmall = dummyTypography.displaySmall.copy(fontFamily = sourceHanSansFontFamily),
    headlineLarge = dummyTypography.headlineLarge.copy(fontFamily = sourceHanSansFontFamily),
    headlineMedium = dummyTypography.headlineMedium.copy(fontFamily = sourceHanSansFontFamily),
    headlineSmall = dummyTypography.headlineSmall.copy(fontFamily = sourceHanSansFontFamily),
    titleLarge = dummyTypography.titleLarge.copy(fontFamily = sourceHanSansFontFamily),
    titleMedium = dummyTypography.titleMedium.copy(fontFamily = sourceHanSansFontFamily),
    titleSmall = dummyTypography.titleSmall.copy(fontFamily = sourceHanSansFontFamily),
    bodyLarge = dummyTypography.bodyLarge.copy(fontFamily = sourceHanSansFontFamily),
    bodyMedium = dummyTypography.bodyMedium.copy(fontFamily = sourceHanSansFontFamily),
    bodySmall = dummyTypography.bodySmall.copy(fontFamily = sourceHanSansFontFamily),
    labelLarge = dummyTypography.labelLarge.copy(fontFamily = sourceHanSansFontFamily),
    labelMedium = dummyTypography.labelMedium.copy(fontFamily = sourceHanSansFontFamily),
    labelSmall = dummyTypography.labelSmall.copy(fontFamily = sourceHanSansFontFamily)
)

val typographyCommon = androidx.compose.material3.Typography(
    displayLarge = dummyTypography.displayLarge.copy(fontFamily = sourceHanSansFontFamily),
    displayMedium = dummyTypography.displayMedium.copy(fontFamily = sourceHanSansFontFamily),
    displaySmall = dummyTypography.displaySmall.copy(fontFamily = sourceHanSansFontFamily),
    headlineLarge = dummyTypography.headlineLarge.copy(fontFamily = sourceHanSansFontFamily),
    headlineMedium = dummyTypography.headlineMedium.copy(fontFamily = sourceHanSansFontFamily),
    headlineSmall = dummyTypography.headlineSmall.copy(fontFamily = sourceHanSansFontFamily),
    titleLarge = dummyTypography.titleLarge.copy(fontFamily = sourceHanSansFontFamily),
    titleMedium = dummyTypography.titleMedium.copy(fontFamily = sourceHanSansFontFamily),
    titleSmall = dummyTypography.titleSmall.copy(fontFamily = sourceHanSansFontFamily),
    bodyLarge = dummyTypography.bodyLarge.copy(fontFamily = sourceHanSansFontFamily),
    bodyMedium = dummyTypography.bodyMedium.copy(fontFamily = sourceHanSansFontFamily),
    bodySmall = dummyTypography.bodySmall.copy(fontFamily = sourceHanSansFontFamily),
    labelLarge = dummyTypography.labelLarge.copy(fontFamily = sourceHanSansFontFamily),
    labelMedium = dummyTypography.labelMedium.copy(fontFamily = sourceHanSansFontFamily),
    labelSmall = dummyTypography.labelSmall.copy(fontFamily = sourceHanSansFontFamily)
)