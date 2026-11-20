package com.example.data

import android.util.Log

/**
 * Granular permissions mapped to application roles.
 */
data class RolePermissions(
    val canAccessAdminPanel: Boolean,
    val canModifySystemConfig: Boolean,
    val canViewAllSecurityAuditLogs: Boolean,
    val canApproveDrivers: Boolean,
    val canClaimRides: Boolean,
    val canUpdateRideCoordinates: Boolean,
    val canBookRides: Boolean,
    val canCancelOwnRide: Boolean,
    val canRateCompletedRide: Boolean,
    val canViewDispatcherQueue: Boolean
)

/**
 * Authentication and credential context used for client-side pre-flight checks
 * before dispatching network operations to Firestore.
 */
data class UserAuthContext(
    val uid: String,
    val phoneNumber: String,
    val name: String,
    val role: UserRole,
    val email: String? = null,
    val isApprovedDriver: Boolean = false,
    val isBiometricVerified: Boolean = false,
    val isMasterKeyVerified: Boolean = false
) {
    val isOwnerAdmin: Boolean
        get() = uid == JaranwalaRepository.IMMUTABLE_OWNER_UID ||
                (email?.lowercase() == JaranwalaRepository.IMMUTABLE_OWNER_EMAIL.lowercase()) ||
                (phoneNumber.replace(" ", "").replace("-", "") == JaranwalaRepository.IMMUTABLE_OWNER_PHONE.replace(" ", "").replace("-", "")) ||
                (phoneNumber.replace(" ", "").replace("-", "") == "03429348582") ||
                (phoneNumber.replace(" ", "").replace("-", "") == "+923429348582")
}

/**
 * Result of client-side schema and rule verification.
 */
sealed class OperationPermissionResult {
    object Granted : OperationPermissionResult()
    data class Denied(
        val reason: String,
        val violationType: SecurityEventType,
        val requiredRole: UserRole? = null
    ) : OperationPermissionResult()
}

/**
 * Helper class that mirrors the Firestore security rules schema in Kotlin.
 * Validates role-based access control and owner identity locks *before* executing
 * database reads, writes, or state transitions.
 */
object UserRoleSecurityManager {

    private const val TAG = "UserRoleSecurityManager"

    /**
     * Returns the permission set assigned to a specific [UserRole].
     */
    fun getPermissionsForRole(role: UserRole, isOwnerAdmin: Boolean = false): RolePermissions {
        return when (role) {
            UserRole.ADMIN -> {
                RolePermissions(
                    canAccessAdminPanel = isOwnerAdmin,
                    canModifySystemConfig = isOwnerAdmin,
                    canViewAllSecurityAuditLogs = isOwnerAdmin,
                    canApproveDrivers = isOwnerAdmin,
                    canClaimRides = false,
                    canUpdateRideCoordinates = false,
                    canBookRides = false,
                    canCancelOwnRide = true,
                    canRateCompletedRide = false,
                    canViewDispatcherQueue = isOwnerAdmin
                )
            }
            UserRole.DRIVER -> {
                RolePermissions(
                    canAccessAdminPanel = false,
                    canModifySystemConfig = false,
                    canViewAllSecurityAuditLogs = false,
                    canApproveDrivers = false,
                    canClaimRides = true,
                    canUpdateRideCoordinates = true,
                    canBookRides = false,
                    canCancelOwnRide = false,
                    canRateCompletedRide = false,
                    canViewDispatcherQueue = true
                )
            }
            UserRole.PASSENGER -> {
                RolePermissions(
                    canAccessAdminPanel = false,
                    canModifySystemConfig = false,
                    canViewAllSecurityAuditLogs = false,
                    canApproveDrivers = false,
                    canClaimRides = false,
                    canUpdateRideCoordinates = false,
                    canBookRides = true,
                    canCancelOwnRide = true,
                    canRateCompletedRide = true,
                    canViewDispatcherQueue = false
                )
            }
        }
    }

    /**
     * Verifies access to administrative collections (/admins, /config/security, /security_audit_logs).
     * Strictly verifies the Immutable Owner Identity of MAHI (UID: ADMIN-MAHI).
     */
    fun validateAdminAccess(authContext: UserAuthContext): OperationPermissionResult {
        if (!authContext.isOwnerAdmin) {
            try {
                Log.w(TAG, "Security Violation: Non-owner (${authContext.uid}) attempted admin access.")
            } catch (_: Throwable) {
                // Ignore in headless local JVM tests where android.util.Log is not mocked
            }
            return OperationPermissionResult.Denied(
                reason = "Administrative collections are locked to MAHI Owner (${JaranwalaRepository.IMMUTABLE_OWNER_UID}).",
                violationType = SecurityEventType.UNAUTHORIZED_ELEVATION_ATTEMPT,
                requiredRole = UserRole.ADMIN
            )
        }
        return OperationPermissionResult.Granted
    }

    /**
     * Verifies passenger user profile creation or modification under `/users/{userId}`.
     * Enforces path scoping (caller can only write their own doc) and prevents self-elevation to ADMIN.
     */
    fun validateUserProfileWrite(
        authContext: UserAuthContext,
        targetUserId: String,
        targetRole: UserRole
    ): OperationPermissionResult {
        // Path scoping check: user can only modify their own profile unless MAHI Owner Admin
        if (authContext.uid != targetUserId && !authContext.isOwnerAdmin) {
            return OperationPermissionResult.Denied(
                reason = "Cross-user document modification denied for target $targetUserId.",
                violationType = SecurityEventType.UNAUTHORIZED_ELEVATION_ATTEMPT
            )
        }

        // Anti-Elevation Guard: Standard users cannot set their role to ADMIN in Firestore
        if (targetRole == UserRole.ADMIN && !authContext.isOwnerAdmin) {
            return OperationPermissionResult.Denied(
                reason = "Role escalation to ADMIN is strictly forbidden for public users.",
                violationType = SecurityEventType.UNAUTHORIZED_ELEVATION_ATTEMPT,
                requiredRole = UserRole.ADMIN
            )
        }

        return OperationPermissionResult.Granted
    }

    /**
     * Verifies driver profile creation or modification under `/drivers/{driverId}`.
     * Prevents drivers from self-approving or modifying ratings/earnings.
     */
    fun validateDriverProfileWrite(
        authContext: UserAuthContext,
        targetDriverId: String,
        isCreatingProfile: Boolean,
        requestedApproval: Boolean,
        modifiedFields: Set<String>
    ): OperationPermissionResult {
        // Path scoping: driver can only write their own document path unless MAHI
        if (authContext.uid != targetDriverId && !authContext.isOwnerAdmin) {
            return OperationPermissionResult.Denied(
                reason = "Access to /drivers/$targetDriverId is restricted to the driver themselves.",
                violationType = SecurityEventType.UNAUTHORIZED_ELEVATION_ATTEMPT
            )
        }

        // Self-Approval Prevention: Initial creation must have isApproved == false
        if (isCreatingProfile && requestedApproval && !authContext.isOwnerAdmin) {
            return OperationPermissionResult.Denied(
                reason = "Captain registration must default to unapproved (isApproved = false).",
                violationType = SecurityEventType.UNAUTHORIZED_ELEVATION_ATTEMPT
            )
        }

        // Protected fields check
        val protectedFields = setOf("isApproved", "statusBadge", "rating", "totalRides", "earningsPKR")
        val attemptedProtectedModifications = modifiedFields.intersect(protectedFields)
        if (attemptedProtectedModifications.isNotEmpty() && !authContext.isOwnerAdmin) {
            return OperationPermissionResult.Denied(
                reason = "Captain cannot alter administrative fields: $attemptedProtectedModifications.",
                violationType = SecurityEventType.UNAUTHORIZED_ELEVATION_ATTEMPT
            )
        }

        return OperationPermissionResult.Granted
    }

    /**
     * Verifies whether the user can create a ride booking under `/rides/{rideId}`.
     */
    fun validateRideCreation(
        authContext: UserAuthContext,
        passengerId: String
    ): OperationPermissionResult {
        if (authContext.uid != passengerId && !authContext.isOwnerAdmin) {
            return OperationPermissionResult.Denied(
                reason = "Passengers can only create rides under their own authenticated UID.",
                violationType = SecurityEventType.UNAUTHORIZED_ELEVATION_ATTEMPT
            )
        }
        return OperationPermissionResult.Granted
    }

    /**
     * Verifies whether the user can read a given ride request.
     */
    fun validateRideRead(
        authContext: UserAuthContext,
        ride: RideBooking
    ): OperationPermissionResult {
        val isPassenger = authContext.uid == ride.passengerId
        val isAssignedDriver = authContext.uid == ride.driverId
        val isSearchingDriver = authContext.role == UserRole.DRIVER &&
                authContext.isApprovedDriver &&
                ride.status == RideStatus.SEARCHING

        if (isPassenger || isAssignedDriver || isSearchingDriver || authContext.isOwnerAdmin) {
            return OperationPermissionResult.Granted
        }

        return OperationPermissionResult.Denied(
            reason = "Unauthorized read attempt on ride ${ride.id}. Caller is not an assigned participant.",
            violationType = SecurityEventType.UNAUTHORIZED_ELEVATION_ATTEMPT
        )
    }

    /**
     * Verifies state transitions and field mutations on `/rides/{rideId}`.
     */
    fun validateRideStatusUpdate(
        authContext: UserAuthContext,
        currentRide: RideBooking,
        newStatus: RideStatus,
        newDriverId: String? = null
    ): OperationPermissionResult {
        if (authContext.isOwnerAdmin) {
            return OperationPermissionResult.Granted
        }

        // Driver claiming ride (SEARCHING -> ACCEPTED)
        if (currentRide.status == RideStatus.SEARCHING && newStatus == RideStatus.ACCEPTED) {
            if (authContext.role != UserRole.DRIVER || !authContext.isApprovedDriver) {
                return OperationPermissionResult.Denied(
                    reason = "Only approved captains can accept active ride requests.",
                    violationType = SecurityEventType.UNAUTHORIZED_ELEVATION_ATTEMPT
                )
            }
            if (newDriverId != authContext.uid) {
                return OperationPermissionResult.Denied(
                    reason = "Driver cannot claim a ride on behalf of another driver UID.",
                    violationType = SecurityEventType.UNAUTHORIZED_ELEVATION_ATTEMPT
                )
            }
            return OperationPermissionResult.Granted
        }

        // Driver updating ride progression (ACCEPTED -> DRIVER_ARRIVED -> IN_PROGRESS -> COMPLETED)
        if (authContext.uid == currentRide.driverId) {
            val allowedDriverTransitions: Map<RideStatus, Set<RideStatus>> = mapOf(
                RideStatus.ACCEPTED to setOf(RideStatus.DRIVER_ARRIVED, RideStatus.CANCELLED),
                RideStatus.DRIVER_ARRIVED to setOf(RideStatus.IN_PROGRESS, RideStatus.CANCELLED),
                RideStatus.IN_PROGRESS to setOf(RideStatus.COMPLETED)
            )
            val allowedNext = allowedDriverTransitions[currentRide.status] ?: emptySet()
            if (newStatus !in allowedNext) {
                return OperationPermissionResult.Denied(
                    reason = "Invalid driver state transition from ${currentRide.status} to $newStatus.",
                    violationType = SecurityEventType.UNAUTHORIZED_ELEVATION_ATTEMPT
                )
            }
            return OperationPermissionResult.Granted
        }

        // Passenger cancelling ride before driver arrives
        if (authContext.uid == currentRide.passengerId) {
            if (newStatus == RideStatus.CANCELLED) {
                if (currentRide.status in listOf(RideStatus.SEARCHING, RideStatus.ACCEPTED)) {
                    return OperationPermissionResult.Granted
                }
                return OperationPermissionResult.Denied(
                    reason = "Passenger cannot cancel ride once captain has arrived or trip is in progress.",
                    violationType = SecurityEventType.UNAUTHORIZED_ELEVATION_ATTEMPT
                )
            }
        }

        return OperationPermissionResult.Denied(
            reason = "Unauthorized ride update attempt by user ${authContext.uid}.",
            violationType = SecurityEventType.UNAUTHORIZED_ELEVATION_ATTEMPT
        )
    }

    /**
     * Verifies system configuration changes (/config/{docId}).
     */
    fun validateConfigModification(authContext: UserAuthContext): OperationPermissionResult {
        if (!authContext.isOwnerAdmin) {
            return OperationPermissionResult.Denied(
                reason = "System configurations and base fares can only be modified by MAHI Owner.",
                violationType = SecurityEventType.UNAUTHORIZED_ELEVATION_ATTEMPT,
                requiredRole = UserRole.ADMIN
            )
        }
        return OperationPermissionResult.Granted
    }
}
