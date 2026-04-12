package dev.aaa1115910.bv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ColorScheme as CommonColorScheme
import androidx.compose.material3.MaterialTheme as CommonMaterialTheme
import androidx.compose.material3.darkColorScheme as commonDarkColorScheme
import androidx.compose.material3.lightColorScheme as commonLightColorScheme
import androidx.tv.material3.ColorScheme as TvColorScheme
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme
import androidx.tv.material3.lightColorScheme as tvLightColorScheme

// 基础色
val AppBlack = Color(0xFF000000)
val AppWhite = Color(0xFFEFEDE8) // Tone 94 暖白
val AppGray = Color(0xFF787774) // Tone 50 暖灰
val AppRed = Color(0xFFFF0000) // Tone 50 纯红
val AppYellow = Color(0xFFD9A000) // Tone 50 暖金黄色

// 浅色模式中性色
// 对应 M3 Tone 98：极为干净的暖白背景，比你的 AppWhite 略亮，作为最底层的画板
val LightSurface = Color(0xFFFCF8F2)
// 对应 M3 Tone 90：用于卡片或非突出元素的背景，与 Surface 形成微妙区分
val LightSurfaceVariant = Color(0xFFEBE5DA)
// 对应 M3 Tone 60：加深了边框色，确保在浅色背景上清晰可见，不发虚
val LightOutline = Color(0xFFAFA89D)
// 对应 M3 Tone 80：较弱的浅色边框，用于浅色模式的 outlineVariant
val LightOutlineVariant = Color(0xFFD1CBC0)

// 深色模式中性色
// 对应 M3 Tone 6：极深的暖黑褐色，避免纯黑的死板，视觉更高级护眼
val DarkSurface = Color(0xFF151311)
// 对应 M3 Tone 30：让深色模式的卡片有浮雕感
val DarkSurfaceVariant = Color(0xFF4A463F)
// 对应 M3 Tone 80：确保深色环境下的阅读性
val DarkOnSurfaceVariant = Color(0xFFCCC6BC)
// 对应 M3 Tone 30：较暗的深色边框，用于深色模式的 outlineVariant
val DarkOutlineVariant = Color(0xFF4A463F)

// 反转色及变体系列
// 对应 M3 Tone 20：极深的暖灰，浅色模式的反转表面与深色模式的反转文字
val AppGrayDark = Color(0xFF322F2C)
// 对应 M3 Tone 95：极柔和的暖白，深色模式的反转表面与浅色模式的反转文字
val AppWhiteLight = Color(0xFFF5F2EC)

// 红色衍生系列
// 对应 M3 Tone 30：深红，与 AppRed 对比鲜明
val AppRedDark = Color(0xFF93000A)
// 对应 M3 Tone 10：极暗红，常用于极强对比度的场景
val AppRedDarker = Color(0xFF410002)

// 对应 M3 Tone 60：明亮鲜艳的红，在深色模式下代替纯红使用，不刺眼
val AppRedLight = Color(0xFFFF5449)
// 对应 M3 Tone 80：柔和的浅红，保留红色的视觉特征，不做无意义的泛白
val AppRedLighter = Color(0xFFFFB4AB)

// 黄色衍生系列
// 对应 M3 Tone 30：深金黄/棕黄，用于深色模式的容器底色
val AppYellowDark = Color(0xFF996B00)
// 对应 M3 Tone 10：极暗的黑褐色，用于浅色模式的容器文字，保证最高可读性
val AppYellowDarker = Color(0xFF4D3300)

// 对应 M3 Tone 80：明亮的琥珀黄，在深色模式下作为 error，十分醒目但不刺眼
val AppYellowLight = Color(0xFFFFD15C)
// 对应 M3 Tone 90：极柔和的浅黄，作为浅色模式的 errorContainer 错误背景
val AppYellowLighter = Color(0xFFFFF0C4)

@Immutable
data class BvThemeTokens(
    val isDark: Boolean, // 是否为深色主题（用于选择 darkColorScheme / lightColorScheme）

    // primary：主色（最强调/最常用的品牌色）
    val primary: Color, // 主色：按钮、选中态、高亮等常用强调色
    val onPrimary: Color, // 主色上的内容颜色：放在 primary 背景上的文字/图标颜色

    // primaryContainer：主色容器（比 primary 更“背景化”的承载色）
    val primaryContainer: Color, // 主色容器：强调区域的背景色（不如 primary 那么“刺眼”）
    val onPrimaryContainer: Color, // 主色容器上的内容颜色：放在 primaryContainer 上的文字/图标颜色

    // secondary：次强调色（通常用于辅助强调、次级按钮等）
    val secondary: Color, // 次色：辅助强调色（次级按钮、芯片等）
    val onSecondary: Color, // 次色上的内容颜色：放在 secondary 背景上的文字/图标颜色
    val secondaryContainer: Color, // 次色容器：secondary 的“容器/背景”版本
    val onSecondaryContainer: Color, // 次色容器上的内容颜色：放在 secondaryContainer 上的文字/图标颜色

    // tertiary：第三强调色（当 primary/secondary 不够区分时使用）
    val tertiary: Color, // 第三色：第三套强调色（更少用，用于补充区分）
    val onTertiary: Color, // 第三色上的内容颜色：放在 tertiary 背景上的文字/图标颜色
    val tertiaryContainer: Color, // 第三色容器：tertiary 的“容器/背景”版本
    val onTertiaryContainer: Color, // 第三色容器上的内容颜色：放在 tertiaryContainer 上的文字/图标颜色

    // background：应用整体背景（页面最底层背景）
    val background: Color, // 背景色：屏幕/页面最底层背景
    val onBackground: Color, // 背景上的内容颜色：放在 background 上的文字/图标颜色

    // surface：表面色（卡片、弹层、控件表面等“承载内容的面”）
    val surface: Color, // 表面色：卡片、弹窗、列表项等控件的底色
    val onSurface: Color, // 表面上的内容颜色：放在 surface 上的文字/图标颜色

    // surfaceVariant：表面变体（用于区分层级/分组/弱化的表面）
    val surfaceVariant: Color, // 表面变体：与 surface 同类但用于分层/分组的底色
    val onSurfaceVariant: Color, // 表面变体上的内容颜色：放在 surfaceVariant 上的文字/图标颜色

    // inverseSurface：反转表面（需要在“surface上叠一层高对比surface”的场景）
    val inverseSurface: Color, // 反转表面：例如Snackbar等的强调容器
    val inverseOnSurface: Color, // 反转表面上的内容色：放在 inverseSurface 上的颜色

    // surfaceTint：表面着色（与 tonal elevation 相关）
    val surfaceTint: Color, // 表面着色：M3中常随 elevation 改变的表面叠色，通常和 primary 相同

    // outline：轮廓/描边/分割线
    val outline: Color, // 轮廓色：边框、分割线、描边等
    val outlineVariant: Color, // 轮廓色变体：弱化的边框色，现在推荐按钮边框等场景用此字段

    // error：错误相关
    val error: Color, // 错误色：错误提示、危险操作强调等
    val onError: Color, // 错误色上的内容颜色：放在 error 背景上的文字/图标颜色
    val errorContainer: Color, // 错误容器：错误提示区域/卡片的背景色
    val onErrorContainer: Color, // 错误容器上的内容颜色：放在 errorContainer 上的文字/图标颜色

    // inversePrimary：反色场景下的主色（用于深浅反转的场景）
    val inversePrimary: Color, // 反转主色：用于“反色/对比”场景下的 primary（如 inverseSurface 上的强调）

    // scrim：遮罩色
    val scrim: Color, // 遮罩蒙层颜色（如对话框、抽屉背后的底色、图片信息的渐变底色）

    // 额外颜色：不属于 Material ColorScheme 标准字段，但业务/组件需要
    val extras: AppThemeColorExtras // 自定义扩展颜色集合（焦点环、选中边框、遮罩等）
) {
    // 让 extras 也能通过 C 直接取：C.focusRing / C.posterOverlay ...
    val focusRing: Color get() = extras.focusRing
    val selectedBorder: Color get() = extras.selectedBorder
    val railBackground: Color get() = extras.railBackground
    val posterOverlay: Color get() = extras.posterOverlay
    val onScrim: Color get() = extras.onScrim
    val disabled: Color get() = extras.disabled
    val commentsBackground: Color get() = extras.commentsBackground
    val bilibili: Color get() = extras.bilibili
    val mentionAndLink: Color get() = extras.mentionAndLink
}

@Immutable
data class AppThemeColorExtras(
    val focusRing: Color, // 焦点高亮环颜色
    val selectedBorder: Color, // 选中态边框颜色
    val railBackground: Color, // 侧边导轨/菜单背景色
    val posterOverlay: Color, // 海报类封面上的遮罩色
    val onScrim: Color, // 显示在图片信息蒙层上的文字/图标颜色
    val disabled: Color, // 禁用颜色
    val commentsBackground: Color,
    val bilibili: Color,
    val mentionAndLink: Color

)

val BvDarkThemeTokens = BvThemeTokens(
    isDark = true,
    primary = AppRedLight,
    onPrimary = AppBlack,
    primaryContainer = AppRedDark,
    onPrimaryContainer = AppWhite,
    secondary = AppRedLighter,
    onSecondary = AppBlack,
    secondaryContainer = AppRedDarker,
    onSecondaryContainer = AppRedLighter,
    tertiary = AppRedLight,
    onTertiary = AppBlack,
    tertiaryContainer = AppRedDarker,
    onTertiaryContainer = AppWhite,
    background = AppBlack,
    onBackground = AppWhite,
    surface = DarkSurface,
    onSurface = AppWhite,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    inverseSurface = AppWhiteLight, // 在深色模式中反转表面使用浅色
    inverseOnSurface = AppGrayDark, // 浅色反转背景上的字使用深色
    surfaceTint = AppRedLight,      // 通常表面叠色采用 primary 一致的色调
    outline = AppGray,
    outlineVariant = DarkOutlineVariant,
    error = AppYellowLight,
    onError = AppBlack,
    errorContainer = AppYellowDark,
    onErrorContainer = AppYellowLighter,
    inversePrimary = AppRed,
    scrim = AppBlack.copy(alpha = 0.7f), // M3中标准用来做遮盖或底层渐变遮罩
    extras = AppThemeColorExtras(
        focusRing = AppRedLight, // 焦点高亮环颜色（TV 遥控器/键盘导航聚焦态）
        selectedBorder = AppWhite, // 选中态边框颜色（如卡片/列表项被选中时的描边）
        railBackground = AppWhite.copy(alpha = 0.05f), // 侧边导轨/菜单背景色（弱化透明底）
        posterOverlay = AppBlack.copy(alpha = 0.7f), // 海报/封面上的遮罩色（用于压暗图片以保证文字可读）
        onScrim = AppWhite, // 显示在图片信息蒙层上的文字/图标颜色
        disabled = AppGray.copy(alpha = 0.4f), // 禁用颜色
        commentsBackground = Color(0xFFFBFBF4),
        bilibili = Color(0xFFFE7297),
        mentionAndLink = Color(0xFF008DC3)
    )
)

val BvLightThemeTokens = BvThemeTokens(
    isDark = false,
    primary = AppRed,
    onPrimary = AppWhite,
    primaryContainer = AppRedLighter,
    onPrimaryContainer = AppRedDarker,
    secondary = AppRedDark,
    onSecondary = AppWhite,
    secondaryContainer = AppRedLighter,
    onSecondaryContainer = AppRedDarker,
    tertiary = AppRedDark,
    onTertiary = AppWhite,
    tertiaryContainer = AppRedLight,
    onTertiaryContainer = AppBlack,
    background = AppWhite,
    onBackground = AppBlack,
    surface = LightSurface,
    onSurface = AppBlack,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = AppGray,
    inverseSurface = AppGrayDark, // 浅色模式中的反转表面使用深色
    inverseOnSurface = AppWhiteLight, // 深色反转背景上的文字使用浅色
    surfaceTint = AppRed,         // 采用浅色模式 primary
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = AppYellow,
    onError = AppBlack,
    errorContainer = AppYellowLighter,
    onErrorContainer = AppYellowDarker,
    inversePrimary = AppRedLight,
    scrim = AppBlack.copy(alpha = 0.7f), // 用作全局蒙层与图片信息底色遮盖
    extras = AppThemeColorExtras(
        focusRing = AppRed, // 焦点高亮环颜色（TV 遥控器/键盘导航聚焦态）
        selectedBorder = AppBlack, // 选中态边框颜色（如卡片/列表项被选中时的描边）
        railBackground = AppBlack.copy(alpha = 0.05f), // 侧边导轨/菜单背景色（弱化透明底）
        posterOverlay = AppBlack.copy(alpha = 0.7f), // 海报/封面上的遮罩色（用于压暗图片以保证文字可读）
        onScrim = AppWhite, // 显示在图片信息蒙层上的文字/图标颜色
        disabled = AppGray.copy(alpha = 0.6f), // 禁用颜色
        commentsBackground = Color(0xFFFBFBF4),
        bilibili = Color(0xFFFE7297),
        mentionAndLink = Color(0xFF008DC3)
    )
)

// 把基础颜色映射到 Material Design 3 的语义角色上
val ThemeMode.themeTokens: BvThemeTokens
    get() = when (this) {
        ThemeMode.LIGHT -> BvLightThemeTokens
        ThemeMode.DARK -> BvDarkThemeTokens
    }

// 将自定义的 BvThemeTokens 转换为 androidx.compose.material3.ColorScheme
fun BvThemeTokens.toCommonColorScheme(): CommonColorScheme =
    if (isDark) {
        commonDarkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            surfaceTint = surfaceTint,
            outline = outline,
            outlineVariant = outlineVariant,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            inversePrimary = inversePrimary,
            scrim = scrim
        )
    } else {
        commonLightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            surfaceTint = surfaceTint,
            outline = outline,
            outlineVariant = outlineVariant,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            inversePrimary = inversePrimary,
            scrim = scrim
        )
    }

// 将自定义的 BvThemeTokens 转换为 androidx.tv.material3.ColorScheme
fun BvThemeTokens.toTvColorScheme(): TvColorScheme =
    if (isDark) {
        tvDarkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            surfaceTint = surfaceTint,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            border = outline,
            borderVariant = outlineVariant,
            scrim = scrim
        )
    } else {
        tvLightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            surfaceTint = surfaceTint,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            border = outline,
            borderVariant = outlineVariant,
            scrim = scrim
        )
    }

// 全量 tokens（用 C.primary / C.surface ...）
val LocalBvThemeTokens = staticCompositionLocalOf { BvDarkThemeTokens }
val C: BvThemeTokens
    @Composable get() = LocalBvThemeTokens.current

//提供一个 Theme 入口：同时喂 Common + TV，且提供 C/EC，这样就能在同一棵树里混用 phone/tv 组件，并且 C/EC 一定是正确的

@Composable
fun BvTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit
) {
    val tokens = mode.themeTokens

    CompositionLocalProvider(LocalBvThemeTokens provides tokens) {
        CommonMaterialTheme(colorScheme = tokens.toCommonColorScheme()) {
            TvMaterialTheme(colorScheme = tokens.toTvColorScheme()) {
                content()
            }
        }
    }
}