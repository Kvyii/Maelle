package com.kvyii.maelle.core

import com.fasterxml.jackson.databind.DeserializationFeature
import com.kvyii.maelle.core.http.HttpResponse
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue

val mapper: JsonMapper = JsonMapper.builder()
    .addModule(kotlinModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .build()

inline fun <reified T> parseJson(value: String): T {
    return mapper.readValue(value)
}

inline fun <reified T> tryParseJson(value: String?): T? {
    return try {
        parseJson(value ?: return null)
    } catch (_: Exception) {
        null
    }
}

inline fun <reified T> HttpResponse.parsed(): T = parseJson(this.text)

inline fun <reified T> HttpResponse.parsedSafe(): T? = tryParseJson(this.text)
