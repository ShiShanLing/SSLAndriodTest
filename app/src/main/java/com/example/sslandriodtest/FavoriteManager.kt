package com.example.sslandriodtest

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 收藏地点数据
 */
data class FavoriteLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * 收藏管理器 - 使用 SharedPreferences 持久化收藏列表
 */
class FavoriteManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("favorite_locations", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_INITIALIZED = "initialized"

        // 默认收藏 - 苏州 (BD-09 坐标)
        private val DEFAULT_FAVORITES = listOf(
            FavoriteLocation("苏州", 31.298886, 120.585315),
            FavoriteLocation("苏州工业园区", 31.316510, 120.708876),
            FavoriteLocation("苏州火车站", 31.330486, 120.585315)
        )
    }

    /**
     * 获取所有收藏地点
     */
    fun getAll(): List<FavoriteLocation> {
        val json = prefs.getString(KEY_FAVORITES, null) ?: return DEFAULT_FAVORITES
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                FavoriteLocation(
                    name = obj.getString("name"),
                    latitude = obj.getDouble("latitude"),
                    longitude = obj.getDouble("longitude")
                )
            }
        } catch (_: Exception) {
            DEFAULT_FAVORITES
        }
    }

    /**
     * 添加收藏
     */
    fun add(location: FavoriteLocation) {
        val list = getAll().toMutableList()
        // 避免重复
        if (list.any { it.name == location.name }) return
        list.add(location)
        save(list)
    }

    /**
     * 删除收藏
     */
    fun remove(index: Int) {
        val list = getAll().toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            save(list)
        }
    }

    private fun save(list: List<FavoriteLocation>) {
        val array = JSONArray()
        list.forEach { loc ->
            val obj = JSONObject().apply {
                put("name", loc.name)
                put("latitude", loc.latitude)
                put("longitude", loc.longitude)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_FAVORITES, array.toString()).apply()
    }
}
