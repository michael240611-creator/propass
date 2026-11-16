[app]
title = ProPass
package.name = propass
package.domain = org.propass
source.dir = .
source.include_exts = py,png,jpg,kv,atlas
version = 1.0.0

# Базовые требования, которые Buildozer сам соберет идеально
requirements = python3,kivy

# Разрешения и архитектура
android.permissions = INTERNET, ACCESS_NETWORK_STATE
android.archs = arm64-v8a
android.accept_sdk_license = True
android.api = 33
android.minapi = 21
android.ndk = 25b

orientation = portrait
fullscreen = 0

[buildozer]
log_level = 2
warn_on_root = 1