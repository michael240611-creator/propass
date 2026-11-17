[app]
title = Plum
package.name = plum
package.domain = org.plum
source.dir = .
source.include_exts = py,png,jpg,kv,atlas,ttf
version = 1.0.0

android.accept_sdk_license = True
android.permissions = INTERNET, ACCESS_NETWORK_STATE
android.archs = arm64-v8a
android.api = 33
android.minapi = 21

# Упрощенные требования без жестких версий (Docker сам подберет нужные)
requirements = python3, kivy

orientation = portrait
fullscreen = 0

[buildozer]
log_level = 2
warn_on_root = 1