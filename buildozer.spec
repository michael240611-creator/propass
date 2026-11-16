[app]
title = ProPass
package.name = propass
package.domain = org.propass
source.dir = .
source.include_exts = py,png,jpg,kv,atlas,ttf
version = 1.0.0

# Автоматическое принятие лицензий (чтобы не было ошибки aidl)
android.accept_sdk_license = True

# Разрешения
android.permissions = INTERNET, ACCESS_NETWORK_STATE

# Архитектура
android.archs = arm64-v8a
android.api = 33
android.minapi = 21
android.ndk = 25b

# Золотая связка версий, исключающая ошибки компиляции
requirements = python3==3.10.12, kivy==2.3.0, pyjnius==1.6.0

# Оформление
orientation = portrait
fullscreen = 0
icon.filename = %(source.dir)s/icon.png

[buildozer]
log_level = 2
warn_on_root = 1