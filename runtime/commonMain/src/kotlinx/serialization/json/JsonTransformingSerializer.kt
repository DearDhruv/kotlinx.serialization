/*
 * Copyright 2017-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.serialization.json

import kotlinx.serialization.*
import kotlinx.serialization.json.internal.*

/**
 * Base class for custom serializers that allows manipulating an abstract JSON
 * representation of the class before serialization or deserialization.
 *
 * [JsonTransformingSerializer] provides capabilities to manipulate [JsonElement] representation
 * directly instead of interacting with [Encoder] and [Decoder] in order to apply a custom
 * transformation to the JSON.
 * Please note that this class expects that [Encoder] and [Decoder] are implemented by [JsonDecoder] and [JsonEncoder],
 * i.e. serializers derived from this class work only with [Json] format.
 *
 * During serialization, this class first serializes original value with [tSerializer] to a [JsonElement],
 * then calls [writeTransform] method, which may contain a user-defined transformation, such as
 * wrapping a value into [JsonArray], filtering keys, adding keys, etc.
 *
 * During deserialization, the opposite process happens: first, value from JSON stream is read
 * to a [JsonElement], second, user transformation in [readTransform] is applied,
 * and then JSON tree is deserialized back to [T] with [tSerializer].
 *
 * Usage example:
 *
 * ```
 * @Serializable
 * data class Example(
 *     @Serializable(UnwrappingJsonListSerializer::class) val data: String
 * )
 * // Unwraps a list to a single object
 * object UnwrappingJsonListSerializer :
 *     JsonTransformingSerializer<String>(String.serializer(), "UnwrappingList") {
 *     override fun readTransform(element: JsonElement): JsonElement {
 *         if (element !is JsonArray) return element
 *         require(element.size == 1) { "Array size must be equal to 1 to unwrap it" }
 *         return element.first()
 *     }
 * }
 * // Now these functions both yield correct result:
 * Json.parse(Example.serializer(), """{"data":["str1"]}""")
 * Json.parse(Example.serializer(), """{"data":"str1"}""")
 * ```
 *
 * @param T A type for Kotlin property for which this serializer could be applied.
 *        **Not** the type that you may encounter in JSON. (e.g. if you unwrap a list
 *        to a single value `T`, use `T`, not `List<T>`)
 * @param tSerializer A serializer for type [T]. Determines [JsonElement] which is passed to [writeTransform].
 *        Should be able to parse [JsonElement] from [readTransform] function.
 *        Usually, default serializer is sufficient.
 */
public abstract class JsonTransformingSerializer<T : Any>(
    private val tSerializer: KSerializer<T>
) : KSerializer<T> {

    @Deprecated(
        "Transformation name parameter is no longer used in TransformingSerializer. To add custom serial name to transformation, override SerialDescriptor.",
        ReplaceWith("JsonTransformingSerializer<T>(tSerializer)"),
        DeprecationLevel.ERROR
    )
    public constructor(
        tSerializer: KSerializer<T>,
        transformationName: String
    ) : this(tSerializer)

    /**
     * A descriptor for this transformation.
     * By default, it delegates to [tSerializer]'s descriptor.
     *
     * However, this descriptor can be overridden to achieve better representation of the resulting JSON shape
     * for schema generating or introspection purposes.
     */
    override val descriptor: SerialDescriptor get() = tSerializer.descriptor

    final override fun serialize(encoder: Encoder, value: T) {
        val output = encoder.asJsonEncoder()
        var element = output.json.writeJson(value, tSerializer)
        element = transformSerialize(element)
        output.encodeJsonElement(element)
    }

    final override fun deserialize(decoder: Decoder): T {
        val input = decoder.asJsonDecoder()
        val element = input.decodeJsonElement()
        return input.json.decodeFromJsonElement(tSerializer, transformDeserialize(element))
    }

    /**
     * Transformation that happens during [deserialize] call.
     * Does nothing by default.
     */
    @Deprecated(
        "This method was renamed to better reflect its purpose",
        ReplaceWith("transformDeserialize(element)"),
        DeprecationLevel.ERROR
    )
    protected fun readTransform(element: JsonElement): JsonElement = transformDeserialize(element)

    /**
     * Transformation that happens during [deserialize] call.
     * Does nothing by default.
     */
    protected open fun transformDeserialize(element: JsonElement): JsonElement = element

    /**
     * Transformation that happens during [serialize] call.
     * Does nothing by default.
     */
    @Deprecated(
        "This method was renamed to better reflect its purpose",
        ReplaceWith("transformSerialize(element)"),
        DeprecationLevel.ERROR
    )
    protected fun writeTransform(element: JsonElement): JsonElement = transformSerialize(element)

    /**
     * Transformation that happens during [serialize] call.
     * Does nothing by default.
     */
    protected open fun transformSerialize(element: JsonElement): JsonElement = element
}
