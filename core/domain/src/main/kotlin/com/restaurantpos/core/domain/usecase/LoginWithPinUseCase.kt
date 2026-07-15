package com.restaurantpos.core.domain.usecase

import com.restaurantpos.core.domain.repository.UserRepository
import com.restaurantpos.core.model.User
import java.security.MessageDigest

class LoginWithPinUseCase(private val userRepo: UserRepository) {

    sealed interface Result {
        data class Success(val user: User) : Result
        data object InvalidPin : Result
        data object UserInactive : Result
    }

    suspend operator fun invoke(pin: String): Result {
        val hash = pin.sha256()
        val user = userRepo.getByPin(hash) ?: return Result.InvalidPin
        if (!user.isActive) return Result.UserInactive
        return Result.Success(user)
    }
}

fun String.sha256(): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
