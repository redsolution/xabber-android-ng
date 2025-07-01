package com.xabber.dto

data class GroupDto(
    val primary: String,
    val owner: String,
    val name: String,
    val groupName: String, // Localized name
    val isSystemGroup: Boolean,
    val isCollapsed: Boolean,
    val order: Int,
    val contactCount: Int // Number of contacts in the group
) : Comparable<GroupDto> {
    override fun compareTo(other: GroupDto): Int {
        // Sort by order, then by groupName
        return if (order != other.order) {
            order - other.order
        } else {
            groupName.compareTo(other.groupName)
        }
    }
}