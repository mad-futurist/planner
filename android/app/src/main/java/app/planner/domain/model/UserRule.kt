package app.planner.domain.model

data class UserRule(val id: String, val description: String, val conditions: String, val action: String, val enabled: Boolean, val explicitlyApproved: Boolean)
