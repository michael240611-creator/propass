[app]
title = ProPass
package.name = propass
package.domain = org.propass
source.dir = .
source.include_exts = py,png,jpg,kv,atlas,ttf
version = 1.0.0

android.accept_sdk_license = True
android.permissions = INTERNET, ACCESS_NETWORK_STATE
android.archs = arm64-v8a
android.api = 33
android.minapi = 24
android.ndk = 25b

requirements = python3==3.10.12, kivy==2.3.0, pyjnius==1.5.0
orientation = portrait
fullscreen = 0

android.logcat_filters = *:S python:D

[buildozer]
log_level = 2
warn_on_root = 1