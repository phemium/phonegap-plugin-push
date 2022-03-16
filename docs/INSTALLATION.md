# Installation

* [Installation](#installation)
* [Configuration](#configuration)

## Installation

| Plugin version | Cordova CLI | Cordova Android | Cordova iOS | CocoaPods |
| -------------- | ----------- | --------------- | ----------- | --------- |
| 2.2.0          | 7.1.0       | 7.1.0           | 4.5.0       | 1.1.1     |
| 2.1.2          | 7.1.0       | 6.3.0           | 4.5.0       | 1.1.1     |
| 2.1.0          | 7.1.0       | 6.3.0           | 4.4.0       | 1.1.1     |
| 2.0.0          | 7.0.0       | 6.2.1           | 4.4.0       | 1.1.1     |
| 1.9.0          | 6.4.0       | 6.0.0           | 4.3.0       | 1.1.1     |
| 1.8.0          | 3.6.3       | 4.0.0           | 4.1.0       | N/A       |

To install from the command line:

```bash
cordova plugin add https://github.com/phemium/phonegap-plugin-push
```

Install dependencies:

```bash
cordova plugin add cordova-plugin-androidx
cordova plugin add cordova-plugin-androidx-adapter
```

**DO NOT INSTALL FROM NPM REPOSITORY**

In the platform tag for Android add the following resource-file tag if you are using cordova-android 7.0 or greater:

```xml
<platform name="android">
  <resource-file src="google-services.json" target="app/google-services.json" />
</platform>
```


By default, on iOS, the plugin will register with APNS. If you want to use FCM on iOS, in the platform tag for iOS add the resource-file tag:

```xml
<platform name="ios">
  <resource-file src="GoogleService-Info.plist" />
</platform>
```

> Note: if you are using Ionic you may need to specify the SENDER_ID variable in your package.json.

```json
  "cordovaPlugins": [
    {
      "locator": "phonegap-plugin-push"
    }
  ]
```

## Notification icon

This fork allows the developer to provide a custom icon and filling color for the notifications. Please note the initializer options and the notification push data has higher priority over the manifest values. The resources provided below are only used as fallback.

### Custom icon

The notification icon MUST be a 1 color PNG file (white is preferable) with an alpha channel for defining the icon silhouette.
The icon must be placed in the drawable folders as shown below:

#### Providing icons

```xml
<platform name="android">
  <resource-file src="resources/android/icon/drawable-mdpi/notification_icon.png" target="app/src/main/res/drawable-mdpi/notification_icon.png" />
  <resource-file src="resources/android/icon/drawable-hdpi/notification_icon.png" target="app/src/main/res/drawable-hdpi/notification_icon.png" />
  <resource-file src="resources/android/icon/drawable-xhdpi/notification_icon.png" target="app/src/main/res/drawable-xhdpi/notification_icon.png" />
  <resource-file src="resources/android/icon/drawable-xxhdpi/notification_icon.png" target="app/src/main/res/drawable-xxhdpi/notification_icon.png" />
  <resource-file src="resources/android/icon/drawable-xxxhdpi/notification_icon.png" target="app/src/main/res/drawable-xxxhdpi/notification_icon.png" />
</platform>
```

Docs: https://developer.android.com/reference/android/app/Notification.Builder#setSmallIcon(android.graphics.drawable.Icon)

#### Selecting which icon to use as fallback

```xml
<platform name="android">
  <config-file parent="/manifest/application" target="app/src/main/AndroidManifest.xml" xmlns:android="http://schemas.android.com/apk/res/android">
      <meta-data android:name="notification_icon" android:value="notification_icon" />
  </config-file>
</platform>
```

### Custom icon color

Color must be an ARGB/RGB hex color. Color names are also accepted.\
**IMPORTANT**: RGB and ARGB must not contain ```#```, the plugin will detect if is an RGB/ARGB color and convert it.

```xml
<platform name="android">
  <config-file parent="/manifest/application" target="app/src/main/AndroidManifest.xml" xmlns:android="http://schemas.android.com/apk/res/android">
      <meta-data android:name="notification_icon_color" android:value="green" />
  </config-file>
</platform>
```

Docs: https://developer.android.com/reference/android/graphics/Color#parseColor(java.lang.String)

### Sound file for iOS

```xml
<platform name="ios">
  <resource-file src="mySound.caf" />
</platform>
```
