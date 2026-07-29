package com.mytutor.touchwhere.data.network

import java.io.File
//okhttp3
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody

data class WhisperModelSpec(
    val fileFormat:String = "audio/m4a",
    val file: File,
    val model:String = "whisper-1",
    val prompt:String = "The next voice file contains a user's request to do certain actions using a smartphone.",
    val language:String = "ko"
): ATTModelSpec {
    private fun String.toPart() = this.toRequestBody("text/plain".toMediaTypeOrNull())
    override val endPoint: String = "v1/audio/transcriptions"
    override fun toFilePart(): MultipartBody.Part {
        val requestFile = file.asRequestBody(fileFormat.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("file", file.name, requestFile)
    }

    override fun toMultipartParts(): Map<String, RequestBody> {
        return mapOf(
            "model" to model.toPart(),
            "prompt" to prompt.toPart(),
            "language" to language.toPart()
        )
    }
}
