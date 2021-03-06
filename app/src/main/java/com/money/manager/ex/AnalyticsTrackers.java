/*
 * Copyright (C) 2012-2016 The Android Money Manager Ex Project Team
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.money.manager.ex;

import android.content.Context;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;

import java.util.HashMap;
import java.util.Map;

/**
 * A collection of Google Analytics trackers. Fetch the tracker you need using
 * {@code AnalyticsTrackers.getInstance().get(...)}
 * <p/>
 * This code was generated by Android Studio but can be safely modified by
 * hand at this point.
 * <p/>
 * TODO: Call {@link #initialize(Context)} from an entry point in your app
 * before using this!
 */
public final class AnalyticsTrackers {

  public enum Target {
    APP,
    // Add more trackers here if you need, and update the code in #get(Target) below
  }

  private static AnalyticsTrackers sInstance;

  public static synchronized void initialize(Context context) {
    if (sInstance != null) {
      throw new IllegalStateException("Extra call to initialize analytics trackers");
    }

    sInstance = new AnalyticsTrackers(context);
  }

  public static synchronized AnalyticsTrackers getInstance() {
    if (sInstance == null) {
      throw new IllegalStateException("Call initialize() before getInstance()");
    }

    return sInstance;
  }

  private final Map<Target, Tracker> mTrackers = new HashMap<Target, Tracker>();
  private final Context mContext;

  /**
   * Don't instantiate directly - use {@link #getInstance()} instead.
   */
  private AnalyticsTrackers(Context context) {
    mContext = context.getApplicationContext();
  }

  public synchronized Tracker get(Target target) {
    if (!mTrackers.containsKey(target)) {
      Tracker tracker;
      switch (target) {
        case APP:
          tracker = GoogleAnalytics.getInstance(mContext).newTracker(R.xml.app_tracker);
          break;
        default:
          throw new IllegalArgumentException("Unhandled analytics target " + target);
      }
      mTrackers.put(target, tracker);
    }

    return mTrackers.get(target);
  }
}
