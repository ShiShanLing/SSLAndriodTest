package com.example.sslandriodtest

import com.baidu.mapapi.model.LatLng
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 坐标转换工具
 * 百度地图使用 BD-09，系统 GPS 模拟使用 WGS-84
 * 完整 4 步转换：WGS-84 ↔ GCJ-02 ↔ BD-09
 */

// ── BD-09 ↔ WGS-84 转换（返回 LatLng 用于百度地图显示）──

fun wgs84ToBdLatLng(wgsLat: Double, wgsLng: Double): LatLng {
    val (bdLat, bdLng) = wgs84ToBd09(wgsLat, wgsLng)
    return LatLng(bdLat, bdLng)
}

// ── BD-09 → WGS-84 ──
fun bd09ToWgs84(bdLat: Double, bdLng: Double): Pair<Double, Double> {
    val gcj = bd09ToGcj02(bdLat, bdLng)
    return gcj02ToWgs84(gcj.first, gcj.second)
}

// ── WGS-84 → BD-09 ──
fun wgs84ToBd09(wgsLat: Double, wgsLng: Double): Pair<Double, Double> {
    val gcj = wgs84ToGcj02(wgsLat, wgsLng)
    return gcj02ToBd09(gcj.first, gcj.second)
}

// ── WGS-84 → GCJ-02 ──
private fun wgs84ToGcj02(lat: Double, lng: Double): Pair<Double, Double> {
    if (outOfChina(lat, lng)) return lat to lng
    var dLat = transformLat(lng - 105.0, lat - 35.0)
    var dLng = transformLng(lng - 105.0, lat - 35.0)
    val radLat = lat / 180.0 * PI
    var magic = sin(radLat)
    magic = 1 - EE * magic * magic
    val sqrtMagic = sqrt(magic)
    dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
    dLng = (dLng * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
    return lat + dLat to lng + dLng
}

// ── GCJ-02 → BD-09 ──
private fun gcj02ToBd09(lat: Double, lng: Double): Pair<Double, Double> {
    val z = sqrt(lng * lng + lat * lat) + 0.00002 * sin(lat * X_PI)
    val theta = atan2(lat, lng) + 0.000003 * cos(lng * X_PI)
    val bdLng = z * cos(theta) + 0.0065
    val bdLat = z * sin(theta) + 0.006
    return bdLat to bdLng
}

// ── BD-09 → GCJ-02 ──
private fun bd09ToGcj02(lat: Double, lng: Double): Pair<Double, Double> {
    val x = lng - 0.0065
    val y = lat - 0.006
    val z = sqrt(x * x + y * y) - 0.00002 * sin(y * X_PI)
    val theta = atan2(y, x) - 0.000003 * cos(x * X_PI)
    val gcjLng = z * cos(theta)
    val gcjLat = z * sin(theta)
    return gcjLat to gcjLng
}

// ── GCJ-02 → WGS-84 ──
private fun gcj02ToWgs84(lat: Double, lng: Double): Pair<Double, Double> {
    if (outOfChina(lat, lng)) return lat to lng
    var dLat = transformLat(lng - 105.0, lat - 35.0)
    var dLng = transformLng(lng - 105.0, lat - 35.0)
    val radLat = lat / 180.0 * PI
    var magic = sin(radLat)
    magic = 1 - EE * magic * magic
    val sqrtMagic = sqrt(magic)
    dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
    dLng = (dLng * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
    return lat - dLat to lng - dLng
}

private fun outOfChina(lat: Double, lng: Double): Boolean {
    return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
}

private fun transformLat(x: Double, y: Double): Double {
    var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
    ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
    ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
    ret += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
    return ret
}

private fun transformLng(x: Double, y: Double): Double {
    var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
    ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
    ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
    ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
    return ret
}

private const val X_PI = PI * 3000.0 / 180.0
private const val A = 6378245.0
private const val EE = 0.00669342162296594323
