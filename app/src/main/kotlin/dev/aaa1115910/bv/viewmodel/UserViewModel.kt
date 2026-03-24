package dev.aaa1115910.bv.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.http.BiliHttpApi
import dev.aaa1115910.biliapi.http.entity.AuthFailureException
import dev.aaa1115910.biliapi.http.entity.user.MyInfoData
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.repository.UserRepository
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.toast
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class UserViewModel(
    private val userRepository: UserRepository
) : ViewModel() {
    private var shouldUpdateInfo = true

    @Volatile
    private var requestVersion = 0L

    private val logger = KotlinLogging.logger { }
    val isLogin get() = userRepository.isLogin
    val username get() = userRepository.username
    val face get() = userRepository.avatar

    var responseData: MyInfoData? by mutableStateOf(null)

    fun updateUserInfo(forceUpdate: Boolean = false) {
        if (!forceUpdate) {
            if (!shouldUpdateInfo || !userRepository.isLogin) return
        } else {
            if (!userRepository.isLogin) return
        }

        val expectedVersion = requestVersion + 1L
        requestVersion = expectedVersion

        logger.fInfo { "Update user info" }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val data = BiliHttpApi.getUserSelfInfo(sessData = Prefs.sessData).getResponseData()
                withContext(Dispatchers.Main) {
                    if (expectedVersion != requestVersion) return@withContext
                    responseData = data
                    shouldUpdateInfo = false
                    userRepository.username = data.name
                    userRepository.avatar = data.face
                }
                logger.fInfo { "Update user info success" }
            }.onFailure {
                when (it) {
                    is AuthFailureException -> {
                        if (expectedVersion != requestVersion) return@onFailure
                        withContext(Dispatchers.Main) {
                            if (expectedVersion != requestVersion) return@withContext
                            BVApp.context.getString(R.string.exception_auth_failure)
                                .toast(BVApp.context)
                            logger.fInfo { "User auth failure" }
                            if (!BuildConfig.DEBUG) userRepository.logout()
                        }
                    }

                    else -> {
                        if (expectedVersion != requestVersion) return@onFailure
                        withContext(Dispatchers.Main) {
                            if (expectedVersion != requestVersion) return@withContext
                            "获取用户信息失败：${it.message}".toast(BVApp.context)
                        }
                    }
                }
            }
        }
    }

    fun logout() {
        requestVersion++
        shouldUpdateInfo = true
        responseData = null
        userRepository.username = ""
        userRepository.avatar = ""

        viewModelScope.launch(Dispatchers.IO) {
            userRepository.logout()
        }
    }

    fun clearUserInfo() {
        requestVersion++
        shouldUpdateInfo = true
        responseData = null
        userRepository.username = ""
        userRepository.avatar = ""
    }
}