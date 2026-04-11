package com.cloudorz.openmonitor.core.data.util

import org.json.JSONObject

class ApiException(val code: Int, message: String) : Exception("[$code] $message")

object ApiResponseParser {

    fun unwrapData(responseBody: String): JSONObject {
        val json = JSONObject(responseBody)
        val code = json.getInt("code")
        if (code != 0) {
            val message = json.optString("message", "unknown error")
            throw ApiException(code, message)
        }
        return json.getJSONObject("data")
    }
}
