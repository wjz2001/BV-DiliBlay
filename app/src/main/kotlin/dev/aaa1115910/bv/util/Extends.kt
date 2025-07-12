package dev.aaa1115910.bv.util

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.core.text.HtmlCompat
import dev.aaa1115910.bv.BVApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

fun String.toast(context: Context, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(context, this, duration).show()
}

fun Int.toast(context: Context, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(context, context.getText(this), duration).show()
}

fun <T> SnapshotStateList<T>.swapList(newList: List<T>) {
    if (this.isEmpty()) {
        // 空列表直接添加全部
        addAll(newList)
        return
    }

    if (newList.isEmpty()) {
        // 新列表为空则清空
        clear()
        return
    }

    // 计算需要实际更新的部分
    val currentSize = this.size
    val newSize = newList.size
    val commonSize = minOf(currentSize, newSize)

    // 1. 更新共同部分（复用已有对象）
    for (i in 0 until commonSize) {
        this[i] = newList[i]
    }

    // 2. 如果新列表更长，添加额外项
    if (newSize > currentSize) {
        addAll(newList.subList(currentSize, newSize))
    }
    // 3. 如果旧列表更长，移除多余项
    else if (currentSize > newSize) {
        repeat(currentSize - newSize) {
            this.removeAt(newSize)
        }
    }
}

suspend fun <T> SnapshotStateList<T>.swapListWithMainContext(newList: List<T>) =
    withContext(Dispatchers.Main) { this@swapListWithMainContext.swapList(newList) }

suspend fun <T> SnapshotStateList<T>.swapListWithMainContext(
    newList: List<T>,
    delay: Long,
    afterSwap: () -> Unit
) {
    this@swapListWithMainContext.swapListWithMainContext(newList)
    delay(delay)
    afterSwap()
}

suspend fun <T> SnapshotStateList<T>.addAllWithMainContext(newList: List<T>) =
    withContext(Dispatchers.Main) { addAll(newList) }

suspend fun <T> SnapshotStateList<T>.addAllWithMainContext(newListBlock: suspend () -> List<T>) {
    val newList = newListBlock()
    withContext(Dispatchers.Main) { addAll(newList) }
}


suspend fun <T> SnapshotStateList<T>.addWithMainContext(item: T) =
    withContext(Dispatchers.Main) { add(item) }


fun <K, V> SnapshotStateMap<K, V>.swapMap(newMap: Map<K, V>) {
    clear()
    putAll(newMap)
}

suspend fun <K, V> SnapshotStateMap<K, V>.swapMapWithMainContext(newMap: Map<K, V>) =
    withContext(Dispatchers.Main) { this@swapMapWithMainContext.swapMap(newMap) }

fun <K, V> SnapshotStateMap<K, V>.swapMap(newMap: Map<K, V>, afterSwap: () -> Unit) {
    this.swapMap(newMap)
    afterSwap()
}

fun Date.formatPubTimeString(context: Context = BVApp.context): String {
    // 创建一个SimpleDateFormat对象，指定格式
    val formatter = SimpleDateFormat("yyyy年MM月dd日HH:mm")
    // 格式化Date对象
    val formattedDate = formatter.format(this)
    return formattedDate
}

fun Long.formatMinSec(): String {
    return if (this < 0L) {
        "..."
    } else {
        String.format(
            "%02d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(this),
            TimeUnit.MILLISECONDS.toSeconds(this) -
                    TimeUnit.MINUTES.toSeconds(
                        TimeUnit.MILLISECONDS.toMinutes(this)
                    )
        )
    }
}

fun Long.toMBString(): String = String.format("%.2f MB", this / 1024f / 1024f)

/**
 * 改进的请求焦点的方法，失败后等待 100ms 后重试
 */
fun FocusRequester.requestFocus(scope: CoroutineScope) {
    scope.launch(Dispatchers.Default) {
        runCatching {
            requestFocus()
        }.onFailure {
            delay(100)
            runCatching { requestFocus() }
        }
    }
}

fun String.removeHtmlTags(): String = HtmlCompat.fromHtml(
    this, HtmlCompat.FROM_HTML_MODE_LEGACY
).toString()

fun KeyEvent.isKeyDown(): Boolean = type == KeyEventType.KeyDown
fun KeyEvent.isKeyUp(): Boolean = type == KeyEventType.KeyUp
fun KeyEvent.isDpadUp(): Boolean = key == Key.DirectionUp
fun KeyEvent.isDpadDown(): Boolean = key == Key.DirectionDown
fun KeyEvent.isDpadLeft(): Boolean = key == Key.DirectionLeft
fun KeyEvent.isDpadRight(): Boolean = key == Key.DirectionRight

fun Int.stringRes(context: Context): String = context.getString(this)