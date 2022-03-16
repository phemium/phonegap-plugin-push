package com.adobe.phonegap.push;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.Intent;
import android.provider.Settings;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import org.apache.cordova.LOG;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;

import me.leolin.shortcutbadger.ShortcutBadger;

public class PushPlugin extends CordovaPlugin implements PushConstants {

  public static final String LOG_TAG = "Push_Plugin";

  private final MyActivityLifecycleCallbacks mCallbacks = new MyActivityLifecycleCallbacks();

  /**
   * Contains all the callback contexts for each Activity used by the application
   */
  private static HashMap<String, ArrayList<CallbackContext>> callbackContextsMap = new HashMap<String, ArrayList<CallbackContext>>();

  private static AppCompatActivity currentActivity;
  private static CordovaWebView gWebView;
  private static List<Bundle> gCachedExtras = Collections.synchronizedList(new ArrayList<Bundle>());

  private static String registration_id = "";

  /**
   * Gets the application context from cordova's main activity.
   *
   * @return the application context
   */
  private Context getApplicationContext() {
    return this.cordova.getActivity().getApplicationContext();
  }

  @TargetApi(26)
  private JSONArray listChannels() throws JSONException {
    JSONArray channels = new JSONArray();
    // only call on Android O and above
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      final NotificationManager notificationManager = (NotificationManager) cordova.getActivity()
          .getSystemService(Context.NOTIFICATION_SERVICE);
      List<NotificationChannel> notificationChannels = notificationManager.getNotificationChannels();
      for (NotificationChannel notificationChannel : notificationChannels) {
        JSONObject channel = new JSONObject();
        channel.put(CHANNEL_ID, notificationChannel.getId());
        channel.put(CHANNEL_DESCRIPTION, notificationChannel.getDescription());
        channels.put(channel);
      }
    }
    return channels;
  }

  @TargetApi(26)
  private int getLockscreenVisibility() {
    // only call on Android O and above
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      LOG.d(LOG_TAG, "Getting lockscreen visibility...");
      final NotificationManager notificationManager = (NotificationManager) cordova.getActivity()
              .getSystemService(Context.NOTIFICATION_SERVICE);
      List<NotificationChannel> notificationChannels = notificationManager.getNotificationChannels();
      if (notificationChannels.size() > 0) {
        int visibility = notificationChannels.get(0).getLockscreenVisibility();
        LOG.d(LOG_TAG, "Lockscreen visibility: " + visibility);
        return visibility;
      } else {
        LOG.e(LOG_TAG, "No channels found");
        return -2;
      }
    } else {
      LOG.e(LOG_TAG, "Incompatible Android version found");
      return -3;
    }
  }

  @TargetApi(26)
  private void enableLockscreenIntent() {
    // only call on Android O and above
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      LOG.d(LOG_TAG, "enableLockscreenIntent");
      final NotificationManager notificationManager = (NotificationManager) cordova.getActivity()
              .getSystemService(Context.NOTIFICATION_SERVICE);
      List<NotificationChannel> notificationChannels = notificationManager.getNotificationChannels();
      if (notificationChannels.size() > 0) {
        String channelId = notificationChannels.get(0).getId();
        LOG.d(LOG_TAG, "Creating display of notification channel settings for channel: " + channelId);
        Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getApplicationContext().getPackageName())
                .putExtra(Settings.EXTRA_CHANNEL_ID, channelId);
        LOG.d(LOG_TAG, "Starting intent...");
        getApplicationContext().startActivity(intent);
      }
    }
  }

  @TargetApi(26)
  private void deleteChannel(String channelId) {
    // only call on Android O and above
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      final NotificationManager notificationManager = (NotificationManager) cordova.getActivity()
          .getSystemService(Context.NOTIFICATION_SERVICE);
      notificationManager.deleteNotificationChannel(channelId);
    }
  }

  @TargetApi(26)
  private void createChannel(JSONObject channel) throws JSONException {
    // only call on Android O and above
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      LOG.d(LOG_TAG, "Creating channel with name: " + channel.get(CHANNEL_DESCRIPTION));
      final NotificationManager notificationManager = (NotificationManager) cordova.getActivity()
          .getSystemService(Context.NOTIFICATION_SERVICE);

      String packageName = getApplicationContext().getPackageName();
      NotificationChannel mChannel = new NotificationChannel(channel.getString(CHANNEL_ID),
          channel.optString(CHANNEL_DESCRIPTION, ""),
          channel.optInt(CHANNEL_IMPORTANCE, NotificationManager.IMPORTANCE_HIGH));

      try {
        String CHANNEL_GROUP_NAME = "Messages";
        LOG.d(LOG_TAG, "Creating channel group with name: " + CHANNEL_GROUP_NAME);
        NotificationChannelGroup mChannelGroup = new NotificationChannelGroup("MessagesGroup", CHANNEL_GROUP_NAME);
        notificationManager.createNotificationChannelGroup(mChannelGroup);
        LOG.d(LOG_TAG, "Assigning channel " + CHANNEL_DESCRIPTION + " to group " + CHANNEL_GROUP_NAME);
        mChannel.setGroup(mChannelGroup.getId());
        LOG.d(LOG_TAG, "Setting color");
      } catch (Exception e) {
        LOG.e(LOG_TAG, e.getMessage());
      }

      int lightColor = channel.optInt(CHANNEL_LIGHT_COLOR, -1);
      if (lightColor != -1) {
        mChannel.setLightColor(lightColor);
      }

      int visibility = channel.optInt(VISIBILITY, NotificationCompat.VISIBILITY_PUBLIC);
      mChannel.setLockscreenVisibility(visibility);

      boolean badge = channel.optBoolean(BADGE, true);
      mChannel.setShowBadge(badge);

      String sound = channel.optString(SOUND, "default");
      AudioAttributes audioAttributes = new AudioAttributes.Builder()
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build();
      if (SOUND_RINGTONE.equals(sound)) {
        mChannel.setSound(android.provider.Settings.System.DEFAULT_RINGTONE_URI, audioAttributes);
      } else if (sound != null && sound.isEmpty()) {
        // Disable sound for this notification channel if an empty string is passed.
        // https://stackoverflow.com/a/47144981/6194193
        mChannel.setSound(null, null);
      } else if (sound != null && !sound.contentEquals(SOUND_DEFAULT)) {
        Uri soundUri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/raw/" + sound);
        mChannel.setSound(soundUri, audioAttributes);
      } else {
        mChannel.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, audioAttributes);
      }

      // If vibration settings is an array set vibration pattern, else set enable
      // vibration.
      JSONArray pattern = channel.optJSONArray(CHANNEL_VIBRATION);
      if (pattern != null) {
        int patternLength = pattern.length();
        long[] patternArray = new long[patternLength];
        for (int i = 0; i < patternLength; i++) {
          patternArray[i] = pattern.optLong(i);
        }
        mChannel.setVibrationPattern(patternArray);
      } else {
        boolean vibrate = channel.optBoolean(CHANNEL_VIBRATION, true);
        mChannel.enableVibration(vibrate);
      }
      mChannel.enableLights(true);
      notificationManager.createNotificationChannel(mChannel);
    }
  }

  @TargetApi(26)
  private void createDefaultNotificationChannelIfNeeded(JSONObject options) {
    String id;
    // only call on Android O and above
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      final NotificationManager notificationManager = (NotificationManager) cordova.getActivity()
          .getSystemService(Context.NOTIFICATION_SERVICE);

      LOG.d(LOG_TAG, "Deleting all previous channels...");
      List<NotificationChannel> channels = notificationManager.getNotificationChannels();
      LOG.d(LOG_TAG, channels.size() + " channels");

      for (int i = 0; i < channels.size(); i++) {
        LOG.d(LOG_TAG, "Deleting channel: " + channels.get(i).getName());
        deleteChannel(channels.get(i).getId());
      }

      LOG.d(LOG_TAG, "Deleting all previous channel groups...");
      List<NotificationChannelGroup> channelGroups = notificationManager.getNotificationChannelGroups();
      LOG.d(LOG_TAG, channelGroups.size() + " channel groups");

      for (int i = 0; i < channelGroups.size(); i++) {
        LOG.d(LOG_TAG, "Deleting channel group: " + channelGroups.get(i).getName());
        notificationManager.deleteNotificationChannelGroup(channelGroups.get(i).getId());
      }
      try {
        options.put(CHANNEL_ID, DEFAULT_CHANNEL_ID);
        options.putOpt(CHANNEL_DESCRIPTION, "FirebaseMessages");
        createChannel(options);
      } catch (JSONException e) {
        LOG.e(LOG_TAG, "execute: Got JSON Exception " + e.getMessage());
      }
    }
  }

  public static void addCallbackContext(CallbackContext callbackContext) {
    String activityName = getCurrentActivityName();
    if (!callbackContextsMap.containsKey(activityName)) {
      callbackContextsMap.put(activityName, new ArrayList<CallbackContext>());
    }
    List<CallbackContext> contexts = callbackContextsMap.get(activityName);
    int index = 0;
    int foundCallbackContext = -1;
    while (contexts.size() > index) {
      if (contexts.get(index).getCallbackId() == callbackContext.getCallbackId()) {
        foundCallbackContext = index;
        break;
      }
      index++;
    }
    if (foundCallbackContext == -1) {
      LOG.d(LOG_TAG, "Registering new CallbackContext #" + callbackContext.getCallbackId() + " inside Activity " + activityName);
      contexts.add(callbackContext);
    }
  }

  @Override
  public boolean execute(final String action, final JSONArray data, final CallbackContext callbackContext) {
    LOG.v(LOG_TAG, "execute: action=" + action);
    gWebView = webView;
    currentActivity = cordova.getActivity();

    if (INITIALIZE.equals(action)) {
      cordova.getThreadPool().execute(new Runnable() {
        public void run() {
          PushPlugin.addCallbackContext(callbackContext);
          JSONObject jo = null;

          LOG.v(LOG_TAG, "execute: data=" + data.toString());
          SharedPreferences sharedPref = getApplicationContext().getSharedPreferences(COM_ADOBE_PHONEGAP_PUSH,
                  Context.MODE_PRIVATE);
          String token = null;
          String senderID = null;

          try {
            jo = data.getJSONObject(0).getJSONObject(ANDROID);

            // If no NotificationChannels exist create the default one
            createDefaultNotificationChannelIfNeeded(jo);

            LOG.v(LOG_TAG, "execute: jo=" + jo.toString());

            senderID = getStringResourceByName(GCM_DEFAULT_SENDER_ID);

            LOG.v(LOG_TAG, "execute: senderID=" + senderID);

            try {
              token = FirebaseInstanceId.getInstance().getToken();
            } catch (IllegalStateException e) {
              LOG.e(LOG_TAG, "Exception raised while getting Firebase token " + e.getMessage());
            }

            if (token == null) {
              try {
                token = FirebaseInstanceId.getInstance().getToken(senderID, FCM);
              } catch (IllegalStateException e) {
                LOG.e(LOG_TAG, "Exception raised while getting Firebase token " + e.getMessage());
              }
            }

            if (!"".equals(token)) {
              JSONObject json = new JSONObject().put(REGISTRATION_ID, token);
              json.put(REGISTRATION_TYPE, FCM);

              LOG.v(LOG_TAG, "onRegistered: " + json.toString());

              JSONArray topics = jo.optJSONArray(TOPICS);
              subscribeToTopics(topics, registration_id);

              PushPlugin.sendEvent(json);
            } else {
              callbackContext.error("Empty registration ID received from FCM");
              return;
            }
          } catch (JSONException e) {
            LOG.e(LOG_TAG, "execute: Got JSON Exception " + e.getMessage());
            callbackContext.error(e.getMessage());
          } catch (IOException e) {
            LOG.e(LOG_TAG, "execute: Got IO Exception " + e.getMessage());
            callbackContext.error(e.getMessage());
          } catch (Resources.NotFoundException e) {

            LOG.e(LOG_TAG, "execute: Got Resources NotFoundException " + e.getMessage());
            callbackContext.error(e.getMessage());
          }

          if (jo != null) {
            SharedPreferences.Editor editor = sharedPref.edit();
            try {
              editor.putString(ICON, jo.getString(ICON));
            } catch (JSONException e) {
              LOG.d(LOG_TAG, "no icon option");
            }
            try {
              editor.putString(ICON_COLOR, jo.getString(ICON_COLOR));
            } catch (JSONException e) {
              LOG.d(LOG_TAG, "no iconColor option");
            }

            boolean clearBadge = jo.optBoolean(CLEAR_BADGE, false);
            if (clearBadge) {
              setApplicationIconBadgeNumber(getApplicationContext(), 0);
            }

            editor.putBoolean(SOUND, jo.optBoolean(SOUND, true));
            editor.putBoolean(VIBRATE, jo.optBoolean(VIBRATE, true));
            editor.putBoolean(CLEAR_BADGE, clearBadge);
            editor.putBoolean(CLEAR_NOTIFICATIONS, jo.optBoolean(CLEAR_NOTIFICATIONS, true));
            editor.putBoolean(FORCE_SHOW, jo.optBoolean(FORCE_SHOW, false));
            editor.putString(SENDER_ID, senderID);
            editor.putString(MESSAGE_KEY, jo.optString(MESSAGE_KEY));
            editor.putString(TITLE_KEY, jo.optString(TITLE_KEY));
            editor.commit();

          }

          if (!gCachedExtras.isEmpty()) {
            LOG.v(LOG_TAG, "sending cached extras");
            synchronized (gCachedExtras) {
              Iterator<Bundle> gCachedExtrasIterator = gCachedExtras.iterator();
              while (gCachedExtrasIterator.hasNext()) {
                sendExtras(gCachedExtrasIterator.next());
              }
            }
            gCachedExtras.clear();
          }
        }
      });
    } else if (UNREGISTER.equals(action)) {
      cordova.getThreadPool().execute(new Runnable() {
        public void run() {
          try {
            SharedPreferences sharedPref = getApplicationContext().getSharedPreferences(COM_ADOBE_PHONEGAP_PUSH,
                Context.MODE_PRIVATE);
            JSONArray topics = data.optJSONArray(0);
            if (topics != null && !"".equals(registration_id)) {
              unsubscribeFromTopics(topics, registration_id);
            } else {
              FirebaseInstanceId.getInstance().deleteInstanceId();
              LOG.v(LOG_TAG, "UNREGISTER");

              // Remove shared prefs
              SharedPreferences.Editor editor = sharedPref.edit();
              editor.remove(SOUND);
              editor.remove(VIBRATE);
              editor.remove(CLEAR_BADGE);
              editor.remove(CLEAR_NOTIFICATIONS);
              editor.remove(FORCE_SHOW);
              editor.remove(SENDER_ID);
              editor.commit();
            }

            callbackContext.success();
          } catch (IOException e) {
            LOG.e(LOG_TAG, "execute: Got JSON Exception " + e.getMessage());
            callbackContext.error(e.getMessage());
          }
        }
      });
    } else if (FINISH.equals(action)) {
      callbackContext.success();
    } else if (HAS_PERMISSION.equals(action)) {
      cordova.getThreadPool().execute(new Runnable() {
        public void run() {
          JSONObject jo = new JSONObject();
          try {
            LOG.d(LOG_TAG,
                "has permission: " + NotificationManagerCompat.from(getApplicationContext()).areNotificationsEnabled());
            jo.put("isEnabled", NotificationManagerCompat.from(getApplicationContext()).areNotificationsEnabled());
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, jo);
            pluginResult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginResult);
          } catch (UnknownError e) {
            callbackContext.error(e.getMessage());
          } catch (JSONException e) {
            callbackContext.error(e.getMessage());
          }
        }
      });
    } else if (SET_APPLICATION_ICON_BADGE_NUMBER.equals(action)) {
      cordova.getThreadPool().execute(new Runnable() {
        public void run() {
          LOG.v(LOG_TAG, "setApplicationIconBadgeNumber: data=" + data.toString());
          try {
            setApplicationIconBadgeNumber(getApplicationContext(), data.getJSONObject(0).getInt(BADGE));
          } catch (JSONException e) {
            callbackContext.error(e.getMessage());
          }
          callbackContext.success();
        }
      });
    } else if (GET_APPLICATION_ICON_BADGE_NUMBER.equals(action)) {
      cordova.getThreadPool().execute(new Runnable() {
        public void run() {
          LOG.v(LOG_TAG, "getApplicationIconBadgeNumber");
          callbackContext.success(getApplicationIconBadgeNumber(getApplicationContext()));
        }
      });
    } else if (CLEAR_ALL_NOTIFICATIONS.equals(action)) {
      cordova.getThreadPool().execute(new Runnable() {
        public void run() {
          LOG.v(LOG_TAG, "clearAllNotifications");
          clearAllNotifications();
          callbackContext.success();
        }
      });
    } else if (SUBSCRIBE.equals(action)) {
      // Subscribing for a topic
      cordova.getThreadPool().execute(new Runnable() {
        public void run() {
          try {
            String topic = data.getString(0);
            subscribeToTopic(topic, registration_id);
            callbackContext.success();
          } catch (JSONException e) {
            callbackContext.error(e.getMessage());
          }
        }
      });
    } else if (UNSUBSCRIBE.equals(action)) {
      // un-subscribing for a topic
      cordova.getThreadPool().execute(new Runnable() {
        public void run() {
          try {
            String topic = data.getString(0);
            unsubscribeFromTopic(topic, registration_id);
            callbackContext.success();
          } catch (JSONException e) {
            callbackContext.error(e.getMessage());
          }
        }
      });
    } else if (CREATE_CHANNEL.equals(action)) {
      // un-subscribing for a topic
      cordova.getThreadPool().execute(new Runnable() {
        public void run() {
          try {
            // call create channel
            createChannel(data.getJSONObject(0));
            callbackContext.success();
          } catch (JSONException e) {
            callbackContext.error(e.getMessage());
          }
        }
      });
    } else if (DELETE_CHANNEL.equals(action)) {
      // un-subscribing for a topic
      cordova.getThreadPool().execute(new Runnable() {
        public void run() {
          try {
            String channelId = data.getString(0);
            deleteChannel(channelId);
            callbackContext.success();
          } catch (JSONException e) {
            callbackContext.error(e.getMessage());
          }
        }
      });
    } else if (LIST_CHANNELS.equals(action)) {
      // un-subscribing for a topic
      cordova.getThreadPool().execute(new Runnable() {
        public void run() {
          try {
            callbackContext.success(listChannels());
          } catch (JSONException e) {
            callbackContext.error(e.getMessage());
          }
        }
      });
    } else if (GET_LOCKSCREEN_VISIBILITY.equals(action)) {
      // un-subscribing for a topic
      cordova.getThreadPool().execute(new Runnable() {
        public void run() {
          try {
            callbackContext.success(getLockscreenVisibility());
          } catch (Exception e) {
            LOG.e(LOG_TAG, e.getMessage());
            callbackContext.error(e.getMessage());
          }
        }
      });
    } else if (ENABLE_LOCKSCREEN_INTENT.equals(action)) {
      // un-subscribing for a topic
      cordova.getThreadPool().execute(new Runnable() {
        public void run() {
          try {
            enableLockscreenIntent();
            callbackContext.success();
          } catch (Exception e) {
            LOG.e(LOG_TAG, e.getMessage());
            callbackContext.error(e.getMessage());
          }
        }
      });
    } else if (CLEAR_NOTIFICATION.equals(action)) {
      // clearing a single notification
      cordova.getThreadPool().execute(new Runnable() {
        public void run() {
          try {
            LOG.v(LOG_TAG, "clearNotification");
            int id = data.getInt(0);
            clearNotification(id);
            callbackContext.success();
          } catch (JSONException e) {
            callbackContext.error(e.getMessage());
          }
        }
      });
    } else {
      LOG.e(LOG_TAG, "Invalid action : " + action);
      callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
      return false;
    }

    return true;
  }

  public static void sendEvent(JSONObject _json) {
    try {
      LOG.d(LOG_TAG, _json.toString(4));
    } catch (JSONException e) {
      e.printStackTrace();
    }
    PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, _json);
    pluginResult.setKeepCallback(true);
    sendPluginResultToCurrentActivity(pluginResult);
  }

  public static void sendPluginResultToAllActivities(PluginResult pluginResult) {
    for (String activityName : callbackContextsMap.keySet()) {
      List<CallbackContext> contexts = callbackContextsMap.get(activityName);
      int index = 0;
      while (contexts.size() > index) {
        CallbackContext callbackContext = contexts.get(index);
        LOG.d(LOG_TAG, "Sending pluginResult to Activity " + activityName + " and CallbackContext " + callbackContext.getCallbackId());
        callbackContext.sendPluginResult(pluginResult);
        index++;
      }
    }
  }

  public static void sendPluginResultToCurrentActivity(PluginResult pluginResult) {
    String activityName = getCurrentActivityName();
    LOG.d(LOG_TAG, "Sending event to current activity: " + activityName);
    List<CallbackContext> contexts = callbackContextsMap.get(activityName);
    LOG.d(LOG_TAG, "Current activity has " + contexts.size() + " callback contexts");
    int index = 0;
    while (contexts.size() > index) {
      CallbackContext callbackContext = contexts.get(index);
      LOG.d(LOG_TAG, "Sending pluginResult to activity " + activityName + " and CallbackContext #" + callbackContext.getCallbackId());
      callbackContext.sendPluginResult(pluginResult);
      index++;
    }
  }

  public static void sendError(String message) {
    PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, message);
    pluginResult.setKeepCallback(true);
    sendPluginResultToCurrentActivity(pluginResult);
  }

  public static String getCurrentActivityName() {
    return currentActivity.getClass().getSimpleName();
  }

  /*
   * Sends the pushbundle extras to the client application. If the client
   * application isn't currently active and the no-cache flag is not set, it is
   * cached for later processing.
   */
  public static void sendExtras(Bundle extras) {
    if (extras != null) {
      String noCache = extras.getString(NO_CACHE);
      if (gWebView != null) {
        sendEvent(convertBundleToJson(extras));
      } else if (!"1".equals(noCache)) {
        LOG.v(LOG_TAG, "sendExtras: caching extras to send at a later time.");
        gCachedExtras.add(extras);
      }
    }
  }

  /*
   * Retrives badge count from SharedPreferences
   */
  public static int getApplicationIconBadgeNumber(Context context) {
    SharedPreferences settings = context.getSharedPreferences(BADGE, Context.MODE_PRIVATE);
    return settings.getInt(BADGE, 0);
  }

  /*
   * Sets badge count on application icon and in SharedPreferences
   */
  public static void setApplicationIconBadgeNumber(Context context, int badgeCount) {
    if (badgeCount > 0) {
      ShortcutBadger.applyCount(context, badgeCount);
    } else {
      ShortcutBadger.removeCount(context);
    }

    SharedPreferences.Editor editor = context.getSharedPreferences(BADGE, Context.MODE_PRIVATE).edit();
    editor.putInt(BADGE, Math.max(badgeCount, 0));
    editor.apply();
  }

  @Override
  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);
  }

  @Override
  public void onPause(boolean multitasking) {
    super.onPause(multitasking);

    SharedPreferences prefs = getApplicationContext().getSharedPreferences(COM_ADOBE_PHONEGAP_PUSH,
        Context.MODE_PRIVATE);
    if (prefs.getBoolean(CLEAR_NOTIFICATIONS, true)) {
      clearAllNotifications();
    }
  }

  @Override
  public void pluginInitialize() {
    cordova.getActivity().getApplication().registerActivityLifecycleCallbacks(mCallbacks);
    super.pluginInitialize();
  }

  @Override
  public void onResume(boolean multitasking) {
    super.onResume(multitasking);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    cordova.getActivity().getApplication().unregisterActivityLifecycleCallbacks(mCallbacks);
  }

  private void clearAllNotifications() {
    final NotificationManager notificationManager = (NotificationManager) cordova.getActivity()
        .getSystemService(Context.NOTIFICATION_SERVICE);
    notificationManager.cancelAll();
  }

  private void clearNotification(int id) {
    final NotificationManager notificationManager = (NotificationManager) cordova.getActivity()
        .getSystemService(Context.NOTIFICATION_SERVICE);
    String appName = (String) this.cordova.getActivity().getPackageManager()
        .getApplicationLabel(this.cordova.getActivity().getApplicationInfo());
    notificationManager.cancel(appName, id);
  }

  private void subscribeToTopics(JSONArray topics, String registrationToken) {
    if (topics != null) {
      String topic = null;
      for (int i = 0; i < topics.length(); i++) {
        topic = topics.optString(i, null);
        subscribeToTopic(topic, registrationToken);
      }
    }
  }

  private void subscribeToTopic(String topic, String registrationToken) {
    if (topic != null) {
      LOG.d(LOG_TAG, "Subscribing to topic: " + topic);
      FirebaseMessaging.getInstance().subscribeToTopic(topic);
    }
  }

  private void unsubscribeFromTopics(JSONArray topics, String registrationToken) {
    if (topics != null) {
      String topic = null;
      for (int i = 0; i < topics.length(); i++) {
        topic = topics.optString(i, null);
        unsubscribeFromTopic(topic, registrationToken);
      }
    }
  }

  private void unsubscribeFromTopic(String topic, String registrationToken) {
    if (topic != null) {
      LOG.d(LOG_TAG, "Unsubscribing to topic: " + topic);
      FirebaseMessaging.getInstance().unsubscribeFromTopic(topic);
    }
  }

  /*
   * serializes a bundle to JSON.
   */
  private static JSONObject convertBundleToJson(Bundle extras) {
    LOG.d(LOG_TAG, "convert extras to json");
    try {
      JSONObject json = new JSONObject();
      JSONObject additionalData = new JSONObject();

      // Add any keys that need to be in top level json to this set
      HashSet<String> jsonKeySet = new HashSet();
      Collections.addAll(jsonKeySet, TITLE, MESSAGE, COUNT, SOUND, IMAGE);

      Iterator<String> it = extras.keySet().iterator();
      while (it.hasNext()) {
        String key = it.next();
        Object value = extras.get(key);

        LOG.d(LOG_TAG, "key = " + key);

        if (jsonKeySet.contains(key)) {
          json.put(key, value);
        } else if (key.equals(COLDSTART)) {
          additionalData.put(key, extras.getBoolean(COLDSTART));
        } else if (key.equals(FOREGROUND)) {
          additionalData.put(key, extras.getBoolean(FOREGROUND));
        } else if (key.equals(DISMISSED)) {
          additionalData.put(key, extras.getBoolean(DISMISSED));
        } else if (value instanceof String) {
          String strValue = (String) value;
          try {
            // Try to figure out if the value is another JSON object
            if (strValue.startsWith("{")) {
              additionalData.put(key, new JSONObject(strValue));
            }
            // Try to figure out if the value is another JSON array
            else if (strValue.startsWith("[")) {
              additionalData.put(key, new JSONArray(strValue));
            } else {
              additionalData.put(key, value);
            }
          } catch (Exception e) {
            additionalData.put(key, value);
          }
        }
      } // while

      json.put(ADDITIONAL_DATA, additionalData);
      LOG.v(LOG_TAG, "extrasToJSON: " + json.toString());

      return json;
    } catch (JSONException e) {
      LOG.e(LOG_TAG, "extrasToJSON: JSON exception");
    }
    return null;
  }

  private String getStringResourceByName(String aString) {
    Activity activity = cordova.getActivity();
    String packageName = activity.getPackageName();
    int resId = activity.getResources().getIdentifier(aString, "string", packageName);
    return activity.getString(resId);
  }

  public static boolean isInForeground() {
    ActivityManager.RunningAppProcessInfo appProcessInfo = new ActivityManager.RunningAppProcessInfo();
    ActivityManager.getMyMemoryState(appProcessInfo);
    Boolean gForeground = (appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND || appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE);
    LOG.d(LOG_TAG, "isInForeground: " + gForeground.toString());
    return gForeground;
  }

  public static boolean isActive() {
    return gWebView != null;
  }

  protected static void setRegistrationID(String token) {
    registration_id = token;
  }

  public static class MyActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {

    private static String LOG_TAG = "Activities";

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
      String activityName = activity.getClass().getSimpleName();
      LOG.d(LOG_TAG, "Created activity: " + activityName);
      if (!callbackContextsMap.containsKey(activityName)) {
        callbackContextsMap.put(activityName, new ArrayList<CallbackContext>());
      }
    }

    @Override
    public void onActivityStarted(Activity activity) {
      String activityName = activity.getClass().getSimpleName();
      LOG.d(LOG_TAG, "Started activity: " + activityName);
    }

    @Override
    public void onActivityResumed(Activity activity) {
      String activityName = activity.getClass().getSimpleName();
      LOG.d(LOG_TAG, "Resumed activity: " + activityName);
      PushPlugin.currentActivity = (AppCompatActivity) activity;
    }

    @Override
    public void onActivityPaused(Activity activity) {
      String activityName = activity.getClass().getSimpleName();
      LOG.d(LOG_TAG, "Paused activity: " + activityName);
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }

    @Override
    public void onActivityStopped(Activity activity) {
      String activityName = activity.getClass().getSimpleName();
      LOG.d(LOG_TAG, "Stopped activity: " + activityName);
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
      String activityName = activity.getClass().getSimpleName();
      LOG.d(LOG_TAG, "Destroying activity: " + activityName);
      if (callbackContextsMap.containsKey(activityName)) {
        callbackContextsMap.remove(activityName);
      }
    }
  }
}
