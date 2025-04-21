package com.xabber.xmpp.jid

class XMPPJID {
    val localPart: String?
    val domainPart: String
    val resourcePart: String?

    constructor(fullJID: String) {
        val pattern = "^(?:([^\\/@]+)@)?([^@\\/]+)(?:/(.+))?$".toRegex()
        val match = pattern.matchEntire(fullJID)
            ?: throw IllegalArgumentException("Invalid JID format: $fullJID")

        localPart = match.groups[1]?.value
        domainPart = match.groups[2]?.value
            ?: throw IllegalArgumentException("Domain part is required: $fullJID")
        resourcePart = match.groups[3]?.value

        validateParts()
    }

    constructor(localPart: String?, domainPart: String, resourcePart: String?) {
        this.localPart = localPart
        this.domainPart = domainPart
        this.resourcePart = resourcePart

        validateParts()
    }

    private fun validateParts() {
        if (domainPart.isBlank()) {
            throw IllegalArgumentException("Domain part cannot be empty")
        }

        if (localPart?.isBlank() == true) {
            throw IllegalArgumentException("Local part cannot be empty if provided")
        }

        if (resourcePart?.isBlank() == true) {
            throw IllegalArgumentException("Resource part cannot be empty if provided")
        }

        // Basic JID format validation
        if (localPart?.contains("@") == true || localPart?.contains("/") == true) {
            throw IllegalArgumentException("Local part contains invalid characters: $localPart")
        }

        if (domainPart.contains("@") || domainPart.contains("/")) {
            throw IllegalArgumentException("Domain part contains invalid characters: $domainPart")
        }

        if (resourcePart?.contains("@") == true || resourcePart?.contains("/") == true) {
            throw IllegalArgumentException("Resource part contains invalid characters: $resourcePart")
        }
    }

    override fun toString(): String {
        val builder = StringBuilder()
        localPart?.let { builder.append(it).append("@") }
        builder.append(domainPart)
        resourcePart?.let { builder.append("/").append(it) }
        return builder.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is XMPPJID) return false
        return localPart == other.localPart &&
                domainPart == other.domainPart &&
                resourcePart == other.resourcePart
    }

    override fun hashCode(): Int {
        var result = localPart?.hashCode() ?: 0
        result = 31 * result + domainPart.hashCode()
        result = 31 * result + (resourcePart?.hashCode() ?: 0)
        return result
    }
}