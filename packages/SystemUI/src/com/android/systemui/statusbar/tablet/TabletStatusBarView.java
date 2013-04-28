/*
 * Copyright (C) 2010 The Android Open Source Project
 * This code has been modified. Portions copyright (C) 2012, ParanoidAndroid Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.tablet;

import com.android.systemui.R;
import com.android.systemui.statusbar.BackgroundAlphaColorDrawable;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.DelegateViewHelper;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.View;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import java.util.List;


public class TabletStatusBarView extends FrameLayout {
    private Handler mHandler;
    
    ActivityManager mActivityManager;
    KeyguardManager mKeyguardManager;
    
    private float mAlpha;
    private int mAlphaMode;
    int mStatusBarColor;
    
    private Runnable mUpdateInHomeAlpha = new Runnable() {
       @Override
       public void run() {
           new AsyncTask<Void, Void, Boolean>() {
               @Override
               protected Boolean doInBackground(Void... params) {
                   final List<ActivityManager.RecentTaskInfo> recentTasks = mActivityManager.getRecentTasksForUser(
                         1, ActivityManager.RECENT_IGNORE_UNAVAILABLE, UserHandle.CURRENT.getIdentifier());
                   if (recentTasks != null && recentTasks.size() > 0) {
                       ActivityManager.RecentTaskInfo recentInfo = recentTasks.get(0);
                       Intent intent = new Intent(recentInfo.baseIntent);
                   if (recentInfo.origActivity != null) {
                       intent.setComponent(recentInfo.origActivity);
                   }
                   if (isCurrentHomeActivity(intent.getComponent(), null)) {
                       return true;
                   }
               }
               return false;
            }

            @Override
            protected void onPostExecute(Boolean inHome) {
                setBackgroundAlpha(inHome ? mAlpha : 1);
                Settings.System.putInt(getContext().getContentResolver(),
                    Settings.System.IS_HOME, inHome ? 1 : 0);
             }
         }.execute();
       }
    };

    private final int MAX_PANELS = 5;
    private final View[] mIgnoreChildren = new View[MAX_PANELS];
    private final View[] mPanels = new View[MAX_PANELS];
    private final int[] mPos = new int[2];
    private DelegateViewHelper mDelegateHelper;

    public TabletStatusBarView(Context context) {
        this(context, null);
    }

    public TabletStatusBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDelegateHelper = new DelegateViewHelper(this);
        
        mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        mKeyguardManager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        SettingsObserver settingsObserver = new SettingsObserver(new Handler());
        settingsObserver.observe();
        updateSettings();
        Drawable bg = mContext.getResources().getDrawable(R.drawable.status_bar_background);
        if(bg instanceof ColorDrawable) {
            BackgroundAlphaColorDrawable bacd = new BackgroundAlphaColorDrawable(
            mStatusBarColor != -1 ? mStatusBarColor : ((ColorDrawable) bg).getColor());
            setBackground(bacd);
        }
        updateSettings();
        
    }

    public void setDelegateView(View view) {
        mDelegateHelper.setDelegateView(view);
    }

    public void setBar(BaseStatusBar phoneStatusBar) {
        mDelegateHelper.setBar(phoneStatusBar);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mDelegateHelper != null) {
            mDelegateHelper.onInterceptTouchEvent(event);
        }
        return true;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // Find the view we wish to grab events from in order to detect search gesture.
        // Depending on the device, this will be one of the id's listed below.
        // If we don't find one, we'll use the view provided in the constructor above (this view).
        View view = findViewById(R.id.navigationArea);
        if (view == null) {
            view = findViewById(R.id.nav_buttons);
        }
        mDelegateHelper.setSourceView(view);
        mDelegateHelper.setInitialTouchRegion(view);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            if (TabletStatusBar.DEBUG) {
                Slog.d(TabletStatusBar.TAG, "TabletStatusBarView intercepting touch event: " + ev);
            }
            // do not close the recents panel here- the intended behavior is that recents is dismissed
            // on touch up when clicking on status bar buttons
            // TODO: should we be closing the notification panel and input methods panel?
            mHandler.removeMessages(TabletStatusBar.MSG_CLOSE_NOTIFICATION_PANEL);
            mHandler.sendEmptyMessage(TabletStatusBar.MSG_CLOSE_NOTIFICATION_PANEL);
            mHandler.removeMessages(TabletStatusBar.MSG_CLOSE_INPUT_METHODS_PANEL);
            mHandler.sendEmptyMessage(TabletStatusBar.MSG_CLOSE_INPUT_METHODS_PANEL);
            mHandler.removeMessages(TabletStatusBar.MSG_STOP_TICKER);
            mHandler.sendEmptyMessage(TabletStatusBar.MSG_STOP_TICKER);

            for (int i=0; i < mPanels.length; i++) {
                if (mPanels[i] != null && mPanels[i].getVisibility() == View.VISIBLE) {
                    if (eventInside(mIgnoreChildren[i], ev)) {
                        if (TabletStatusBar.DEBUG) {
                            Slog.d(TabletStatusBar.TAG,
                                    "TabletStatusBarView eating event for view: "
                                    + mIgnoreChildren[i]);
                        }
                        return true;
                    }
                }
            }
        }
        if (TabletStatusBar.DEBUG) {
            Slog.d(TabletStatusBar.TAG, "TabletStatusBarView not intercepting event");
        }
        if (mDelegateHelper != null && mDelegateHelper.onInterceptTouchEvent(ev)) {
            return true;
        }
        return super.onInterceptTouchEvent(ev);
    }

    private boolean eventInside(View v, MotionEvent ev) {
        // assume that x and y are window coords because we are.
        final int x = (int)ev.getX();
        final int y = (int)ev.getY();

        final int[] p = mPos;
        v.getLocationInWindow(p);

        final int l = p[0];
        final int t = p[1];
        final int r = p[0] + v.getWidth();
        final int b = p[1] + v.getHeight();

        return x >= l && x < r && y >= t && y < b;
    }

    public void setHandler(Handler h) {
        mHandler = h;
    }

    /**
     * Let the status bar know that if you tap on ignore while panel is showing, don't do anything.
     *
     * Debounces taps on, say, a popup's trigger when the popup is already showing.
     */
    public void setIgnoreChildren(int index, View ignore, View panel) {
        mIgnoreChildren[index] = ignore;
        mPanels[index] = panel;
    }
    
    private boolean isKeyguardEnabled() {
        if(mKeyguardManager == null) return false;
        return mKeyguardManager.isKeyguardLocked();
    }
    
    /*
     * ]0 < alpha < 1[
     */
    protected void setBackgroundAlpha(float alpha) {
        Drawable bg = getBackground();
        if (bg == null)
            return;
        
        if(bg instanceof BackgroundAlphaColorDrawable) {
            ((BackgroundAlphaColorDrawable) bg).setBgColor(mStatusBarColor);
        }
        int a = (int) (alpha * 255);
        bg.setAlpha(a);
    }
    
    public void updateBackgroundAlpha() {
        if(isKeyguardEnabled() && mAlphaMode == 0) {
            setBackgroundAlpha(1);
        } else if (isKeyguardEnabled() || mAlphaMode == 2) {
            setBackgroundAlpha(mAlpha);
        } else {
            removeCallbacks(mUpdateInHomeAlpha);
            postDelayed(mUpdateInHomeAlpha, 100);
        }
    }
    
    private boolean isCurrentHomeActivity(ComponentName component, ActivityInfo homeInfo) {
        if (homeInfo == null) {
            final PackageManager pm = mContext.getPackageManager();
            homeInfo = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .resolveActivityInfo(pm, 0);
        }
        return homeInfo != null
        && homeInfo.packageName.equals(component.getPackageName())
        && homeInfo.name.equals(component.getClassName());
    }
    
    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }
        
        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUS_BAR_ALPHA), false, this);
            resolver.registerContentObserver(
                     Settings.System.getUriFor(Settings.System.STATUS_NAV_BAR_ALPHA_MODE), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUS_BAR_COLOR), false, this);
            updateSettings();
        }
        
        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }
    
    protected void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        mAlpha = 1.0f - Settings.System.getFloat(mContext.getContentResolver(),
                                 Settings.System.STATUS_BAR_ALPHA,
                                                 0.0f);
        mAlphaMode = Settings.System.getInt(mContext.getContentResolver(),
                             Settings.System.STATUS_NAV_BAR_ALPHA_MODE, 1);
        mStatusBarColor = Settings.System.getInt(mContext.getContentResolver(),
                                  Settings.System.STATUS_BAR_COLOR, -1);
        
        updateBackgroundAlpha();
        
    }

}
