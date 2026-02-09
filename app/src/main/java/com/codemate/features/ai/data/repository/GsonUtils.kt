package com.codemate.features.ai.data.repository

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

/**
 * Gson工具类
 * 提供全局的Gson实例和常用方法
 */
object GsonUtils {
    val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create()
    
    /**
     * 将对象转换为JSON字符串
     */
    fun toJson(obj: Any): String = gson.toJson(obj)
    
    /**
     * 将JSON字符串转换为对象
     */
    fun <T> fromJson(json: String, type: Class<T>): T = gson.fromJson(json, type)
    
    /**
     * 将JSON字符串转换为List
     */
    fun <T> fromJsonList(json: String, type: TypeToken<List<T>>): List<T> = 
        gson.fromJson(json, type.type)
    
    /**
     * 将JSON字符串转换为Map
     */
    fun fromJsonMap(json: String): Map<String, Any> = 
        gson.fromJson(json, object : TypeToken<Map<String, Any>>() {}.type)
}