[app]
title = ProPass
package.name = propass
package.domain = org.propass
source.dir = .
source.include_exts = py,png,jpg,kv,atlas,ttf
version = 1.0.0

# Автоматическое принятие лицензий Android SDK
android.accept_sdk_license = True

android.permissions = INTERNET, ACCESS_NETWORK_STATE
android.archs = arm64-v8a
android.api = 33
android.minapi = 21

requirements = python3==3.11.9, kivy==2.3.0
orientation = portrait
fullscreen = 0

android.logcat_filters = *:S python:D

[buildozer]
log_level = 2
warn_on_root = 1