# Add project specific ProGuard rules here.
# You can control the set of anomalies encountered by modifying this file.

# 百度地图 SDK 不混淆
-keep class com.baidu.** {*;}
-keep class vi.com.** {*;}
-keep class com.baidu.vi.** {*;}
-dontwarn com.baidu.**
-dontwarn com.baidu.lbsyun.**
-keep class com.baidu.lbsyun.** {*;}

# Google Play Services 融合定位
-keep class com.google.android.gms.location.** {*;}
-keep class com.google.android.gms.common.** {*;}
-dontwarn com.google.android.gms.**

# Location 反射方法不被混淆（LocationSanitizer 依赖反射）
-keep class android.location.Location { *; }

# 保留 Service 和关键类
-keep class com.example.sslandriodtest.MockLocationService { *; }
-keep class com.example.sslandriodtest.MockLocationProvider { *; }
-keep class com.example.sslandriodtest.LocationSanitizer { *; }

# 保留行号便于调试
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile