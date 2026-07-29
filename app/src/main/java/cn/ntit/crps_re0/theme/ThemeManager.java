package cn.ntit.crps_re0.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

public class ThemeManager {

    private static final String PREFS_NAME = "crps_prefs";
    private static final String KEY_PRESET_INDEX = "theme_preset_index";
    private static final String KEY_CUSTOM_PRIMARY = "theme_custom_primary";
    public static final int PRESET_CUSTOM = -1;

    public static class ThemeColorSet {
        public final String name;
        public final int primary;
        public final int onPrimary;
        public final int primaryContainer;
        public final int onPrimaryContainer;
        public final int secondary;
        public final int onSecondary;
        public final int secondaryContainer;
        public final int onSecondaryContainer;
        public final int tertiary;
        public final int onTertiary;
        public final int tertiaryContainer;
        public final int onTertiaryContainer;
        public final int background;
        public final int onBackground;
        public final int surface;
        public final int onSurface;
        public final int surfaceVariant;
        public final int onSurfaceVariant;
        public final int outline;
        public final int outlineVariant;
        public final int surfaceContainerLowest;
        public final int surfaceContainerLow;
        public final int surfaceContainer;
        public final int surfaceContainerHigh;
        public final int surfaceContainerHighest;
        public final int[] chartColors;

        public ThemeColorSet(String name,
                             int primary, int onPrimary, int primaryContainer, int onPrimaryContainer,
                             int secondary, int onSecondary, int secondaryContainer, int onSecondaryContainer,
                             int tertiary, int onTertiary, int tertiaryContainer, int onTertiaryContainer,
                             int background, int onBackground,
                             int surface, int onSurface, int surfaceVariant, int onSurfaceVariant,
                             int outline, int outlineVariant,
                             int surfaceContainerLowest, int surfaceContainerLow,
                             int surfaceContainer, int surfaceContainerHigh, int surfaceContainerHighest,
                             int[] chartColors) {
            this.name = name;
            this.primary = primary;
            this.onPrimary = onPrimary;
            this.primaryContainer = primaryContainer;
            this.onPrimaryContainer = onPrimaryContainer;
            this.secondary = secondary;
            this.onSecondary = onSecondary;
            this.secondaryContainer = secondaryContainer;
            this.onSecondaryContainer = onSecondaryContainer;
            this.tertiary = tertiary;
            this.onTertiary = onTertiary;
            this.tertiaryContainer = tertiaryContainer;
            this.onTertiaryContainer = onTertiaryContainer;
            this.background = background;
            this.onBackground = onBackground;
            this.surface = surface;
            this.onSurface = onSurface;
            this.surfaceVariant = surfaceVariant;
            this.onSurfaceVariant = onSurfaceVariant;
            this.outline = outline;
            this.outlineVariant = outlineVariant;
            this.surfaceContainerLowest = surfaceContainerLowest;
            this.surfaceContainerLow = surfaceContainerLow;
            this.surfaceContainer = surfaceContainer;
            this.surfaceContainerHigh = surfaceContainerHigh;
            this.surfaceContainerHighest = surfaceContainerHighest;
            this.chartColors = chartColors;
        }
    }

    // 8 套预设配色（亮色）
    private static final ThemeColorSet[] PRESETS_LIGHT = new ThemeColorSet[]{
            // 0: 珊瑚橙（默认，当前配色）
            new ThemeColorSet("珊瑚橙",
                    0xFF99462A, 0xFFFFFFFF, 0xFFD97757, 0xFF541400,
                    0xFF745A34, 0xFFFFFFFF, 0xFFFEDAAA, 0xFF795E38,
                    0xFF39693B, 0xFFFFFFFF, 0xFF6C9E6A, 0xFF00330B,
                    0xFFFFF8F3, 0xFF1F1B16,
                    0xFFFFF8F3, 0xFF1F1B16, 0xFFEAE1D8, 0xFF55433D,
                    0xFF88726C, 0xFFDBC1B9,
                    0xFFFFFFFF, 0xFFFCF2E9, 0xFFF6ECE4, 0xFFF0E7DE, 0xFFEAE1D8,
                    new int[]{0xFF99462A, 0xFF745A34, 0xFF99462A, 0xFF745A34, 0xFF39693B, 0xFFBA1A1A}
            ),
            // 1: 靛蓝
            new ThemeColorSet("靛蓝",
                    0xFF2C52B8, 0xFFFFFFFF, 0xFFD9E2FF, 0xFF001749,
                    0xFF575E92, 0xFFFFFFFF, 0xFFE0E1FF, 0xFF12184B,
                    0xFF755472, 0xFFFFFFFF, 0xFFFFD7F3, 0xFF2C122E,
                    0xFFFBF8FF, 0xFF1B1B21,
                    0xFFFBF8FF, 0xFF1B1B21, 0xFFE2E1EC, 0xFF45464F,
                    0xFF767680, 0xFFC6C5D0,
                    0xFFFFFFFF, 0xFFF4F3FA, 0xFFECEAF1, 0xFFE6E4EB, 0xFFE0DFE6,
                    new int[]{0xFF2C52B8, 0xFF575E92, 0xFF2C52B8, 0xFF575E92, 0xFF755472, 0xFFBA1A1A}
            ),
            // 2: 森绿
            new ThemeColorSet("森绿",
                    0xFF2F6B2E, 0xFFFFFFFF, 0xFFB0F3A9, 0xFF002201,
                    0xFF52634F, 0xFFFFFFFF, 0xFFD5E8CD, 0xFF101F10,
                    0xFF396767, 0xFFFFFFFF, 0xFFBCECEB, 0xFF002020,
                    0xFFF9FBF2, 0xFF1A1C19,
                    0xFFF9FBF2, 0xFF1A1C19, 0xFFDEE5D8, 0xFF424940,
                    0xFF72796F, 0xFFC2C9BC,
                    0xFFFFFFFF, 0xFFF3F5EC, 0xFFEDEEE5, 0xFFE7E9DF, 0xFFE1E3D9,
                    new int[]{0xFF2F6B2E, 0xFF52634F, 0xFF2F6B2E, 0xFF52634F, 0xFF396767, 0xFFBA1A1A}
            ),
            // 3: 紫罗兰
            new ThemeColorSet("紫罗兰",
                    0xFF6750A4, 0xFFFFFFFF, 0xFFEADDFF, 0xFF21005D,
                    0xFF625B71, 0xFFFFFFFF, 0xFFE8DEF8, 0xFF1D192B,
                    0xFF7D5260, 0xFFFFFFFF, 0xFFFFD8E4, 0xFF31111D,
                    0xFFFFFBFE, 0xFF1C1B1F,
                    0xFFFFFBFE, 0xFF1C1B1F, 0xFFE7E0EC, 0xFF49454F,
                    0xFF79747E, 0xFFCAC4D0,
                    0xFFFFFFFF, 0xFFF7F2FA, 0xFFF0EAF3, 0xFFEAE2EE, 0xFFE4DCE8,
                    new int[]{0xFF6750A4, 0xFF625B71, 0xFF6750A4, 0xFF625B71, 0xFF7D5260, 0xFFBA1A1A}
            ),
            // 4: 樱桃红
            new ThemeColorSet("樱桃红",
                    0xFFA23A3A, 0xFFFFFFFF, 0xFFFFDAD6, 0xFF410002,
                    0xFF745A56, 0xFFFFFFFF, 0xFFFFDAD4, 0xFF2B1613,
                    0xFF735A2F, 0xFFFFFFFF, 0xFFFFDEA8, 0xFF271900,
                    0xFFFFF8F6, 0xFF201A19,
                    0xFFFFF8F6, 0xFF201A19, 0xFFF4DDD9, 0xFF534341,
                    0xFF857370, 0xFFD8C2BE,
                    0xFFFFFFFF, 0xFFF7EDEA, 0xFFF1E4DF, 0xFFEBDFE0, 0xFFE5D9D8,
                    new int[]{0xFFA23A3A, 0xFF745A56, 0xFFA23A3A, 0xFF745A56, 0xFF735A2F, 0xFFBA1A1A}
            ),
            // 5: 海洋蓝
            new ThemeColorSet("海洋蓝",
                    0xFF006877, 0xFFFFFFFF, 0xFF96F0FF, 0xFF001F25,
                    0xFF4A6268, 0xFFFFFFFF, 0xFFCCE8ED, 0xFF051F24,
                    0xFF545F7D, 0xFFFFFFFF, 0xFFDDE2FF, 0xFF101C37,
                    0xFFF8FDFC, 0xFF191C1D,
                    0xFFF8FDFC, 0xFF191C1D, 0xFFDAE4E7, 0xFF3F484B,
                    0xFF6F797C, 0xFFBEC8CB,
                    0xFFFFFFFF, 0xFFF1F7F6, 0xFFEBEFF2, 0xFFE5EAEE, 0xFFDEE5E9,
                    new int[]{0xFF006877, 0xFF4A6268, 0xFF006877, 0xFF4A6268, 0xFF545F7D, 0xFFBA1A1A}
            ),
            // 6: 沙漠金
            new ThemeColorSet("沙漠金",
                    0xFF825500, 0xFFFFFFFF, 0xFFFFDDB2, 0xFF291800,
                    0xFF705C43, 0xFFFFFFFF, 0xFFFBE0BA, 0xFF281A06,
                    0xFF52653A, 0xFFFFFFFF, 0xFFD5EBAD, 0xFF131F02,
                    0xFFFFF8F0, 0xFF1F1B16,
                    0xFFFFF8F0, 0xFF1F1B16, 0xFFEBE1D0, 0xFF4C4639,
                    0xFF7D7667, 0xFFCDC5B4,
                    0xFFFFFFFF, 0xFFFBEEE0, 0xFFF5E7D6, 0xFFEFE0CF, 0xFFE9DBC9,
                    new int[]{0xFF825500, 0xFF705C43, 0xFF825500, 0xFF705C43, 0xFF52653A, 0xFFBA1A1A}
            ),
            // 7: 石墨灰
            new ThemeColorSet("石墨灰",
                    0xFF425980, 0xFFFFFFFF, 0xFFD8E2FF, 0xFF001A41,
                    0xFF5A5C66, 0xFFFFFFFF, 0xFFDFE1F0, 0xFF181920,
                    0xFF775469, 0xFFFFFFFF, 0xFFFFD7E6, 0xFF2D1125,
                    0xFFFDFBFF, 0xFF1B1B1F,
                    0xFFFDFBFF, 0xFF1B1B1F, 0xFFE3E1EC, 0xFF46464F,
                    0xFF767680, 0xFFC6C5D0,
                    0xFFFFFFFF, 0xFFF4F3FA, 0xFFECEAF1, 0xFFE6E4EB, 0xFFE0DFE6,
                    new int[]{0xFF425980, 0xFF5A5C66, 0xFF425980, 0xFF5A5C66, 0xFF775469, 0xFFBA1A1A}
            ),
            // 8: 极简黑白
            new ThemeColorSet("极简黑白",
                    0xFF1A1A1A, 0xFFFFFFFF, 0xFF3A3A3A, 0xFFFFFFFF,
                    0xFF4A4A4A, 0xFFFFFFFF, 0xFFD6D6D6, 0xFF1A1A1A,
                    0xFF6A6A6A, 0xFFFFFFFF, 0xFFC8C8C8, 0xFF1A1A1A,
                    0xFFFAFAFA, 0xFF1A1A1A,
                    0xFFFAFAFA, 0xFF1A1A1A, 0xFFDDDDDD, 0xFF4A4A4A,
                    0xFF7A7A7A, 0xFFC8C8C8,
                    0xFFFFFFFF, 0xFFF5F5F5, 0xFFEFEFEF, 0xFFE9E9E9, 0xFFE3E3E3,
                    new int[]{0xFF1A1A1A, 0xFF4A4A4A, 0xFF1A1A1A, 0xFF4A4A4A, 0xFF6A6A6A, 0xFFBA1A1A}
            ),
            // 9: 电报蓝（Telegram）
            new ThemeColorSet("电报蓝",
                    0xFF0088CC, 0xFFFFFFFF, 0xFFB3E5FC, 0xFF003847,
                    0xFF51606D, 0xFFFFFFFF, 0xFFD5E2E8, 0xFF0E1D24,
                    0xFF7A5746, 0xFFFFFFFF, 0xFFFFDCC8, 0xFF2A1709,
                    0xFFF7FBFC, 0xFF181C1E,
                    0xFFF7FBFC, 0xFF181C1E, 0xFFD8E3E7, 0xFF3F484B,
                    0xFF6F797C, 0xFFBEC8CB,
                    0xFFFFFFFF, 0xFFF1F6F8, 0xFFEBF0F2, 0xFFE5EBEE, 0xFFDFE5E9,
                    new int[]{0xFF0088CC, 0xFF51606D, 0xFF0088CC, 0xFF51606D, 0xFF7A5746, 0xFFBA1A1A}
            ),
            // 10: 暮光紫（Discord）
            new ThemeColorSet("暮光紫",
                    0xFF5865F2, 0xFFFFFFFF, 0xFFE0E5FF, 0xFF001257,
                    0xFF5A5D72, 0xFFFFFFFF, 0xFFE1E2F5, 0xFF171A2E,
                    0xFF7D5260, 0xFFFFFFFF, 0xFFFFD8E4, 0xFF31111D,
                    0xFFFBF8FF, 0xFF1B1B21,
                    0xFFFBF8FF, 0xFF1B1B21, 0xFFE3E1EC, 0xFF45464F,
                    0xFF767680, 0xFFC6C5D0,
                    0xFFFFFFFF, 0xFFF4F3FA, 0xFFECEAF1, 0xFFE6E4EB, 0xFFE0DFE6,
                    new int[]{0xFF5865F2, 0xFF5A5D72, 0xFF5865F2, 0xFF5A5D72, 0xFF7D5260, 0xFFBA1A1A}
            ),
            // 11: 优雅灰白
            new ThemeColorSet("优雅灰白",
                    0xFF5C5C5C, 0xFFFFFFFF, 0xFFD0D0D0, 0xFF1A1A1A,
                    0xFF8C8C8C, 0xFFFFFFFF, 0xFFE0E0E0, 0xFF1A1A1A,
                    0xFF707070, 0xFFFFFFFF, 0xFFD8D8D8, 0xFF1A1A1A,
                    0xFFFCFCFC, 0xFF1A1A1A,
                    0xFFFCFCFC, 0xFF1A1A1A, 0xFFE0E0E0, 0xFF4A4A4A,
                    0xFF808080, 0xFFCCCCCC,
                    0xFFFFFFFF, 0xFFF7F7F7, 0xFFF1F1F1, 0xFFEBEBEB, 0xFFE5E5E5,
                    new int[]{0xFF5C5C5C, 0xFF8C8C8C, 0xFF5C5C5C, 0xFF8C8C8C, 0xFF707070, 0xFFBA1A1A}
            ),
    };
    // 12 套预设配色（暗色）
    private static final ThemeColorSet[] PRESETS_DARK = new ThemeColorSet[]{
            // 0: 珊瑚橙
            new ThemeColorSet("珊瑚橙",
                    0xFFFFB59E, 0xFF141210, 0xFF7A2F15, 0xFFFFDBD0,
                    0xFFFFCBA4, 0xFF141210, 0xFF5A431F, 0xFFFFDDB1,
                    0xFF9FD49B, 0xFF003A0E, 0xFF215025, 0xFFBAF0B6,
                    0xFF141210, 0xFFEAE1D8,
                    0xFF1C1A18, 0xFFEAE1D8, 0xFF55433D, 0xFFDBC1B9,
                    0xFFA08C85, 0xFF55433D,
                    0xFF1F1B16, 0xFF2B2620, 0xFF332E28, 0xFF3D3830, 0xFF4A443C,
                    new int[]{0xFFFFB59E, 0xFFE3C193, 0xFFFFB59E, 0xFFE3C193, 0xFF9FD49B, 0xFFFFB4AB}
            ),
            // 1: 靛蓝
            new ThemeColorSet("靛蓝",
                0xFFB2C5FF, 0xFF002B75, 0xFF003F9D, 0xFFDCE2FF,
                0xFFC2C5FF, 0xFF282C5A, 0xFF3F4377, 0xFFE1E1FF,
                0xFFE6B8D6, 0xFF45263D, 0xFF5D3E54, 0xFFFFD7F0,
                0xFF15151C, 0xFFC5C5D2,
                0xFF191920, 0xFFC5C5D2, 0xFF2A2A33, 0xFFC0BFD4,
                0xFF908F99, 0xFF44444F,
                0xFF101017, 0xFF1E1E24, 0xFF25252C, 0xFF2C2C33, 0xFF36363D,
                new int[]{0xFFB2C5FF, 0xFFC2C5FF, 0xFFB2C5FF, 0xFFC2C5FF, 0xFFE6B8D6, 0xFFFFB4AB}
            ),
            // 2: 森绿
            new ThemeColorSet("森绿",
                    0xFF95D78F, 0xFF003A04, 0xFF155218, 0xFFB0F3A9,
                    0xFFB9CCB2, 0xFF243422, 0xFF3A4B37, 0xFFD5E8CD,
                    0xFFA0D0D0, 0xFF003636, 0xFF1F4E4D, 0xFFBCECEB,
                    0xFF141411, 0xFFE3E4DD,
                    0xFF141411, 0xFFE3E4DD, 0xFF2A2F26, 0xFFBFC7B6,
                    0xFF8B9384, 0xFF40473D,
                    0xFF0E0F0C, 0xFF1A1B17, 0xFF20211C, 0xFF262722, 0xFF30312B,
                    new int[]{0xFF95D78F, 0xFFB9CCB2, 0xFF95D78F, 0xFFB9CCB2, 0xFFA0D0D0, 0xFFFFB4AB}
            ),
            // 3: 紫罗兰
            new ThemeColorSet("紫罗兰",
                    0xFFD0BCFF, 0xFF381E72, 0xFF4F378B, 0xFFEADDFF,
                    0xFFCCC2DC, 0xFF332D41, 0xFF4A4458, 0xFFE8DEF8,
                    0xFFEFB8C8, 0xFF492532, 0xFF633B48, 0xFFFFD8E4,
                    0xFF1C1B1F, 0xFFE6E1E5,
                    0xFF1C1B1F, 0xFFE6E1E5, 0xFF313033, 0xFFCAC4D0,
                    0xFF938F99, 0xFF49454F,
                    0xFF151418, 0xFF222026, 0xFF2A282E, 0xFF322F37, 0xFF3C3940,
                    new int[]{0xFFD0BCFF, 0xFFCCC2DC, 0xFFD0BCFF, 0xFFCCC2DC, 0xFFEFB8C8, 0xFFFFB4AB}
            ),
            // 4: 樱桃红
            new ThemeColorSet("樱桃红",
                    0xFFFFB4AB, 0xFF690005, 0xFF93000A, 0xFFFFDAD6,
                    0xFFE7BDB6, 0xFF442722, 0xFF5D3D37, 0xFFFFDAD4,
                    0xFFE2C48B, 0xFF3E2D04, 0xFF574419, 0xFFFFDEA8,
                    0xFF201514, 0xFFEDE0DE,
                    0xFF201514, 0xFFEDE0DE, 0xFF382725, 0xFFD8C2BE,
                    0xFFA08C8A, 0xFF534341,
                    0xFF170F0E, 0xFF251715, 0xFF2D1E1C, 0xFF372625, 0xFF443230,
                    new int[]{0xFFFFB4AB, 0xFFE7BDB6, 0xFFFFB4AB, 0xFFE7BDB6, 0xFFE2C48B, 0xFFFFB4AB}
            ),
            // 5: 海洋蓝
            new ThemeColorSet("海洋蓝",
                    0xFF7FDBEE, 0xFF00363F, 0xFF004F5A, 0xFF96F0FF,
                    0xFFB0CCD2, 0xFF1D3439, 0xFF354B50, 0xFFCCE8ED,
                    0xFFB8C4FF, 0xFF192B57, 0xFF334370, 0xFFDDE2FF,
                    0xFF101415, 0xFFE1E3E4,
                    0xFF101415, 0xFFE1E3E4, 0xFF273032, 0xFFBEC8CB,
                    0xFF899296, 0xFF3F484B,
                    0xFF0B0F10, 0xFF151A1B, 0xFF1C2122, 0xFF24292A, 0xFF2E3335,
                    new int[]{0xFF7FDBEE, 0xFFB0CCD2, 0xFF7FDBEE, 0xFFB0CCD2, 0xFFB8C4FF, 0xFFFFB4AB}
            ),
            // 6: 沙漠金
            new ThemeColorSet("沙漠金",
                    0xFFFFB86A, 0xFF422A00, 0xFF604000, 0xFFFFDDB2,
                    0xFFDEC4A1, 0xFF3D2E15, 0xFF57442A, 0xFFFBE0BA,
                    0xFFB8CE8E, 0xFF28360B, 0xFF3E4D1F, 0xFFD5EBAD,
                    0xFF1F1B13, 0xFFEAE1D0,
                    0xFF1F1B13, 0xFFEAE1D0, 0xFF342F24, 0xFFCDC5B4,
                    0xFFA29A8A, 0xFF4C4639,
                    0xFF15130D, 0xFF28231B, 0xFF302B21, 0xFF3A3429, 0xFF453F33,
                    new int[]{0xFFFFB86A, 0xFFDEC4A1, 0xFFFFB86A, 0xFFDEC4A1, 0xFFB8CE8E, 0xFFFFB4AB}
            ),
            // 7: 石墨灰
            new ThemeColorSet("石墨灰",
                    0xFFB2C5FF, 0xFF002B75, 0xFF2540A6, 0xFFDCE2FF,
                    0xFFC4C5DD, 0xFF2C2D37, 0xFF43444F, 0xFFDFE1F0,
                    0xFFFFB1CB, 0xFF492536, 0xFF633B4C, 0xFFFFD7E6,
                    0xFF1A1B20, 0xFFE4E1F6,
                    0xFF1A1B20, 0xFFE4E1F6, 0xFF292931, 0xFFC0BFD4,
                    0xFF908F9D, 0xFF44444F,
                    0xFF141518, 0xFF1E1F24, 0xFF25262B, 0xFF2D2E33, 0xFF38383E,
                    new int[]{0xFFB2C5FF, 0xFFC4C5DD, 0xFFB2C5FF, 0xFFC4C5DD, 0xFFFFB1CB, 0xFFFFB4AB}
            ),
            // 8: 极简黑白（暗色）
            new ThemeColorSet("极简黑白",
                    0xFFE0E0E0, 0xFF1A1A1A, 0xFFA0A0A0, 0xFF2A2A2A,
                    0xFFB0B0B0, 0xFF2A2A2A, 0xFF6A6A6A, 0xFF1A1A1A,
                    0xFF909090, 0xFF1A1A1A, 0xFF808080, 0xFF2A2A2A,
                    0xFF1A1A1A, 0xFFE0E0E0,
                    0xFF1A1A1A, 0xFFE0E0E0, 0xFF2A2A2A, 0xFFB0B0B0,
                    0xFF808080, 0xFF404040,
                    0xFF0E0E0E, 0xFF1A1A1A, 0xFF242424, 0xFF2E2E2E, 0xFF383838,
                    new int[]{0xFFE0E0E0, 0xFFB0B0B0, 0xFFE0E0E0, 0xFFB0B0B0, 0xFF909090, 0xFFFFB4AB}
            ),
            // 9: 电报蓝（Telegram 暗色）
            new ThemeColorSet("电报蓝",
                    0xFF6FC8F0, 0xFF003544, 0xFF004F66, 0xFFB3E5FC,
                    0xFFB0C8D2, 0xFF1A2A30, 0xFF384B53, 0xFFD5E2E8,
                    0xFFE2C48B, 0xFF3E2D04, 0xFF574419, 0xFFFFDCC8,
                    0xFF0F1517, 0xFFDEE7EA,
                    0xFF0F1517, 0xFFDEE7EA, 0xFF252E30, 0xFFBEC8CB,
                    0xFF899296, 0xFF3F484B,
                    0xFF0A0F10, 0xFF141A1B, 0xFF1C2122, 0xFF24292A, 0xFF2E3335,
                    new int[]{0xFF6FC8F0, 0xFFB0C8D2, 0xFF6FC8F0, 0xFFB0C8D2, 0xFFE2C48B, 0xFFFFB4AB}
            ),
            // 10: 暮光紫（Discord 暗色）
            new ThemeColorSet("暮光紫",
                    0xFFC7D0FF, 0xFF001A72, 0xFF3A47B5, 0xFFE0E5FF,
                    0xFFC2C4DC, 0xFF2A2D42, 0xFF43476A, 0xFFE1E2F5,
                    0xFFEFB8C8, 0xFF492532, 0xFF633B48, 0xFFFFD8E4,
                    0xFF15161B, 0xFFE4E2EC,
                    0xFF15161B, 0xFFE4E2EC, 0xFF2A2A33, 0xFFC0BFD4,
                    0xFF908F99, 0xFF44444F,
                    0xFF101014, 0xFF1E1E24, 0xFF25252C, 0xFF2C2C33, 0xFF36363D,
                    new int[]{0xFFC7D0FF, 0xFFC2C4DC, 0xFFC7D0FF, 0xFFC2C4DC, 0xFFEFB8C8, 0xFFFFB4AB}
            ),
            // 11: 优雅灰白（暗色）
            new ThemeColorSet("优雅灰白",
                    0xFFCCCCCC, 0xFF2A2A2A, 0xFF808080, 0xFF1A1A1A,
                    0xFF9C9C9C, 0xFF1A1A1A, 0xFF5C5C5C, 0xFF2A2A2A,
                    0xFFB0B0B0, 0xFF1A1A1A, 0xFF909090, 0xFF2A2A2A,
                    0xFF1F1F1F, 0xFFCCCCCC,
                    0xFF1F1F1F, 0xFFCCCCCC, 0xFF303030, 0xFF9C9C9C,
                    0xFF6A6A6A, 0xFF3A3A3A,
                    0xFF161616, 0xFF1F1F1F, 0xFF262626, 0xFF2E2E2E, 0xFF363636,
                    new int[]{0xFFCCCCCC, 0xFF9C9C9C, 0xFFCCCCCC, 0xFF9C9C9C, 0xFFB0B0B0, 0xFFFFB4AB}
            ),
    };

    // 40 个常用自定义颜色（RGB 选择器的预设色网格）
    public static final int[] QUICK_COLORS = new int[]{
            0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5,
            0xFF2196F3, 0xFF03A9F4, 0xFF00BCD4, 0xFF009688, 0xFF4CAF50,
            0xFF8BC34A, 0xFFCDDC39, 0xFFFFEB3B, 0xFFFFC107, 0xFFFF9800,
            0xFFFF5722, 0xFF795548, 0xFF607D8B, 0xFF9E9E9E, 0xFF000000,
            0xFFE57373, 0xFFF06292, 0xFFBA68C8, 0xFF9575CD, 0xFF7986CB,
            0xFF64B5F6, 0xFF4FC3F7, 0xFF4DD0E1, 0xFF4DB6AC, 0xFF81C784,
            0xFFAED581, 0xFFDCE775, 0xFFFFD54F, 0xFFFFB74D, 0xFFFF8A65,
            0xFFA1887F, 0xFF90A4AE, 0xFFE0E0E0, 0xFFBDBDBD, 0xFF757575,
    };

    // 配色显示顺序映射：显示位 → 底层存储索引（按色系渐变排列）
    private static final int[] PRESET_DISPLAY_ORDER = new int[]{
            4,  // 0 樱桃红(红)
            0,  // 1 珊瑚橙(橙)
            6,  // 2 沙漠金(黄)
            2,  // 3 森绿(绿)
            5,  // 4 海洋蓝(青)
            9,  // 5 电报蓝(蓝)
            1,  // 6 靛蓝(蓝紫)
            3,  // 7 紫罗兰(紫)
            10, // 8 暮光紫(蓝紫亮)
            7,  // 9 石墨灰(中性灰)
            11, // 10 优雅灰白(浅灰)
            8,  // 11 极简黑白(无彩)
    };

    public static int displayIndexToStored(int displayIndex) {
        if (displayIndex < 0 || displayIndex >= PRESET_DISPLAY_ORDER.length) return 0;
        return PRESET_DISPLAY_ORDER[displayIndex];
    }

    public static int storedIndexToDisplay(int storedIndex) {
        for (int i = 0; i < PRESET_DISPLAY_ORDER.length; i++) {
            if (PRESET_DISPLAY_ORDER[i] == storedIndex) return i;
        }
        return 0;
    }

    public static int getPresetCount() {
        return PRESETS_LIGHT.length;
    }

    public static ThemeColorSet getPreset(int index, boolean dark) {
        ThemeColorSet[] arr = dark ? PRESETS_DARK : PRESETS_LIGHT;
        if (index < 0 || index >= arr.length) return arr[0];
        return arr[index];
    }

    public static ThemeColorSet getPresetByDisplay(int displayIndex, boolean dark) {
        return getPreset(displayIndexToStored(displayIndex), dark);
    }

    public static List<String> getPresetNames() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < PRESET_DISPLAY_ORDER.length; i++) {
            names.add(PRESETS_LIGHT[PRESET_DISPLAY_ORDER[i]].name);
        }
        return names;
    }

    public static boolean isDarkMode(Context context) {
        int nightFlags = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    public static int loadPresetIndex(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_PRESET_INDEX, 7);
    }

    public static int loadCustomPrimary(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_CUSTOM_PRIMARY, 0xFF2C52B8);
    }

    public static void savePresetIndex(Context context, int index) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putInt(KEY_PRESET_INDEX, index).apply();
    }

    public static void saveCustomPrimary(Context context, int color) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putInt(KEY_CUSTOM_PRIMARY, color).apply();
    }

    /**
     * 获取当前生效的配色。
     * 如果是预设，直接返回预设色卡；
     * 如果是自定义，基于主色用色彩调和算法生成完整色卡。
     */
    public static ThemeColorSet getCurrentColors(Context context) {
        boolean dark = isDarkMode(context);
        int idx = loadPresetIndex(context);
        if (idx == PRESET_CUSTOM) {
            int primary = loadCustomPrimary(context);
            return generateFromPrimary(primary, dark);
        }
        return getPreset(idx, dark);
    }

    /**
     * 基于一个主色，用简单色彩调和算法生成完整 Material 3 风格色卡。
     * 算法思路：
     * - primary 是输入主色
     * - onPrimary 用白/黑（根据 primary 亮度）
     * - primaryContainer 是 primary 降饱和提亮 / 降饱和压暗
     * - secondary 是 primary 的邻近色相，降饱和
     * - tertiary 是 primary 的对比色相，降饱和
     * - surface 系列是中性灰，带一点点主色相偏
     */
    public static ThemeColorSet generateFromPrimary(@ColorInt int primary, boolean dark) {
        float[] hsv = new float[3];
        Color.colorToHSV(primary, hsv);
        float h = hsv[0];
        float s = hsv[1];
        float v = hsv[2];

        int onPrimary = v > 0.5f ? 0xFFFFFFFF : 0xFF000000;

        int primaryContainer = dark
                ? adjustColor(primary, 0f, -0.15f, -0.3f)
                : adjustColor(primary, 0f, -0.3f, 0.3f);
        int onPrimaryContainer = dark ? 0xFFFFFFFF : 0xFF000000;

        // secondary：邻近色（+15°），降饱和
        int secondary = adjustColor(primary, 15f, -0.3f, 0f);
        int onSecondary = isLight(secondary) ? 0xFF000000 : 0xFFFFFFFF;
        int secondaryContainer = dark
                ? adjustColor(secondary, 0f, -0.1f, -0.3f)
                : adjustColor(secondary, 0f, -0.3f, 0.3f);
        int onSecondaryContainer = dark ? 0xFFFFFFFF : 0xFF000000;

        // tertiary：对比色（+120°），降饱和
        int tertiary = adjustColor(primary, 120f, -0.2f, 0f);
        int onTertiary = isLight(tertiary) ? 0xFF000000 : 0xFFFFFFFF;
        int tertiaryContainer = dark
                ? adjustColor(tertiary, 0f, -0.1f, -0.3f)
                : adjustColor(tertiary, 0f, -0.3f, 0.3f);
        int onTertiaryContainer = dark ? 0xFFFFFFFF : 0xFF000000;

        // 中性色：取主色相，极低饱和
        int background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant;
        int outline, outlineVariant;
        int scLowest, scLow, sc, scHigh, scHighest;

        if (dark) {
            background = adjustColor(primary, 0f, -s * 0.9f, 0.08f);
            onBackground = 0xFFE5E5EA;
            surface = adjustColor(primary, 0f, -s * 0.9f, 0.10f);
            onSurface = 0xFFE5E5EA;
            surfaceVariant = adjustColor(primary, 0f, -s * 0.7f, 0.20f);
            onSurfaceVariant = 0xFFC5C5CE;
            outline = adjustColor(primary, 0f, -s * 0.5f, 0.55f);
            outlineVariant = adjustColor(primary, 0f, -s * 0.8f, 0.25f);
            scLowest = adjustColor(primary, 0f, -s * 0.9f, 0.06f);
            scLow = adjustColor(primary, 0f, -s * 0.9f, 0.12f);
            sc = adjustColor(primary, 0f, -s * 0.9f, 0.16f);
            scHigh = adjustColor(primary, 0f, -s * 0.85f, 0.20f);
            scHighest = adjustColor(primary, 0f, -s * 0.8f, 0.25f);
        } else {
            background = adjustColor(primary, 0f, -s * 0.95f, 0.97f);
            onBackground = 0xFF1A1A1F;
            surface = adjustColor(primary, 0f, -s * 0.95f, 0.98f);
            onSurface = 0xFF1A1A1F;
            surfaceVariant = adjustColor(primary, 0f, -s * 0.7f, 0.90f);
            onSurfaceVariant = 0xFF45464F;
            outline = adjustColor(primary, 0f, -s * 0.5f, 0.50f);
            outlineVariant = adjustColor(primary, 0f, -s * 0.85f, 0.80f);
            scLowest = 0xFFFFFFFF;
            scLow = adjustColor(primary, 0f, -s * 0.95f, 0.96f);
            sc = adjustColor(primary, 0f, -s * 0.95f, 0.94f);
            scHigh = adjustColor(primary, 0f, -s * 0.9f, 0.92f);
            scHighest = adjustColor(primary, 0f, -s * 0.85f, 0.88f);
        }

        // 图表色：primary, secondary, primary(again), secondary(again), tertiary, error
        int[] chartColors = new int[]{
                primary, secondary, primary, secondary, tertiary, 0xFFBA1A1A
        };
        if (dark) {
            chartColors[0] = lighten(primary, 0.3f);
            chartColors[1] = lighten(secondary, 0.3f);
            chartColors[2] = lighten(primary, 0.3f);
            chartColors[3] = lighten(secondary, 0.3f);
            chartColors[4] = lighten(tertiary, 0.3f);
            chartColors[5] = 0xFFFFB4AB;
        }

        return new ThemeColorSet("自定义",
                primary, onPrimary, primaryContainer, onPrimaryContainer,
                secondary, onSecondary, secondaryContainer, onSecondaryContainer,
                tertiary, onTertiary, tertiaryContainer, onTertiaryContainer,
                background, onBackground,
                surface, onSurface, surfaceVariant, onSurfaceVariant,
                outline, outlineVariant,
                scLowest, scLow, sc, scHigh, scHighest,
                chartColors);
    }

    private static int adjustColor(int color, float hueDelta, float satDelta, float valDelta) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + hueDelta + 360) % 360;
        hsv[1] = clamp01(hsv[1] + satDelta);
        hsv[2] = clamp01(hsv[2] + valDelta);
        return Color.HSVToColor(Color.alpha(color), hsv);
    }

    private static int lighten(int color, float amount) {
        return adjustColor(color, 0f, 0f, amount);
    }

    private static boolean isLight(int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        double lum = 0.299 * r + 0.587 * g + 0.114 * b;
        return lum > 140;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
