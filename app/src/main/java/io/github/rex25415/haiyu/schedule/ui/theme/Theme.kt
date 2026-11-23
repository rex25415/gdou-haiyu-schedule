package io.github.rex25415.haiyu.schedule.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import io.github.rex25415.haiyu.schedule.model.AppThemePalette
import io.github.rex25415.haiyu.schedule.model.DarkModeOption

// 1. 海屿薄荷青（默认主主题）
private val MintLightScheme = lightColorScheme(
    primary = Color(0xFF0C7D72),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC5F4EC),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF3E665F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5EFE9),
    onSecondaryContainer = Color(0xFF09201C),
    tertiary = Color(0xFF286684),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCBE8FA),
    onTertiaryContainer = Color(0xFF001E2D),
    background = Color(0xFFF4FAF8),
    onBackground = Color(0xFF141E1C),
    surface = Color(0xFFF4FAF8),
    onSurface = Color(0xFF141E1C),
    surfaceVariant = Color(0xFFE0ECE8),
    onSurfaceVariant = Color(0xFF3F4E4B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF6F3),
    surfaceContainer = Color(0xFFE6F0EC),
    surfaceContainerHigh = Color(0xFFDDE9E5),
    outline = Color(0xFF6F7F7B),
    outlineVariant = Color(0xFFC1D2CD),
    error = Color(0xFFBA1A1A)
)

private val MintDarkScheme = darkColorScheme(
    primary = Color(0xFF5CDCC8),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFFB3F3E8),
    secondary = Color(0xFFA9CFC6),
    onSecondary = Color(0xFF133630),
    secondaryContainer = Color(0xFF294D46),
    onSecondaryContainer = Color(0xFFC6ECE3),
    tertiary = Color(0xFF9ACCE8),
    onTertiary = Color(0xFF00344B),
    tertiaryContainer = Color(0xFF0B4C6A),
    onTertiaryContainer = Color(0xFFCBE8FA),
    background = Color(0xFF0E1514),
    onBackground = Color(0xFFDFE8E5),
    surface = Color(0xFF0E1514),
    onSurface = Color(0xFFDFE8E5),
    surfaceVariant = Color(0xFF253230),
    onSurfaceVariant = Color(0xFFB3C4BF),
    surfaceContainerLowest = Color(0xFF0A100F),
    surfaceContainerLow = Color(0xFF141D1B),
    surfaceContainer = Color(0xFF192422),
    surfaceContainerHigh = Color(0xFF212E2B),
    outline = Color(0xFF7B8E89),
    outlineVariant = Color(0xFF344542),
    error = Color(0xFFFFB4AB)
)

// 2. 深海蔚蓝
private val OceanLightScheme = lightColorScheme(
    primary = Color(0xFF0E64B5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD2E6FF),
    onPrimaryContainer = Color(0xFF001C3B),
    secondary = Color(0xFF4C607A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E4F7),
    onSecondaryContainer = Color(0xFF091D34),
    tertiary = Color(0xFF0D7D7B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC4F2F0),
    onTertiaryContainer = Color(0xFF00201F),
    background = Color(0xFFF5F8FD),
    onBackground = Color(0xFF151C24),
    surface = Color(0xFFF5F8FD),
    onSurface = Color(0xFF151C24),
    surfaceVariant = Color(0xFFDFE6F0),
    onSurfaceVariant = Color(0xFF424B57),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF3FA),
    surfaceContainer = Color(0xFFE6EDF6),
    surfaceContainerHigh = Color(0xFFDCE5F0),
    outline = Color(0xFF717C8A),
    outlineVariant = Color(0xFFC3CDD9)
)

private val OceanDarkScheme = darkColorScheme(
    primary = Color(0xFF96C7FF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF004889),
    onPrimaryContainer = Color(0xFFD2E6FF),
    secondary = Color(0xFFB5C7E0),
    onSecondary = Color(0xFF1F3149),
    secondaryContainer = Color(0xFF354861),
    onSecondaryContainer = Color(0xFFD6E4F7),
    tertiary = Color(0xFF6CD6D3),
    onTertiary = Color(0xFF003736),
    background = Color(0xFF0E141B),
    onBackground = Color(0xFFE0E6EF),
    surface = Color(0xFF0E141B),
    onSurface = Color(0xFFE0E6EF),
    surfaceVariant = Color(0xFF242E3A),
    onSurfaceVariant = Color(0xFFB6C2D1),
    surfaceContainerLowest = Color(0xFF090E14),
    surfaceContainerLow = Color(0xFF141C25),
    surfaceContainer = Color(0xFF19222C),
    surfaceContainerHigh = Color(0xFF212B37),
    outline = Color(0xFF7F8B9A),
    outlineVariant = Color(0xFF35414F)
)

// 3. 海滨日落琥珀
private val AmberLightScheme = lightColorScheme(
    primary = Color(0xFFB8541A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBC9),
    onPrimaryContainer = Color(0xFF371100),
    secondary = Color(0xFF755748),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFADCC9),
    onSecondaryContainer = Color(0xFF2B160B),
    tertiary = Color(0xFF186C6A),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFCF8F5),
    onBackground = Color(0xFF221A16),
    surface = Color(0xFFFCF8F5),
    onSurface = Color(0xFF221A16),
    surfaceVariant = Color(0xFFF0E4DC),
    onSurfaceVariant = Color(0xFF53443C),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F0EB),
    surfaceContainer = Color(0xFFF1E8E2),
    surfaceContainerHigh = Color(0xFFEADFD7),
    outline = Color(0xFF84746B),
    outlineVariant = Color(0xFFD6C6BC)
)

private val AmberDarkScheme = darkColorScheme(
    primary = Color(0xFFFFB58C),
    onPrimary = Color(0xFF5A2000),
    primaryContainer = Color(0xFF833304),
    onPrimaryContainer = Color(0xFFFFDBC9),
    secondary = Color(0xFFE3BFA9),
    onSecondary = Color(0xFF422A1D),
    secondaryContainer = Color(0xFF5B4032),
    onSecondaryContainer = Color(0xFFFADCC9),
    tertiary = Color(0xFF7AD6D2),
    onTertiary = Color(0xFF003735),
    background = Color(0xFF18120F),
    onBackground = Color(0xFFEDE0D9),
    surface = Color(0xFF18120F),
    onSurface = Color(0xFFEDE0D9),
    surfaceVariant = Color(0xFF352A24),
    onSurfaceVariant = Color(0xFFD2C2B8),
    surfaceContainerLowest = Color(0xFF120D0A),
    surfaceContainerLow = Color(0xFF201814),
    surfaceContainer = Color(0xFF271E19),
    surfaceContainerHigh = Color(0xFF312721),
    outline = Color(0xFF9A8B82),
    outlineVariant = Color(0xFF4A3D36)
)

data class CourseCardPalette(
    val container: Color,
    val accent: Color,
    val onContainer: Color
)

private val LightCourseColors = listOf(
    CourseCardPalette(Color(0xFFD4F5EC), Color(0xFF0E8A7D), Color(0xFF0A433D)), // 薄荷青
    CourseCardPalette(Color(0xFFD9ECFF), Color(0xFF1E78D6), Color(0xFF0E3866)), // 海浪蓝
    CourseCardPalette(Color(0xFFFFE5D4), Color(0xFFD86826), Color(0xFF5E2B0C)), // 珊瑚橙
    CourseCardPalette(Color(0xFFE8E2FF), Color(0xFF6B52D1), Color(0xFF2C1E63)), // 薰衣紫
    CourseCardPalette(Color(0xFFDDF4D8), Color(0xFF378F3E), Color(0xFF16471A)), // 椰林绿
    CourseCardPalette(Color(0xFFFFE1EA), Color(0xFFD44374), Color(0xFF5E1630)), // 晚霞粉
    CourseCardPalette(Color(0xFFFFF1CE), Color(0xFFC2870E), Color(0xFF543903)), // 沙滩金
    CourseCardPalette(Color(0xFFD6F1F8), Color(0xFF1485A3), Color(0xFF083C4A)), // 浅湾蓝
    CourseCardPalette(Color(0xFFF3E2FA), Color(0xFF9C47BA), Color(0xFF471957)), // 丁香紫
    CourseCardPalette(Color(0xFFE4F0D3), Color(0xFF638C28), Color(0xFF2B3F0E)), // 橄榄青
    CourseCardPalette(Color(0xFFFFE3DF), Color(0xFFC94A3D), Color(0xFF5B1B15)), // 绯红
    CourseCardPalette(Color(0xFFE0EBFA), Color(0xFF3E6BB5), Color(0xFF19325C))  // 晴空蓝
)

private val DarkCourseColors = listOf(
    CourseCardPalette(Color(0xFF123B36), Color(0xFF56DEC9), Color(0xFFC8F7EF)),
    CourseCardPalette(Color(0xFF143152), Color(0xFF74B9FF), Color(0xFFD4E9FF)),
    CourseCardPalette(Color(0xFF4A2816), Color(0xFFFFA36C), Color(0xFFFFE3D1)),
    CourseCardPalette(Color(0xFF2D2354), Color(0xFFB5A4FF), Color(0xFFE6E0FF)),
    CourseCardPalette(Color(0xFF1B3C1F), Color(0xFF7CD982), Color(0xFFD8F5DA)),
    CourseCardPalette(Color(0xFF4A1B2E), Color(0xFFFF88B0), Color(0xFFFFDCE8)),
    CourseCardPalette(Color(0xFF45340E), Color(0xFFF7C95C), Color(0xFFFFEFC7)),
    CourseCardPalette(Color(0xFF113945), Color(0xFF66D1EC), Color(0xFFD1F2FA)),
    CourseCardPalette(Color(0xFF3C1C4A), Color(0xFFDA9AF2), Color(0xFFF2DEFA)),
    CourseCardPalette(Color(0xFF2B3B16), Color(0xFFAED96A), Color(0xFFE4F2CE)),
    CourseCardPalette(Color(0xFF4A1F1B), Color(0xFFFF9185), Color(0xFFFFDFDC)),
    CourseCardPalette(Color(0xFF1C2E4D), Color(0xFF8EB5F5), Color(0xFFDDEAFF))
)

fun coursePaletteFor(index: Int, isDark: Boolean): CourseCardPalette {
    val list = if (isDark) DarkCourseColors else LightCourseColors
    val safeIndex = ((index % list.size) + list.size) % list.size
    return list[safeIndex]
}

private val HaiyuTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp)
)

@Composable
fun HaiyuTheme(
    palette: AppThemePalette,
    darkModeOption: DarkModeOption,
    content: @Composable (isDark: Boolean) -> Unit
) {
    val isDark = when (darkModeOption) {
        DarkModeOption.SYSTEM -> isSystemInDarkTheme()
        DarkModeOption.LIGHT -> false
        DarkModeOption.DARK -> true
    }

    val colorScheme = when (palette) {
        AppThemePalette.HAIYU_MINT -> if (isDark) MintDarkScheme else MintLightScheme
        AppThemePalette.DEEP_OCEAN -> if (isDark) OceanDarkScheme else OceanLightScheme
        AppThemePalette.SUNSET_AMBER -> if (isDark) AmberDarkScheme else AmberLightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !isDark
                    isAppearanceLightNavigationBars = !isDark
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HaiyuTypography
    ) {
        content(isDark)
    }
}
