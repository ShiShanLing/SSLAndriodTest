package com.example.sslandriodtest

import android.location.Location
import android.os.Build

/**
 * 通过反射清除 Location 上的模拟标记，提升第三方 App（高德/智租换电等）对注入坐标的接受度。
 */
internal object LocationSanitizer {

    fun stripMockFlags(location: Location) {
        // API 31 以下：清除 isFromMockProvider
        try {
            Location::class.java
                .getMethod("setIsFromMockProvider", Boolean::class.javaPrimitiveType)
                .invoke(location, false)
        } catch (_: Exception) {
        }

        // API 31+：清除 setMock
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                Location::class.java
                    .getMethod("setMock", Boolean::class.javaPrimitiveType)
                    .invoke(location, false)
            } catch (_: Exception) {
            }
        }

        // 直接修改私有字段 mIsFromMockProvider
        try {
            val field = Location::class.java.getDeclaredField("mIsFromMockProvider")
            field.isAccessible = true
            field.setBoolean(location, false)
        } catch (_: Exception) {
        }

        // 清除 extras 中的 mockLocation 标记
        val extras = location.extras
        if (extras != null) {
            extras.remove("mockLocation")
            if (extras.isEmpty) {
                location.extras = null
            }
        }
    }
}
