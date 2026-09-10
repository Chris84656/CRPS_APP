package cn.ntit.crps_compose.theme

import android.content.Context
import android.graphics.Color

/**
 * 主题色板：25 个 Material3 主题色 + 6 个图表色。
 *
 * 色值数组索引约定：
 * 0 primary / 1 onPrimary / 2 primaryContainer / 3 onPrimaryContainer
 * 4 secondary / 5 onSecondary / 6 secondaryContainer / 7 onSecondaryContainer
 * 8 tertiary / 9 onTertiary / 10 tertiaryContainer / 11 onTertiaryContainer
 * 12 background / 13 onBackground
 * 14 surface / 15 onSurface / 16 surfaceVariant / 17 onSurfaceVariant
 * 18 outline / 19 outlineVariant
 * 20 surfaceContainerLowest / 21 surfaceContainerLow / 22 surfaceContainer
 * 23 surfaceContainerHigh / 24 surfaceContainerHighest
 * 25-30 图表 6 色
 */
class ThemePalette(val name: String, private val c: IntArray) {

    val primary get() = c[0]
    val onPrimary get() = c[1]
    val primaryContainer get() = c[2]
    val onPrimaryContainer get() = c[3]
    val secondary get() = c[4]
    val onSecondary get() = c[5]
    val secondaryContainer get() = c[6]
    val onSecondaryContainer get() = c[7]
    val tertiary get() = c[8]
    val onTertiary get() = c[9]
    val tertiaryContainer get() = c[10]
    val onTertiaryContainer get() = c[11]
    val background get() = c[12]
    val onBackground get() = c[13]
    val surface get() = c[14]
    val onSurface get() = c[15]
    val surfaceVariant get() = c[16]
    val onSurfaceVariant get() = c[17]
    val outline get() = c[18]
    val outlineVariant get() = c[19]
    val surfaceContainerLowest get() = c[20]
    val surfaceContainerLow get() = c[21]
    val surfaceContainer get() = c[22]
    val surfaceContainerHigh get() = c[23]
    val surfaceContainerHighest get() = c[24]

    fun chartColor(index: Int): Int = c[25 + (index % 6)]

    companion object {

        const val PRESET_CUSTOM = -1

        // 12 套预设（亮色），色值与旧版 crps_re0 完全一致
        val PRESETS_LIGHT: List<ThemePalette> = listOf(
            // 0 珊瑚橙
            ThemePalette("珊瑚橙", intArrayOf(
                0xFF99462A.toInt(), 0xFFFFFFFF.toInt(), 0xFFD97757.toInt(), 0xFF541400.toInt(),
                0xFF745A34.toInt(), 0xFFFFFFFF.toInt(), 0xFFFEDAAA.toInt(), 0xFF795E38.toInt(),
                0xFF39693B.toInt(), 0xFFFFFFFF.toInt(), 0xFF6C9E6A.toInt(), 0xFF00330B.toInt(),
                0xFFFFF8F3.toInt(), 0xFF1F1B16.toInt(), 0xFFFFF8F3.toInt(), 0xFF1F1B16.toInt(),
                0xFFEAE1D8.toInt(), 0xFF55433D.toInt(), 0xFF88726C.toInt(), 0xFFDBC1B9.toInt(),
                0xFFFFFFFF.toInt(), 0xFFFCF2E9.toInt(), 0xFFF6ECE4.toInt(), 0xFFF0E7DE.toInt(), 0xFFEAE1D8.toInt(),
                0xFF99462A.toInt(), 0xFF745A34.toInt(), 0xFF99462A.toInt(), 0xFF745A34.toInt(), 0xFF39693B.toInt(), 0xFFBA1A1A.toInt(),
            )),
            // 1 靛蓝
            ThemePalette("靛蓝", intArrayOf(
                0xFF2C52B8.toInt(), 0xFFFFFFFF.toInt(), 0xFFD9E2FF.toInt(), 0xFF001749.toInt(),
                0xFF575E92.toInt(), 0xFFFFFFFF.toInt(), 0xFFE0E1FF.toInt(), 0xFF12184B.toInt(),
                0xFF755472.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFD7F3.toInt(), 0xFF2C122E.toInt(),
                0xFFFBF8FF.toInt(), 0xFF1B1B21.toInt(), 0xFFFBF8FF.toInt(), 0xFF1B1B21.toInt(),
                0xFFE2E1EC.toInt(), 0xFF45464F.toInt(), 0xFF767680.toInt(), 0xFFC6C5D0.toInt(),
                0xFFFFFFFF.toInt(), 0xFFF4F3FA.toInt(), 0xFFECEAF1.toInt(), 0xFFE6E4EB.toInt(), 0xFFE0DFE6.toInt(),
                0xFF2C52B8.toInt(), 0xFF575E92.toInt(), 0xFF2C52B8.toInt(), 0xFF575E92.toInt(), 0xFF755472.toInt(), 0xFFBA1A1A.toInt(),
            )),
            // 2 森绿
            ThemePalette("森绿", intArrayOf(
                0xFF2F6B2E.toInt(), 0xFFFFFFFF.toInt(), 0xFFB0F3A9.toInt(), 0xFF002201.toInt(),
                0xFF52634F.toInt(), 0xFFFFFFFF.toInt(), 0xFFD5E8CD.toInt(), 0xFF101F10.toInt(),
                0xFF396767.toInt(), 0xFFFFFFFF.toInt(), 0xFFBCECEB.toInt(), 0xFF002020.toInt(),
                0xFFF9FBF2.toInt(), 0xFF1A1C19.toInt(), 0xFFF9FBF2.toInt(), 0xFF1A1C19.toInt(),
                0xFFDEE5D8.toInt(), 0xFF424940.toInt(), 0xFF72796F.toInt(), 0xFFC2C9BC.toInt(),
                0xFFFFFFFF.toInt(), 0xFFF3F5EC.toInt(), 0xFFEDEEE5.toInt(), 0xFFE7E9DF.toInt(), 0xFFE1E3D9.toInt(),
                0xFF2F6B2E.toInt(), 0xFF52634F.toInt(), 0xFF2F6B2E.toInt(), 0xFF52634F.toInt(), 0xFF396767.toInt(), 0xFFBA1A1A.toInt(),
            )),
            // 3 紫罗兰
            ThemePalette("紫罗兰", intArrayOf(
                0xFF6750A4.toInt(), 0xFFFFFFFF.toInt(), 0xFFEADDFF.toInt(), 0xFF21005D.toInt(),
                0xFF625B71.toInt(), 0xFFFFFFFF.toInt(), 0xFFE8DEF8.toInt(), 0xFF1D192B.toInt(),
                0xFF7D5260.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFD8E4.toInt(), 0xFF31111D.toInt(),
                0xFFFFFBFE.toInt(), 0xFF1C1B1F.toInt(), 0xFFFFFBFE.toInt(), 0xFF1C1B1F.toInt(),
                0xFFE7E0EC.toInt(), 0xFF49454F.toInt(), 0xFF79747E.toInt(), 0xFFCAC4D0.toInt(),
                0xFFFFFFFF.toInt(), 0xFFF7F2FA.toInt(), 0xFFF0EAF3.toInt(), 0xFFEAE2EE.toInt(), 0xFFE4DCE8.toInt(),
                0xFF6750A4.toInt(), 0xFF625B71.toInt(), 0xFF6750A4.toInt(), 0xFF625B71.toInt(), 0xFF7D5260.toInt(), 0xFFBA1A1A.toInt(),
            )),
            // 4 樱桃红
            ThemePalette("樱桃红", intArrayOf(
                0xFFA23A3A.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFDAD6.toInt(), 0xFF410002.toInt(),
                0xFF745A56.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFDAD4.toInt(), 0xFF2B1613.toInt(),
                0xFF735A2F.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFDEA8.toInt(), 0xFF271900.toInt(),
                0xFFFFF8F6.toInt(), 0xFF201A19.toInt(), 0xFFFFF8F6.toInt(), 0xFF201A19.toInt(),
                0xFFF4DDD9.toInt(), 0xFF534341.toInt(), 0xFF857370.toInt(), 0xFFD8C2BE.toInt(),
                0xFFFFFFFF.toInt(), 0xFFF7EDEA.toInt(), 0xFFF1E4DF.toInt(), 0xFFEBDFE0.toInt(), 0xFFE5D9D8.toInt(),
                0xFFA23A3A.toInt(), 0xFF745A56.toInt(), 0xFFA23A3A.toInt(), 0xFF745A56.toInt(), 0xFF735A2F.toInt(), 0xFFBA1A1A.toInt(),
            )),
            // 5 海洋蓝
            ThemePalette("海洋蓝", intArrayOf(
                0xFF006877.toInt(), 0xFFFFFFFF.toInt(), 0xFF96F0FF.toInt(), 0xFF001F25.toInt(),
                0xFF4A6268.toInt(), 0xFFFFFFFF.toInt(), 0xFFCCE8ED.toInt(), 0xFF051F24.toInt(),
                0xFF545F7D.toInt(), 0xFFFFFFFF.toInt(), 0xFFDDE2FF.toInt(), 0xFF101C37.toInt(),
                0xFFF8FDFC.toInt(), 0xFF191C1D.toInt(), 0xFFF8FDFC.toInt(), 0xFF191C1D.toInt(),
                0xFFDAE4E7.toInt(), 0xFF3F484B.toInt(), 0xFF6F797C.toInt(), 0xFFBEC8CB.toInt(),
                0xFFFFFFFF.toInt(), 0xFFF1F7F6.toInt(), 0xFFEBEFF2.toInt(), 0xFFE5EAEE.toInt(), 0xFFDEE5E9.toInt(),
                0xFF006877.toInt(), 0xFF4A6268.toInt(), 0xFF006877.toInt(), 0xFF4A6268.toInt(), 0xFF545F7D.toInt(), 0xFFBA1A1A.toInt(),
            )),
            // 6 沙漠金
            ThemePalette("沙漠金", intArrayOf(
                0xFF825500.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFDDB2.toInt(), 0xFF291800.toInt(),
                0xFF705C43.toInt(), 0xFFFFFFFF.toInt(), 0xFFFBE0BA.toInt(), 0xFF281A06.toInt(),
                0xFF52653A.toInt(), 0xFFFFFFFF.toInt(), 0xFFD5EBAD.toInt(), 0xFF131F02.toInt(),
                0xFFFFF8F0.toInt(), 0xFF1F1B16.toInt(), 0xFFFFF8F0.toInt(), 0xFF1F1B16.toInt(),
                0xFFEBE1D0.toInt(), 0xFF4C4639.toInt(), 0xFF7D7667.toInt(), 0xFFCDC5B4.toInt(),
                0xFFFFFFFF.toInt(), 0xFFFBEEE0.toInt(), 0xFFF5E7D6.toInt(), 0xFFEFE0CF.toInt(), 0xFFE9DBC9.toInt(),
                0xFF825500.toInt(), 0xFF705C43.toInt(), 0xFF825500.toInt(), 0xFF705C43.toInt(), 0xFF52653A.toInt(), 0xFFBA1A1A.toInt(),
            )),
            // 7 石墨灰
            ThemePalette("石墨灰", intArrayOf(
                0xFF425980.toInt(), 0xFFFFFFFF.toInt(), 0xFFD8E2FF.toInt(), 0xFF001A41.toInt(),
                0xFF5A5C66.toInt(), 0xFFFFFFFF.toInt(), 0xFFDFE1F0.toInt(), 0xFF181920.toInt(),
                0xFF775469.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFD7E6.toInt(), 0xFF2D1125.toInt(),
                0xFFFDFBFF.toInt(), 0xFF1B1B1F.toInt(), 0xFFFDFBFF.toInt(), 0xFF1B1B1F.toInt(),
                0xFFE3E1EC.toInt(), 0xFF46464F.toInt(), 0xFF767680.toInt(), 0xFFC6C5D0.toInt(),
                0xFFFFFFFF.toInt(), 0xFFF4F3FA.toInt(), 0xFFECEAF1.toInt(), 0xFFE6E4EB.toInt(), 0xFFE0DFE6.toInt(),
                0xFF425980.toInt(), 0xFF5A5C66.toInt(), 0xFF425980.toInt(), 0xFF5A5C66.toInt(), 0xFF775469.toInt(), 0xFFBA1A1A.toInt(),
            )),
            // 8 极简黑白
            ThemePalette("极简黑白", intArrayOf(
                0xFF1A1A1A.toInt(), 0xFFFFFFFF.toInt(), 0xFF3A3A3A.toInt(), 0xFFFFFFFF.toInt(),
                0xFF4A4A4A.toInt(), 0xFFFFFFFF.toInt(), 0xFFD6D6D6.toInt(), 0xFF1A1A1A.toInt(),
                0xFF6A6A6A.toInt(), 0xFFFFFFFF.toInt(), 0xFFC8C8C8.toInt(), 0xFF1A1A1A.toInt(),
                0xFFFAFAFA.toInt(), 0xFF1A1A1A.toInt(), 0xFFFAFAFA.toInt(), 0xFF1A1A1A.toInt(),
                0xFFDDDDDD.toInt(), 0xFF4A4A4A.toInt(), 0xFF7A7A7A.toInt(), 0xFFC8C8C8.toInt(),
                0xFFFFFFFF.toInt(), 0xFFF5F5F5.toInt(), 0xFFEFEFEF.toInt(), 0xFFE9E9E9.toInt(), 0xFFE3E3E3.toInt(),
                0xFF1A1A1A.toInt(), 0xFF4A4A4A.toInt(), 0xFF1A1A1A.toInt(), 0xFF4A4A4A.toInt(), 0xFF6A6A6A.toInt(), 0xFFBA1A1A.toInt(),
            )),
            // 9 电报蓝
            ThemePalette("电报蓝", intArrayOf(
                0xFF0088CC.toInt(), 0xFFFFFFFF.toInt(), 0xFFB3E5FC.toInt(), 0xFF003847.toInt(),
                0xFF51606D.toInt(), 0xFFFFFFFF.toInt(), 0xFFD5E2E8.toInt(), 0xFF0E1D24.toInt(),
                0xFF7A5746.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFDCC8.toInt(), 0xFF2A1709.toInt(),
                0xFFF7FBFC.toInt(), 0xFF181C1E.toInt(), 0xFFF7FBFC.toInt(), 0xFF181C1E.toInt(),
                0xFFD8E3E7.toInt(), 0xFF3F484B.toInt(), 0xFF6F797C.toInt(), 0xFFBEC8CB.toInt(),
                0xFFFFFFFF.toInt(), 0xFFF1F6F8.toInt(), 0xFFEBF0F2.toInt(), 0xFFE5EBEE.toInt(), 0xFFDFE5E9.toInt(),
                0xFF0088CC.toInt(), 0xFF51606D.toInt(), 0xFF0088CC.toInt(), 0xFF51606D.toInt(), 0xFF7A5746.toInt(), 0xFFBA1A1A.toInt(),
            )),
            // 10 暮光紫
            ThemePalette("暮光紫", intArrayOf(
                0xFF5865F2.toInt(), 0xFFFFFFFF.toInt(), 0xFFE0E5FF.toInt(), 0xFF001257.toInt(),
                0xFF5A5D72.toInt(), 0xFFFFFFFF.toInt(), 0xFFE1E2F5.toInt(), 0xFF171A2E.toInt(),
                0xFF7D5260.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFD8E4.toInt(), 0xFF31111D.toInt(),
                0xFFFBF8FF.toInt(), 0xFF1B1B21.toInt(), 0xFFFBF8FF.toInt(), 0xFF1B1B21.toInt(),
                0xFFE3E1EC.toInt(), 0xFF45464F.toInt(), 0xFF767680.toInt(), 0xFFC6C5D0.toInt(),
                0xFFFFFFFF.toInt(), 0xFFF4F3FA.toInt(), 0xFFECEAF1.toInt(), 0xFFE6E4EB.toInt(), 0xFFE0DFE6.toInt(),
                0xFF5865F2.toInt(), 0xFF5A5D72.toInt(), 0xFF5865F2.toInt(), 0xFF5A5D72.toInt(), 0xFF7D5260.toInt(), 0xFFBA1A1A.toInt(),
            )),
            // 11 优雅灰白
            ThemePalette("优雅灰白", intArrayOf(
                0xFF5C5C5C.toInt(), 0xFFFFFFFF.toInt(), 0xFFD0D0D0.toInt(), 0xFF1A1A1A.toInt(),
                0xFF8C8C8C.toInt(), 0xFFFFFFFF.toInt(), 0xFFE0E0E0.toInt(), 0xFF1A1A1A.toInt(),
                0xFF707070.toInt(), 0xFFFFFFFF.toInt(), 0xFFD8D8D8.toInt(), 0xFF1A1A1A.toInt(),
                0xFFFCFCFC.toInt(), 0xFF1A1A1A.toInt(), 0xFFFCFCFC.toInt(), 0xFF1A1A1A.toInt(),
                0xFFE0E0E0.toInt(), 0xFF4A4A4A.toInt(), 0xFF808080.toInt(), 0xFFCCCCCC.toInt(),
                0xFFFFFFFF.toInt(), 0xFFF7F7F7.toInt(), 0xFFF1F1F1.toInt(), 0xFFEBEBEB.toInt(), 0xFFE5E5E5.toInt(),
                0xFF5C5C5C.toInt(), 0xFF8C8C8C.toInt(), 0xFF5C5C5C.toInt(), 0xFF8C8C8C.toInt(), 0xFF707070.toInt(), 0xFFBA1A1A.toInt(),
            )),
        )

        // 12 套预设（暗色）
        val PRESETS_DARK: List<ThemePalette> = listOf(
            // 0 珊瑚橙
            ThemePalette("珊瑚橙", intArrayOf(
                0xFFFFB59E.toInt(), 0xFF141210.toInt(), 0xFF7A2F15.toInt(), 0xFFFFDBD0.toInt(),
                0xFFFFCBA4.toInt(), 0xFF141210.toInt(), 0xFF5A431F.toInt(), 0xFFFFDDB1.toInt(),
                0xFF9FD49B.toInt(), 0xFF003A0E.toInt(), 0xFF215025.toInt(), 0xFFBAF0B6.toInt(),
                0xFF141210.toInt(), 0xFFEAE1D8.toInt(), 0xFF1C1A18.toInt(), 0xFFEAE1D8.toInt(),
                0xFF55433D.toInt(), 0xFFDBC1B9.toInt(), 0xFFA08C85.toInt(), 0xFF55433D.toInt(),
                0xFF1F1B16.toInt(), 0xFF2B2620.toInt(), 0xFF332E28.toInt(), 0xFF3D3830.toInt(), 0xFF4A443C.toInt(),
                0xFFFFB59E.toInt(), 0xFFE3C193.toInt(), 0xFFFFB59E.toInt(), 0xFFE3C193.toInt(), 0xFF9FD49B.toInt(), 0xFFFFB4AB.toInt(),
            )),
            // 1 靛蓝
            ThemePalette("靛蓝", intArrayOf(
                0xFFB2C5FF.toInt(), 0xFF002B75.toInt(), 0xFF003F9D.toInt(), 0xFFDCE2FF.toInt(),
                0xFFC2C5FF.toInt(), 0xFF282C5A.toInt(), 0xFF3F4377.toInt(), 0xFFE1E1FF.toInt(),
                0xFFE6B8D6.toInt(), 0xFF45263D.toInt(), 0xFF5D3E54.toInt(), 0xFFFFD7F0.toInt(),
                0xFF15151C.toInt(), 0xFFC5C5D2.toInt(), 0xFF191920.toInt(), 0xFFC5C5D2.toInt(),
                0xFF2A2A33.toInt(), 0xFFC0BFD4.toInt(), 0xFF908F99.toInt(), 0xFF44444F.toInt(),
                0xFF101017.toInt(), 0xFF1E1E24.toInt(), 0xFF25252C.toInt(), 0xFF2C2C33.toInt(), 0xFF36363D.toInt(),
                0xFFB2C5FF.toInt(), 0xFFC2C5FF.toInt(), 0xFFB2C5FF.toInt(), 0xFFC2C5FF.toInt(), 0xFFE6B8D6.toInt(), 0xFFFFB4AB.toInt(),
            )),
            // 2 森绿
            ThemePalette("森绿", intArrayOf(
                0xFF95D78F.toInt(), 0xFF003A04.toInt(), 0xFF155218.toInt(), 0xFFB0F3A9.toInt(),
                0xFFB9CCB2.toInt(), 0xFF243422.toInt(), 0xFF3A4B37.toInt(), 0xFFD5E8CD.toInt(),
                0xFFA0D0D0.toInt(), 0xFF003636.toInt(), 0xFF1F4E4D.toInt(), 0xFFBCECEB.toInt(),
                0xFF141411.toInt(), 0xFFE3E4DD.toInt(), 0xFF141411.toInt(), 0xFFE3E4DD.toInt(),
                0xFF2A2F26.toInt(), 0xFFBFC7B6.toInt(), 0xFF8B9384.toInt(), 0xFF40473D.toInt(),
                0xFF0E0F0C.toInt(), 0xFF1A1B17.toInt(), 0xFF20211C.toInt(), 0xFF262722.toInt(), 0xFF30312B.toInt(),
                0xFF95D78F.toInt(), 0xFFB9CCB2.toInt(), 0xFF95D78F.toInt(), 0xFFB9CCB2.toInt(), 0xFFA0D0D0.toInt(), 0xFFFFB4AB.toInt(),
            )),
            // 3 紫罗兰
            ThemePalette("紫罗兰", intArrayOf(
                0xFFD0BCFF.toInt(), 0xFF381E72.toInt(), 0xFF4F378B.toInt(), 0xFFEADDFF.toInt(),
                0xFFCCC2DC.toInt(), 0xFF332D41.toInt(), 0xFF4A4458.toInt(), 0xFFE8DEF8.toInt(),
                0xFFEFB8C8.toInt(), 0xFF492532.toInt(), 0xFF633B48.toInt(), 0xFFFFD8E4.toInt(),
                0xFF1C1B1F.toInt(), 0xFFE6E1E5.toInt(), 0xFF1C1B1F.toInt(), 0xFFE6E1E5.toInt(),
                0xFF313033.toInt(), 0xFFCAC4D0.toInt(), 0xFF938F99.toInt(), 0xFF49454F.toInt(),
                0xFF151418.toInt(), 0xFF222026.toInt(), 0xFF2A282E.toInt(), 0xFF322F37.toInt(), 0xFF3C3940.toInt(),
                0xFFD0BCFF.toInt(), 0xFFCCC2DC.toInt(), 0xFFD0BCFF.toInt(), 0xFFCCC2DC.toInt(), 0xFFEFB8C8.toInt(), 0xFFFFB4AB.toInt(),
            )),
            // 4 樱桃红
            ThemePalette("樱桃红", intArrayOf(
                0xFFFFB4AB.toInt(), 0xFF690005.toInt(), 0xFF93000A.toInt(), 0xFFFFDAD6.toInt(),
                0xFFE7BDB6.toInt(), 0xFF442722.toInt(), 0xFF5D3D37.toInt(), 0xFFFFDAD4.toInt(),
                0xFFE2C48B.toInt(), 0xFF3E2D04.toInt(), 0xFF574419.toInt(), 0xFFFFDEA8.toInt(),
                0xFF201514.toInt(), 0xFFEDE0DE.toInt(), 0xFF201514.toInt(), 0xFFEDE0DE.toInt(),
                0xFF382725.toInt(), 0xFFD8C2BE.toInt(), 0xFFA08C8A.toInt(), 0xFF534341.toInt(),
                0xFF170F0E.toInt(), 0xFF251715.toInt(), 0xFF2D1E1C.toInt(), 0xFF372625.toInt(), 0xFF443230.toInt(),
                0xFFFFB4AB.toInt(), 0xFFE7BDB6.toInt(), 0xFFFFB4AB.toInt(), 0xFFE7BDB6.toInt(), 0xFFE2C48B.toInt(), 0xFFFFB4AB.toInt(),
            )),
            // 5 海洋蓝
            ThemePalette("海洋蓝", intArrayOf(
                0xFF7FDBEE.toInt(), 0xFF00363F.toInt(), 0xFF004F5A.toInt(), 0xFF96F0FF.toInt(),
                0xFFB0CCD2.toInt(), 0xFF1D3439.toInt(), 0xFF354B50.toInt(), 0xFFCCE8ED.toInt(),
                0xFFB8C4FF.toInt(), 0xFF192B57.toInt(), 0xFF334370.toInt(), 0xFFDDE2FF.toInt(),
                0xFF101415.toInt(), 0xFFE1E3E4.toInt(), 0xFF101415.toInt(), 0xFFE1E3E4.toInt(),
                0xFF273032.toInt(), 0xFFBEC8CB.toInt(), 0xFF899296.toInt(), 0xFF3F484B.toInt(),
                0xFF0B0F10.toInt(), 0xFF151A1B.toInt(), 0xFF1C2122.toInt(), 0xFF24292A.toInt(), 0xFF2E3335.toInt(),
                0xFF7FDBEE.toInt(), 0xFFB0CCD2.toInt(), 0xFF7FDBEE.toInt(), 0xFFB0CCD2.toInt(), 0xFFB8C4FF.toInt(), 0xFFFFB4AB.toInt(),
            )),
            // 6 沙漠金
            ThemePalette("沙漠金", intArrayOf(
                0xFFFFB86A.toInt(), 0xFF422A00.toInt(), 0xFF604000.toInt(), 0xFFFFDDB2.toInt(),
                0xFFDEC4A1.toInt(), 0xFF3D2E15.toInt(), 0xFF57442A.toInt(), 0xFFFBE0BA.toInt(),
                0xFFB8CE8E.toInt(), 0xFF28360B.toInt(), 0xFF3E4D1F.toInt(), 0xFFD5EBAD.toInt(),
                0xFF1F1B13.toInt(), 0xFFEAE1D0.toInt(), 0xFF1F1B13.toInt(), 0xFFEAE1D0.toInt(),
                0xFF342F24.toInt(), 0xFFCDC5B4.toInt(), 0xFFA29A8A.toInt(), 0xFF4C4639.toInt(),
                0xFF15130D.toInt(), 0xFF28231B.toInt(), 0xFF302B21.toInt(), 0xFF3A3429.toInt(), 0xFF453F33.toInt(),
                0xFFFFB86A.toInt(), 0xFFDEC4A1.toInt(), 0xFFFFB86A.toInt(), 0xFFDEC4A1.toInt(), 0xFFB8CE8E.toInt(), 0xFFFFB4AB.toInt(),
            )),
            // 7 石墨灰
            ThemePalette("石墨灰", intArrayOf(
                0xFFB2C5FF.toInt(), 0xFF002B75.toInt(), 0xFF2540A6.toInt(), 0xFFDCE2FF.toInt(),
                0xFFC4C5DD.toInt(), 0xFF2C2D37.toInt(), 0xFF43444F.toInt(), 0xFFDFE1F0.toInt(),
                0xFFFFB1CB.toInt(), 0xFF492536.toInt(), 0xFF633B4C.toInt(), 0xFFFFD7E6.toInt(),
                0xFF1A1B20.toInt(), 0xFFE4E1F6.toInt(), 0xFF1A1B20.toInt(), 0xFFE4E1F6.toInt(),
                0xFF292931.toInt(), 0xFFC0BFD4.toInt(), 0xFF908F9D.toInt(), 0xFF44444F.toInt(),
                0xFF141518.toInt(), 0xFF1E1F24.toInt(), 0xFF25262B.toInt(), 0xFF2D2E33.toInt(), 0xFF38383E.toInt(),
                0xFFB2C5FF.toInt(), 0xFFC4C5DD.toInt(), 0xFFB2C5FF.toInt(), 0xFFC4C5DD.toInt(), 0xFFFFB1CB.toInt(), 0xFFFFB4AB.toInt(),
            )),
            // 8 极简黑白
            ThemePalette("极简黑白", intArrayOf(
                0xFFE0E0E0.toInt(), 0xFF1A1A1A.toInt(), 0xFFA0A0A0.toInt(), 0xFF2A2A2A.toInt(),
                0xFFB0B0B0.toInt(), 0xFF2A2A2A.toInt(), 0xFF6A6A6A.toInt(), 0xFF1A1A1A.toInt(),
                0xFF909090.toInt(), 0xFF1A1A1A.toInt(), 0xFF808080.toInt(), 0xFF2A2A2A.toInt(),
                0xFF1A1A1A.toInt(), 0xFFE0E0E0.toInt(), 0xFF1A1A1A.toInt(), 0xFFE0E0E0.toInt(),
                0xFF2A2A2A.toInt(), 0xFFB0B0B0.toInt(), 0xFF808080.toInt(), 0xFF404040.toInt(),
                0xFF0E0E0E.toInt(), 0xFF1A1A1A.toInt(), 0xFF242424.toInt(), 0xFF2E2E2E.toInt(), 0xFF383838.toInt(),
                0xFFE0E0E0.toInt(), 0xFFB0B0B0.toInt(), 0xFFE0E0E0.toInt(), 0xFFB0B0B0.toInt(), 0xFF909090.toInt(), 0xFFFFB4AB.toInt(),
            )),
            // 9 电报蓝
            ThemePalette("电报蓝", intArrayOf(
                0xFF6FC8F0.toInt(), 0xFF003544.toInt(), 0xFF004F66.toInt(), 0xFFB3E5FC.toInt(),
                0xFFB0C8D2.toInt(), 0xFF1A2A30.toInt(), 0xFF384B53.toInt(), 0xFFD5E2E8.toInt(),
                0xFFE2C48B.toInt(), 0xFF3E2D04.toInt(), 0xFF574419.toInt(), 0xFFFFDCC8.toInt(),
                0xFF0F1517.toInt(), 0xFFDEE7EA.toInt(), 0xFF0F1517.toInt(), 0xFFDEE7EA.toInt(),
                0xFF252E30.toInt(), 0xFFBEC8CB.toInt(), 0xFF899296.toInt(), 0xFF3F484B.toInt(),
                0xFF0A0F10.toInt(), 0xFF141A1B.toInt(), 0xFF1C2122.toInt(), 0xFF24292A.toInt(), 0xFF2E3335.toInt(),
                0xFF6FC8F0.toInt(), 0xFFB0C8D2.toInt(), 0xFF6FC8F0.toInt(), 0xFFB0C8D2.toInt(), 0xFFE2C48B.toInt(), 0xFFFFB4AB.toInt(),
            )),
            // 10 暮光紫
            ThemePalette("暮光紫", intArrayOf(
                0xFFC7D0FF.toInt(), 0xFF001A72.toInt(), 0xFF3A47B5.toInt(), 0xFFE0E5FF.toInt(),
                0xFFC2C4DC.toInt(), 0xFF2A2D42.toInt(), 0xFF43476A.toInt(), 0xFFE1E2F5.toInt(),
                0xFFEFB8C8.toInt(), 0xFF492532.toInt(), 0xFF633B48.toInt(), 0xFFFFD8E4.toInt(),
                0xFF15161B.toInt(), 0xFFE4E2EC.toInt(), 0xFF15161B.toInt(), 0xFFE4E2EC.toInt(),
                0xFF2A2A33.toInt(), 0xFFC0BFD4.toInt(), 0xFF908F99.toInt(), 0xFF44444F.toInt(),
                0xFF101014.toInt(), 0xFF1E1E24.toInt(), 0xFF25252C.toInt(), 0xFF2C2C33.toInt(), 0xFF36363D.toInt(),
                0xFFC7D0FF.toInt(), 0xFFC2C4DC.toInt(), 0xFFC7D0FF.toInt(), 0xFFC2C4DC.toInt(), 0xFFEFB8C8.toInt(), 0xFFFFB4AB.toInt(),
            )),
            // 11 优雅灰白
            ThemePalette("优雅灰白", intArrayOf(
                0xFFCCCCCC.toInt(), 0xFF2A2A2A.toInt(), 0xFF808080.toInt(), 0xFF1A1A1A.toInt(),
                0xFF9C9C9C.toInt(), 0xFF1A1A1A.toInt(), 0xFF5C5C5C.toInt(), 0xFF2A2A2A.toInt(),
                0xFFB0B0B0.toInt(), 0xFF1A1A1A.toInt(), 0xFF909090.toInt(), 0xFF2A2A2A.toInt(),
                0xFF1F1F1F.toInt(), 0xFFCCCCCC.toInt(), 0xFF1F1F1F.toInt(), 0xFFCCCCCC.toInt(),
                0xFF303030.toInt(), 0xFF9C9C9C.toInt(), 0xFF6A6A6A.toInt(), 0xFF3A3A3A.toInt(),
                0xFF161616.toInt(), 0xFF1F1F1F.toInt(), 0xFF262626.toInt(), 0xFF2E2E2E.toInt(), 0xFF363636.toInt(),
                0xFFCCCCCC.toInt(), 0xFF9C9C9C.toInt(), 0xFFCCCCCC.toInt(), 0xFF9C9C9C.toInt(), 0xFFB0B0B0.toInt(), 0xFFFFB4AB.toInt(),
            )),
        )

        /** 显示顺序映射：显示位 → 底层存储索引（按色系渐变排列） */
        private val PRESET_DISPLAY_ORDER = intArrayOf(
            4, 0, 6, 2, 5, 9, 1, 3, 10, 7, 11, 8,
        )

        fun displayIndexToStored(displayIndex: Int): Int =
            if (displayIndex in PRESET_DISPLAY_ORDER.indices) PRESET_DISPLAY_ORDER[displayIndex] else 0

        fun storedIndexToDisplay(storedIndex: Int): Int =
            PRESET_DISPLAY_ORDER.indexOf(storedIndex).let { if (it < 0) 0 else it }

        fun getPreset(index: Int, dark: Boolean): ThemePalette {
            val arr = if (dark) PRESETS_DARK else PRESETS_LIGHT
            return arr[if (index in arr.indices) index else 0]
        }

        fun getPresetByDisplay(displayIndex: Int, dark: Boolean): ThemePalette =
            getPreset(displayIndexToStored(displayIndex), dark)

        /**
         * 基于一个主色，用色彩调和算法生成完整 Material 3 风格色板
         * （移植自旧版 ThemeManager.generateFromPrimary）。
         */
        fun generateFromPrimary(primary: Int, dark: Boolean): ThemePalette {
            val hsv = FloatArray(3)
            Color.colorToHSV(primary, hsv)
            val h = hsv[0]
            val s = hsv[1]
            val v = hsv[2]

            val onPrimary = if (v > 0.5f) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            val primaryContainer = if (dark) adjust(primary, 0f, -0.15f, -0.3f)
            else adjust(primary, 0f, -0.3f, 0.3f)
            val onPrimaryContainer = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()

            val secondary = adjust(primary, 15f, -0.3f, 0f)
            val onSecondary = if (isLight(secondary)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            val secondaryContainer = if (dark) adjust(secondary, 0f, -0.1f, -0.3f)
            else adjust(secondary, 0f, -0.3f, 0.3f)
            val onSecondaryContainer = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()

            val tertiary = adjust(primary, 120f, -0.2f, 0f)
            val onTertiary = if (isLight(tertiary)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            val tertiaryContainer = if (dark) adjust(tertiary, 0f, -0.1f, -0.3f)
            else adjust(tertiary, 0f, -0.3f, 0.3f)
            val onTertiaryContainer = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()

            val background: Int
            val onBackground: Int
            val surface: Int
            val onSurface: Int
            val surfaceVariant: Int
            val onSurfaceVariant: Int
            val outline: Int
            val outlineVariant: Int
            val scLowest: Int
            val scLow: Int
            val sc: Int
            val scHigh: Int
            val scHighest: Int

            if (dark) {
                background = adjust(primary, 0f, -s * 0.9f, 0.08f)
                onBackground = 0xFFE5E5EA.toInt()
                surface = adjust(primary, 0f, -s * 0.9f, 0.10f)
                onSurface = 0xFFE5E5EA.toInt()
                surfaceVariant = adjust(primary, 0f, -s * 0.7f, 0.20f)
                onSurfaceVariant = 0xFFC5C5CE.toInt()
                outline = adjust(primary, 0f, -s * 0.5f, 0.55f)
                outlineVariant = adjust(primary, 0f, -s * 0.8f, 0.25f)
                scLowest = adjust(primary, 0f, -s * 0.9f, 0.06f)
                scLow = adjust(primary, 0f, -s * 0.9f, 0.12f)
                sc = adjust(primary, 0f, -s * 0.9f, 0.16f)
                scHigh = adjust(primary, 0f, -s * 0.85f, 0.20f)
                scHighest = adjust(primary, 0f, -s * 0.8f, 0.25f)
            } else {
                background = adjust(primary, 0f, -s * 0.95f, 0.97f)
                onBackground = 0xFF1A1A1F.toInt()
                surface = adjust(primary, 0f, -s * 0.95f, 0.98f)
                onSurface = 0xFF1A1A1F.toInt()
                surfaceVariant = adjust(primary, 0f, -s * 0.7f, 0.90f)
                onSurfaceVariant = 0xFF45464F.toInt()
                outline = adjust(primary, 0f, -s * 0.5f, 0.50f)
                outlineVariant = adjust(primary, 0f, -s * 0.85f, 0.80f)
                scLowest = 0xFFFFFFFF.toInt()
                scLow = adjust(primary, 0f, -s * 0.95f, 0.96f)
                sc = adjust(primary, 0f, -s * 0.95f, 0.94f)
                scHigh = adjust(primary, 0f, -s * 0.9f, 0.92f)
                scHighest = adjust(primary, 0f, -s * 0.85f, 0.88f)
            }

            val chart = IntArray(6)
            chart[0] = if (dark) lighten(primary, 0.3f) else primary
            chart[1] = if (dark) lighten(secondary, 0.3f) else secondary
            chart[2] = if (dark) lighten(primary, 0.3f) else primary
            chart[3] = if (dark) lighten(secondary, 0.3f) else secondary
            chart[4] = if (dark) lighten(tertiary, 0.3f) else tertiary
            chart[5] = if (dark) 0xFFFFB4AB.toInt() else 0xFFBA1A1A.toInt()

            return ThemePalette(
                "自定义",
                intArrayOf(
                    primary, onPrimary, primaryContainer, onPrimaryContainer,
                    secondary, onSecondary, secondaryContainer, onSecondaryContainer,
                    tertiary, onTertiary, tertiaryContainer, onTertiaryContainer,
                    background, onBackground, surface, onSurface,
                    surfaceVariant, onSurfaceVariant, outline, outlineVariant,
                    scLowest, scLow, sc, scHigh, scHighest,
                    chart[0], chart[1], chart[2], chart[3], chart[4], chart[5],
                ),
            )
        }

        private fun adjust(color: Int, hueDelta: Float, satDelta: Float, valDelta: Float): Int {
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            hsv[0] = (hsv[0] + hueDelta + 360) % 360
            hsv[1] = (hsv[1] + satDelta).coerceIn(0f, 1f)
            hsv[2] = (hsv[2] + valDelta).coerceIn(0f, 1f)
            return Color.HSVToColor(Color.alpha(color), hsv)
        }

        private fun lighten(color: Int, amount: Float): Int = adjust(color, 0f, 0f, amount)

        private fun isLight(color: Int): Boolean {
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val lum = 0.299 * r + 0.587 * g + 0.114 * b
            return lum > 140
        }
    }
}

/** 主题持久化存储 */
object ThemeStore {

    private const val PREFS_NAME = "crps_prefs"
    private const val KEY_PRESET_INDEX = "theme_preset_index"
    private const val KEY_DARK_MODE = "theme_dark_mode"

    /** 夜间模式：-1 跟随系统 / 0 亮色 / 1 暗色 */
    const val DARK_FOLLOW_SYSTEM = -1
    const val DARK_LIGHT = 0
    const val DARK_DARK = 1

    fun loadPresetIndex(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_PRESET_INDEX, 7)

    fun savePresetIndex(context: Context, index: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_PRESET_INDEX, index).apply()
    }

    fun loadDarkMode(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_DARK_MODE, DARK_FOLLOW_SYSTEM)

    fun saveDarkMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_DARK_MODE, mode).apply()
    }
}
