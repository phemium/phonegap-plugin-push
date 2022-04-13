# [DEPRECATED] phonegap-plugin-push [![Build Status](https://travis-ci.org/phonegap/phonegap-plugin-push.svg)](https://travis-ci.org/phonegap/phonegap-plugin-push)

> Register and receive push notifications

> Use `@phemium-costaisa/cordova-plugin-push` for improved version: https://github.com/phemium/cordova-plugin-push

# Warning

The links below take you to the version 2.x documentation which includes a
number of breaking API changes from version 1.x, mostly the move from GCM to
FCM. If you are using version 1.x please reference the docs in the
[v1.x branch](https://github.com/phonegap/phonegap-plugin-push/tree/v1.x).

# What is this?

This plugin offers support to receive and handle native push notifications with
a **single unified API**.

This does not mean you will be able to send a single push message and have it
arrive on devices running different operating systems. By default Android uses
FCM and iOS uses APNS and their payloads are significantly different. Even if
you are using FCM for both Android and iOS there are differences in the payload
required for the plugin to work correctly. For Android **always** put your push
payload in the `data` section of the push notification. For more information on
why that is the case read
[Notification vs Data Payload](https://github.com/phonegap/phonegap-plugin-push/blob/master/docs/PAYLOAD.md#notification-vs-data-payloads).
For iOS follow the regular
[FCM documentation](https://firebase.google.com/docs/cloud-messaging/http-server-ref).

This plugin does not provide a way to determine which platform you are running
on. The best way to do that is use the `device.platform` property provided by
[cordova-plugin-device](https://github.com/apache/cordova-plugin-device).

Starting with version `2.0.0`, this plugin will support `CocoaPods` installation
of the `Firebase Cloud Messaging` library. More details are available in the
[Installation](docs/INSTALLATION.md#cocoapods) documentation.

* [Reporting Issues](docs/ISSUES.md)
* [Installation](docs/INSTALLATION.md)
* [API reference](docs/API.md)
* [Typescript support](docs/TYPESCRIPT.md)
* [Examples](docs/EXAMPLES.md)
* [Platform support](docs/PLATFORM_SUPPORT.md)
* [Cloud build support (PG Build, IntelXDK)](docs/PHONEGAP_BUILD.md)
* [Push notification payload details](docs/PAYLOAD.md)
* [Contributing](.github/CONTRIBUTING.md)
* [License (MIT)](MIT-LICENSE)
