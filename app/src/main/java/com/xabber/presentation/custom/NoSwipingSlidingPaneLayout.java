package com.xabber.presentation.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MotionEventCompat;
import androidx.slidingpanelayout.widget.SlidingPaneLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class NoSwipingSlidingPaneLayout extends SlidingPaneLayout {
    private boolean mSlideEnabled = true;
    private Field mSlideOffsetField = null;
    private Field mSlideableViewField = null;
    private Method updateObscuredViewsVisibilityMethod = null;
    private Method dispatchOnPanelOpenedMethod = null;
    private Method dispatchOnPanelClosedMethod = null;
    private Field mPreservedOpenStateField = null;
    private Method parallaxOtherViewsMethod = null;
    // Запретить ли боковое скольжение
    private boolean prohibitSideslip = false;

    public NoSwipingSlidingPaneLayout(@NonNull Context context) {
        super(context);
    }

    public NoSwipingSlidingPaneLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public NoSwipingSlidingPaneLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        try {
            mSlideOffsetField = SlidingPaneLayout.class.getDeclaredField("mSlideOffset");
            mSlideableViewField = SlidingPaneLayout.class.getDeclaredField("mSlideableView");
            updateObscuredViewsVisibilityMethod = SlidingPaneLayout.class.getDeclaredMethod("updateObscuredViewsVisibility",
                    View.class);
            dispatchOnPanelClosedMethod = SlidingPaneLayout.class.getDeclaredMethod("dispatchOnPanelClosed", View.class);
            dispatchOnPanelOpenedMethod = SlidingPaneLayout.class.getDeclaredMethod("dispatchOnPanelOpened", View.class);
            mPreservedOpenStateField = SlidingPaneLayout.class.getDeclaredField("mPreservedOpenState");
            parallaxOtherViewsMethod = SlidingPaneLayout.class.getDeclaredMethod("parallaxOtherViews", float.class);

            mSlideOffsetField.setAccessible(true);
            mSlideableViewField.setAccessible(true);
            updateObscuredViewsVisibilityMethod.setAccessible(true);
            dispatchOnPanelOpenedMethod.setAccessible(true);
            dispatchOnPanelClosedMethod.setAccessible(true);
            mPreservedOpenStateField.setAccessible(true);
            parallaxOtherViewsMethod.setAccessible(true);
        } catch (Exception e) {
            Log.w("ASPL", "Failed to set up animation-less sliding layout.");
        }
    }

    public void openPaneNoAnimation() {
        try {
            View slideableView = (View) mSlideableViewField.get(this);
            mSlideOffsetField.set(this, 1.0f);
            parallaxOtherViewsMethod.invoke(this, 1.0f);
            requestLayout();
            invalidate();
            dispatchOnPanelOpenedMethod.invoke(this, slideableView);
            mPreservedOpenStateField.set(this, true);
        } catch (Exception e) {
            openPane();
        }
    }

    public void closePaneNoAnimation() {
        try {
            View slideableView = (View) mSlideableViewField.get(this);
            mSlideOffsetField.set(this, 0.0f);
            parallaxOtherViewsMethod.invoke(this, 0.0f);
            requestLayout();
            invalidate();
            updateObscuredViewsVisibilityMethod.invoke(this, slideableView);
            dispatchOnPanelClosedMethod.invoke(this, slideableView);
            mPreservedOpenStateField.set(this, false);
        } catch (Exception e) {
            closePane();
        }
    }

    public boolean getProhibitSideslip() {
        return prohibitSideslip;
    }

    // Вызов этого метода там, где нужно запретить или разрешить боковое скольжение
    public void setProhibitSideslip(boolean prohibitSideslip) {
        this.prohibitSideslip = prohibitSideslip;
    }

    // Этот метод может перехватить событие сенсорного экрана SlidingPaneLayout
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (MotionEventCompat.getActionMasked(ev)) {
            case MotionEvent.ACTION_MOVE:
                if (prohibitSideslip) {
                    return false;
                }
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (MotionEventCompat.getActionMasked(ev)) {
            case MotionEvent.ACTION_MOVE:
                if (prohibitSideslip) {
                    return false;
                }
        }
        return super.onTouchEvent(ev);
    }
}
