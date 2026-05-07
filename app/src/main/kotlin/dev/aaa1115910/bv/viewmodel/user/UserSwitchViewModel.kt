package dev.aaa1115910.bv.viewmodel.user

import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.dao.AppDatabase
import dev.aaa1115910.bv.entity.db.UserDB
import dev.aaa1115910.bv.repository.UserRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel
import qrcode.QRCode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@KoinViewModel
class UserSwitchViewModel(
    private val userRepository: UserRepository,
    private val db: AppDatabase = BVApp.getAppDatabase()
) : ViewModel() {
    var loading by mutableStateOf(true)
    private val _userDbList = MutableStateFlow(persistentListOf<UserDB>())
    val userDbList: StateFlow<ImmutableList<UserDB>> = _userDbList.asStateFlow()

    fun updateData() {
        viewModelScope.launch(Dispatchers.IO) {
            updateUserDbList()
            withContext(Dispatchers.Main) { loading = false }
        }
    }

    private suspend fun updateUserDbList() {
        withContext(Dispatchers.Main) {
            _userDbList.value = db.userDao().getAll().toPersistentList()
        }
    }

    fun switchUser(user: UserDB, onFinished: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            userRepository.setUser(user)
            withContext(Dispatchers.Main) { onFinished() }
        }
    }

    fun deleteUser(userDB: UserDB, onFinished: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            db.userDao().delete(userDB)
            updateUserDbList()
            val users = _userDbList.value
            if (users.isNotEmpty()) {
                userRepository.setUser(users.first())
            } else {
                userRepository.logout()
            }
            withContext(Dispatchers.Main) { onFinished() }
        }
    }

    fun generateAuthQrImage(auth: String, onImageGenerated: (ImageBitmap) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val output = ByteArrayOutputStream()
            QRCode(auth).render().writeImage(output)
            val input = ByteArrayInputStream(output.toByteArray())
            val image = BitmapFactory.decodeStream(input).asImageBitmap()
            withContext(Dispatchers.Main) {
                onImageGenerated(image)
            }
        }
    }
}
