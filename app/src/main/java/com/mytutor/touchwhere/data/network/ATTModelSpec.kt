package com.mytutor.touchwhere.data.network

import okhttp3.MultipartBody
import okhttp3.RequestBody

interface ATTModelSpec {
    val endPoint:String
    fun toFilePart(): MultipartBody.Part
    fun toMultipartParts(): Map<String, RequestBody>
}