package com.xabber.presentation.application.activity;

import android.app.Activity;
import android.graphics.Rect;
import android.view.View;

public class KeyboardHeightHelper {
    private final View decorView;
    private int lastKeyboardHeight = -1;

    public KeyboardHeightHelper(Activity activity, View activityRootView, OnKeyboardHeightChangeListener listener) {
        this.decorView = activity.getWindow().getDecorView();
        activityRootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            int keyboardHeight = getKeyboardHeight();
            if (lastKeyboardHeight != keyboardHeight) {
                lastKeyboardHeight = keyboardHeight;
                listener.onKeyboardHeightChange(keyboardHeight);
            }
        });
    }

    private int getKeyboardHeight() {
        Rect rect = new Rect();
        decorView.getWindowVisibleDisplayFrame(rect);
        return decorView.getHeight() - rect.bottom;
    }

    public interface OnKeyboardHeightChangeListener {
        void onKeyboardHeightChange(int keyboardHeight);
    }
}
