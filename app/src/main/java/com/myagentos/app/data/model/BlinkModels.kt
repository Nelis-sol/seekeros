package com.myagentos.app.data.model

/**
 * Represents a single action button in a Blink
 */
data class BlinkAction(
    val label: String,
    val href: String,
    val parameters: List<BlinkParameter>? = null
)

/**
 * Parameter for actions requiring user input
 */
data class BlinkParameter(
    val name: String,
    val label: String?,
    val required: Boolean = false
)

/**
 * Represents a linked action (related actions)
 */
data class BlinkLinkedAction(
    val href: String,
    val label: String,
    val parameters: List<BlinkParameter>? = null
)

/**
 * Metadata for a Blink (result of GET request to action endpoint)
 */
data class BlinkMetadata(
    val title: String,
    val icon: String, // Small square icon/logo
    val description: String,
    val label: String?, // Label for the primary action
    val disabled: Boolean = false,
    val links: BlinkLinks? = null,
    val error: BlinkError? = null,
    val actionUrl: String = "", // The action endpoint URL
    val parameters: List<BlinkParameter>? = null, // Parameters for the primary action
    val image: String? = null // Optional larger banner image
)

/**
 * Links object containing related actions
 */
data class BlinkLinks(
    val actions: List<BlinkLinkedAction>
)

/**
 * Error response from action endpoint
 */
data class BlinkError(
    val message: String
)

/**
 * Full GET response structure from action endpoint
 */
data class BlinkResponse(
    val title: String,
    val icon: String,
    val description: String,
    val label: String?,
    val disabled: Boolean = false,
    val links: BlinkLinks? = null,
    val error: BlinkError? = null
)

