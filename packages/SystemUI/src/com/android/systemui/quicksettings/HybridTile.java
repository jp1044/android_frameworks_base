
package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class HybridTile extends QuickSettingsTile {

    public HybridTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container,
            final QuickSettingsController qsc, Handler handler) {
        super(context, inflater, container, qsc);


    	mOnClick = new View.OnClickListener() {
	   @Override
	   public void onClick(View v) {
	      Intent intent = new Intent("android.intent.action.MAIN");
	      intent.setClassName("com.android.settings", "com.android.settings.Settings$HybridSettingsActivity");
	
	   startSettingsActivity(intent);
	   }
   	};	
    }

    private synchronized void updateTile() {
        mDrawable = R.drawable.ic_qs_hybrid;
        mLabel = mContext.getString(R.string.quick_settings_hybrid);
    }
 	
}
