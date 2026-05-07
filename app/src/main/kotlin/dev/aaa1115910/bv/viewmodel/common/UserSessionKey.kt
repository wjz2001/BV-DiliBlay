package dev.aaa1115910.bv.viewmodel.common

import dev.aaa1115910.bv.repository.UserRepository

fun UserRepository.accountSessionKey(): String {
    return if (!isLogin) {
        "logout"
    } else {
        "$uid:$uidCkMd5:$sessData"
    }
}
