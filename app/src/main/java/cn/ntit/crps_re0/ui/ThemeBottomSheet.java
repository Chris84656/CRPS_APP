package cn.ntit.crps_re0.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import cn.ntit.crps_re0.R;
import cn.ntit.crps_re0.theme.ThemeManager;

public class ThemeBottomSheet extends BottomSheetDialogFragment {

    public interface OnApplyListener {
        void onApply(int presetIndex);
    }

    private OnApplyListener onApplyListener;
    private int selectedPresetIndex;
    private int selectedDisplayIndex;
    private GridLayout presetGrid;

    public void setOnApplyListener(OnApplyListener listener) {
        this.onApplyListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getContext() == null) return;
        selectedPresetIndex = ThemeManager.loadPresetIndex(getContext());
        if (selectedPresetIndex == ThemeManager.PRESET_CUSTOM) {
            selectedPresetIndex = 0;
        }
        selectedDisplayIndex = ThemeManager.storedIndexToDisplay(selectedPresetIndex);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        Context ctx = getContext();
        if (ctx == null) return dialog;

        float d = ctx.getResources().getDisplayMetrics().density;
        int dp4 = (int) (4 * d);
        int dp8 = (int) (8 * d);
        int dp12 = (int) (12 * d);
        int dp16 = (int) (16 * d);
        int dp24 = (int) (24 * d);
        int dp48 = (int) (48 * d);
        int dp64 = (int) (64 * d);

        ThemeManager.ThemeColorSet current = ThemeManager.getCurrentColors(ctx);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp16, dp16, dp16, dp16);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("主题配色");
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(current.onSurface);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpTitle.bottomMargin = dp16;
        root.addView(tvTitle, lpTitle);

        TextView tvPresets = new TextView(ctx);
        tvPresets.setText("预设方案");
        tvPresets.setTextSize(14f);
        tvPresets.setTextColor(current.onSurfaceVariant);
        LinearLayout.LayoutParams lpPresets = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpPresets.bottomMargin = dp12;
        root.addView(tvPresets, lpPresets);

        presetGrid = new GridLayout(ctx);
        presetGrid.setColumnCount(4);
        buildPresetGrid(ctx, d, dp64, dp48, dp8, current);
        LinearLayout.LayoutParams lpGrid = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpGrid.bottomMargin = dp24;
        root.addView(presetGrid, lpGrid);

        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);

        MaterialButton btnCancel = new MaterialButton(ctx, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnCancel.setText("取消");
        btnCancel.setOnClickListener(v -> dismiss());
        LinearLayout.LayoutParams lpCancel = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpCancel.rightMargin = dp12;
        btnRow.addView(btnCancel, lpCancel);

        MaterialButton btnApply = new MaterialButton(ctx);
        btnApply.setText("应用");
        btnApply.setOnClickListener(v -> {
            if (onApplyListener != null) {
                onApplyListener.onApply(ThemeManager.displayIndexToStored(selectedDisplayIndex));
            }
            dismiss();
        });
        btnRow.addView(btnApply, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams lpBtnRow = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpBtnRow.topMargin = dp8;
        root.addView(btnRow, lpBtnRow);

        dialog.setContentView(root);
        return dialog;
    }

    private void buildPresetGrid(Context ctx, float d, int dp64, int dp48, int dp8,
                                  ThemeManager.ThemeColorSet current) {
        presetGrid.removeAllViews();
        for (int i = 0; i < ThemeManager.getPresetCount(); i++) {
            ThemeManager.ThemeColorSet preset = ThemeManager.getPresetByDisplay(i, false);

            LinearLayout item = new LinearLayout(ctx);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);

            View colorBar = new View(ctx);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setColors(new int[]{preset.primary, preset.secondary});
            bg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
            bg.setCornerRadius(dp8 * 2);
            if (selectedDisplayIndex == i) {
                bg.setStroke((int) (3 * d), current.primary);
            }
            colorBar.setBackground(bg);
            LinearLayout.LayoutParams lpBar = new LinearLayout.LayoutParams(dp64, dp48);
            lpBar.bottomMargin = dp8 / 2;
            item.addView(colorBar, lpBar);

            TextView name = new TextView(ctx);
            name.setText(preset.name);
            name.setTextSize(12f);
            name.setTextColor(current.onSurface);
            item.addView(name, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            final int idx = i;
            item.setOnClickListener(v -> {
                selectedDisplayIndex = idx;
                selectedPresetIndex = ThemeManager.displayIndexToStored(idx);
                buildPresetGrid(ctx, d, dp64, dp48, dp8, current);
            });

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.columnSpec = GridLayout.spec(i % 4, 1f);
            lp.bottomMargin = dp8;
            presetGrid.addView(item, lp);
        }
    }
}
