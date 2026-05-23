package dev.aaa1115910.bv.wjzfocus

import android.os.SystemClock
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.util.fDebug
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.fWarn
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 焦点系统日志。只记录调试/降级信息，不参与业务决策。 */
private val wjzFocusLogger = KotlinLogging.logger("WjzFocusCoordinator")
/** 普通 pending 请求的默认存活时间： */
private const val DefaultPendingTtlMillis = 1_500L
/** 焦点锁定期间 pending 请求续期后的存活时间： */
private const val LockedPendingTtlMillis = 1_500L
/** Lazy 列表恢复请求的默认存活时间，略长于普通节点请求： */
private const val LazyPendingTtlMillis = 3_000L
/** 生命周期恢复补偿请求的默认存活时间，覆盖页面恢复后较慢的重挂载窗口： */
private const val ResumeRestoreTtlMillis = 10_000L
/** Lazy 恢复滚动后等待目标 item 可见并重新挂载的默认最长时间。 */
const val WjzLazyFocusDefaultRestoreTimeoutMillis = 1_000L
/** 同一节点连续请求失败达到该阈值后，降级为恢复当前 layer： */
private const val FailureDegradeThreshold = 3
/** 失败计数清零时间窗口，超过该窗口的旧失败不再累加，避免偶发失败累计触发降级： */
private const val FailureCountResetWindowMillis = 3_000L
/** 焦点锁最长存活时间，避免异常 token 让 TV 遥控永久失效。 */
private const val FocusLockMaxTimeoutMillis = 3_000L
/** dropExpiredPendingRequests 的节流间隔，避免每帧高频路径反复触发 notifyPendingChanged： */
private const val DropExpiredThrottleMillis = 50L
/** 每个 layer 保留的最近焦点记录上限。 */
private const val MaxRecentFocusRecords = 16

/**
 * 焦点活动层。
 *
 * layer 表示“当前哪一块区域有资格接收焦点”。同一时刻内 coordinator 中只有一个[[WjzFocusCoordinator.activeLayer]]，
 * 节点请求必须落在 active layer 内，Dialog、Drawer、Player 等独立区域通过切换 layer 来避免互相抢焦点。
 *
 * 这里保持 enum。因为 wjzfocus 当前是应用内焦点基建，
 * 集中枚举能保留 when 穷举检查、IDE 跳转和全局 layer 清单，避免拼写错误降级为运行时问题。
 */
enum class WjzFocusLayer {
    /** 主内容区。 */
    Content,
    /** 顶部导航。 */
    TopNav,
    /** 侧边抽屉。 */
    Drawer,
    /** 播放器控制层。 */
    Player,
    /** 普通覆盖层。 */
    Overlay,
    /** Dialog/Popup 独立窗口层。 */
    Dialog,
    /** 软键盘层。 */
    Keyboard,
    /** 操作按钮层。 */
    Action
}

/**
 * Host 进入某个 scope 时的恢复策略。
 *
 * `RequestFocus` 表示该节点自己就是可执行目标，`Container` 表示该节点只是容器，
 * 进入时应继续在容器内部寻找末端节点。
 */
enum class WjzFocusRestoreStrategy {
    /** 直接请求该节点自己的 requester。 */
    RequestFocus,
    /** 该节点是容器，进入时应寻找容器内可请求的末端节点。 */
    Container
}

/** coordinator 内部节点类型，由 restore strategy 派生，不作为注册入参。 */
internal enum class WjzFocusNodeKind {
    /** 可直接执行 requester 的末端节点。 */
    Leaf,
    /** 只表示路径/容器，进入时需要继续寻找末端节点。 */
    Container
}

/** 焦点请求的详细结果，用于区分立即成功、已入队等待和真实失败。 */
enum class WjzFocusRequestResult {
    /** 已执行原生请求并立即获得焦点。 */
    Focused,
    /** 目标暂不可请求，但请求已进入 pending 队列等待后续消费。 */
    Enqueued,
    /** 请求已被策略性跳过，不需要重试。 */
    Dropped,
    /** 请求未能立即成功，也没有进入 pending 队列。 */
    Failed
}

/** Host onEnter 入口请求结果。 */
internal enum class WjzFocusEnterRequestResult {
    /** 已通过 coordinator 统一请求流程聚焦到目标节点。 */
    Focused,
    /** 当前 Host 没有入口叶子节点，调用方应转成 WjzFocus 恢复请求并取消原生搜索。 */
    NativeSearch,
    /** 当前状态不允许进入该 Host，调用方应取消本次焦点变化。 */
    Cancelled,
    /** 找到了入口目标，但统一请求流程拒绝或执行失败。 */
    Failed
}

/** 记录来源焦点时返回的 token，用于精确恢复对应来源。 */
@JvmInline
value class WjzFocusSourceToken(
    val value: Long
)

/**
 * 已注册的焦点节点。
 *
 * 该结构只在 coordinator 内部流转，外部通过 `Modifier.wjzFocusable` 或`wjzFocusRegistration` 注册节点。
 *
 * 其中 [scopeId] 表示模块边界，[id] 表示最终执行目标，[fallback] 和 [globalFallback] 分别用于 scope 内兜底和 layer 级全局兜底。
 */
internal data class WjzFocusNode(
    val id: WjzFocusNodeId,
    val layer: WjzFocusLayer,
    val requester: FocusRequester,
    val strategy: WjzFocusRestoreStrategy = WjzFocusRestoreStrategy.RequestFocus,
    val scopeId: WjzFocusScopeId? = null,
    val fallback: Boolean = false,
    val globalFallback: Boolean = false,
    val requestPolicy: WjzFocusRequestPolicy = WjzFocusAllowAllRequests,
    val directionHandlers: List<WjzFocusDirectionHandler> = emptyList(),
    val exits: List<WjzFocusNodeExit> = emptyList()
) {
    val kind: WjzFocusNodeKind
        get() = when (strategy) {
            WjzFocusRestoreStrategy.RequestFocus -> WjzFocusNodeKind.Leaf
            WjzFocusRestoreStrategy.Container -> WjzFocusNodeKind.Container
        }
}

/**
 * coordinator 内部 pending 队列的意图。
 *
 * pending 的职责是保留“应该去哪”的请求，同时避免目标未 ready 时源焦点丢失。
 * 每条请求都有 TTL，超时后会被丢弃，避免旧请求在界面状态变化后误命中。
 * TTL 、dedupe key 、generation 校验共同定义 pending 语义，不能替换成全局单 Job。
 * Dialog恢复 、source 恢复、Lazy item 恢复、Host resume 等请求可能合法并发。
 */
internal sealed interface WjzFocusIntent {
    val layer: WjzFocusLayer
    val scopeId: WjzFocusScopeId?

    /** 等目标节点挂载后请求具体 node。 */
    data class RequestNode(
        val nodeId: WjzFocusNodeId,
        override val layer: WjzFocusLayer,
        override val scopeId: WjzFocusScopeId?,
        val expectedGeneration: Int? = null
    ) : WjzFocusIntent

    /** 等 active layer 可恢复时恢复某个 scope。 */
    data class RestoreLayer(
        override val layer: WjzFocusLayer,
        override val scopeId: WjzFocusScopeId?
    ) : WjzFocusIntent

    /** disabled-region 恢复失败后的严格重试，只允许回到当前 layer + 当前 scope。 */
    data class RestoreDisabledScope(
        override val layer: WjzFocusLayer,
        override val scopeId: WjzFocusScopeId?
    ) : WjzFocusIntent

    /** 等普通焦点组内目标节点挂载后恢复焦点。 */
    data class RestoreGroup(
        val nodeId: WjzFocusNodeId,
        override val layer: WjzFocusLayer,
        override val scopeId: WjzFocusScopeId?,
        val restorerId: String,
        val listId: String,
        val fallbackNodeId: WjzFocusNodeId? = null
    ) : WjzFocusIntent

    /** 等 Lazy item 可见并重新挂载后恢复目标节点。 */
    data class RestoreLazyItem(
        val nodeId: WjzFocusNodeId,
        val itemKey: WjzFocusItemKey,
        override val layer: WjzFocusLayer,
        override val scopeId: WjzFocusScopeId?,
        val restorerId: String,
        val listId: String
    ) : WjzFocusIntent
}

/** 定向唤醒 group/lazy pending 消费者的门铃。 */
internal data class WjzFocusPendingWakeup(
    val layer: WjzFocusLayer,
    val scopeId: WjzFocusScopeId?,
    val restorerId: String,
    val listId: String,
    val kind: WjzFocusPendingWakeupKind
)

/** 门铃对应的消费类型。 */
internal enum class WjzFocusPendingWakeupKind {
    Group,
    Lazy
}

/** Lazy 恢复执行时传给 restorer 的完整目标信息。 */
internal data class WjzLazyFocusTarget(
    val nodeId: WjzFocusNodeId,
    val itemKey: WjzFocusItemKey,
    val layer: WjzFocusLayer,
    val scopeId: WjzFocusScopeId?,
    val restorerId: String,
    val listId: String,
    val expectedGeneration: Int?
)

/** 普通焦点组恢复执行时传给 restorer 的完整目标信息。 */
internal data class WjzGroupFocusTarget(
    val nodeId: WjzFocusNodeId,
    val layer: WjzFocusLayer,
    val scopeId: WjzFocusScopeId?,
    val restorerId: String,
    val listId: String,
    val expectedGeneration: Int?,
    val fallbackNodeId: WjzFocusNodeId?
)

/** Lazy 列表恢复器。实现方负责滚动到 item，并在 item 可见且节点挂载后请求焦点。 */
internal fun interface WjzLazyFocusRestorer {
    suspend fun restore(target: WjzLazyFocusTarget): Boolean
}

/** 普通焦点组恢复器。实现方负责在目标节点挂载后请求焦点。 */
internal fun interface WjzFocusGroupRestorer {
    suspend fun restore(target: WjzGroupFocusTarget): Boolean
}

/** 焦点锁接口。锁定期间 Host 会吞掉按键，pending 请求会续期等待。 */
interface WjzFocusLock {
    /** 当前是否存在至少一个锁 token。 */
    val isFocusLocked: Boolean

    /** 增加一个锁 token。相同 token 可重复进入，释放时按后进先出删除一个。 */
    fun lockFocus(token: Any)

    /** 释放一个锁 token，所有锁释放后会刷新并消费 pending 请求。 */
    fun unlockFocus(token: Any)

    /**
     * 锁定期间记录最后一次方向意图。
     *
     * 这不是按键重放队列：连续方向只保留最后一次，并在解锁后最多消费一次。
     * 确认键不走该路径，继续由 Host 按锁定期安全策略吞掉。
     */
    fun recordLockedDirectionIntent(direction: FocusDirection): Boolean
}

/**
 * Wjz 焦点协调器。
 *
 * 它是焦点系统的执行中心：注册节点、维护 active layer、记录来源、保存 pending、
 * 处理 lazy 恢复、执行最终的底层焦点请求。业务模块通常不直接操作内部状态，
 * 而是通过 [WjzFocusHost]、`Modifier.wjzFocusable` 和 entry 协议接入。
 *
 * 概念上可以这样理解：
 * 1. layer 是“当前活跃区域”。
 * 2. scope 是“模块边界/寻址边界”。
 * 3. nodeId 是“最终执行目标”。
 * 4. entryId 是“跨模块公开入口”，解析后才会落到 nodeId。
 *
 * 焦点状态语义不变量：
 *
 * - `focusedSnapshotByLayerScope` 是 path/subtree 观察状态，允许指向 Container。它只表示某个
 *   layer/scope path 或其子树内有焦点，适合物理区域、disabled-region 和“是否有焦点在这棵树里”
 *   这类判断，不能当作真实可操作焦点来源。
 * - `focusedLeafSnapshotByLayerScope` 是真实可操作焦点快照，只允许 RequestFocus leaf。Lazy/Scroll
 *   等需要知道“当前可恢复/可滚动到哪个真实节点”的代码必须读取它，而不是读取 path snapshot。
 * - `recentFocusByLayer` 是恢复历史，只允许 RequestFocus leaf。Container 的冒泡焦点不能进入 recent，
 *   否则恢复会落到不能执行焦点请求的路径节点。
 * - `sourceStack` 是跨 layer 来源恢复历史。已记录的 source node 缺失或暂未挂载时必须保留，
 *   让 `restoreSourceLayer` 能回退到来源 layer/scope 入口；只有当前存在且确定不属于记录的
 *   layer/scope 或不是 RequestFocus leaf 的 source node 才能修复清理。
 * - `lastFocusedScopeByLayer` 是 resume 恢复资格，只能由 RequestFocus leaf 写入。它表示某个 layer
 *   最近真正落焦的 scope，Container 的 path 焦点不能污染 resume 判断。
 *
 * 新增派生焦点状态时，必须先明确它属于 path/subtree 语义还是 leaf/restore 语义；前者可以观察
 * Container，后者必须过滤到 RequestFocus leaf。新增 source、recent、restore、resume、locked direction
 * 这类精确候选逻辑时，优先复用 `isSourceCandidateFor(...)`；需要确认目标本身就是可请求 leaf 时复用
 * `isRequestableLeafTargetFor(...)`。不要在这些路径里重新手写 `hasFocus + strategy` 判断。
 */
@Stable
class WjzFocusCoordinator : WjzFocusLock {
    private val entries = linkedMapOf<WjzFocusNodeId, WjzFocusEntry>()
    private val entriesByLayer = hashMapOf<WjzFocusLayer, LinkedHashMap<WjzFocusNodeId, WjzFocusEntry>>()
    private val entriesByLayerScope = hashMapOf<WjzFocusLayerScopeKey, LinkedHashMap<WjzFocusNodeId, WjzFocusEntry>>()
    private val entryOrders = hashMapOf<WjzFocusNodeId, Long>()
    private val entriesHosts = hashMapOf<WjzFocusComponentId, WjzFocusEntriesHostState>()
    private val lockTokens = mutableStateListOf<WjzFocusLockRecord>()
    private val sourceStack = mutableListOf<WjzFocusSource>()
    private val pendingRequests = mutableListOf<WjzFocusPendingRequest>()
    private val hostExits = linkedMapOf<Any, WjzFocusHostExitRecord>()
    private val groupRestorers = hashMapOf<WjzFocusGroupRestorerKey, WjzFocusGroupRestorer>()
    private val lazyRestorers = hashMapOf<WjzFocusLazyRestorerKey, WjzLazyFocusRestorer>()
    private val failureCounts = hashMapOf<WjzFocusNodeId, WjzFocusFailureRecord>()
    private val lastFocusedScopeByLayer = hashMapOf<WjzFocusLayer, WjzFocusScopeId?>()
    private val focusedSnapshotByLayerScope = mutableStateMapOf<WjzFocusLayerScopeKey, WjzFocusedNodeSnapshot>()
    private val focusedLeafSnapshotByLayerScope = mutableStateMapOf<WjzFocusLayerScopeKey, WjzFocusedNodeSnapshot>()
    private val recentFocusByLayer = hashMapOf<WjzFocusLayer, ArrayDeque<RecentFocusRecord>>()
    private val initialResumeRestoredLayers = mutableSetOf<WjzFocusLayer>()
    private val disabledFocusRegions = hashMapOf<Any, WjzDisabledFocusRegionRecord>()
    private val focusPropertiesInvalidators = hashMapOf<WjzFocusNodeId, WjzFocusPropertiesInvalidatorRecord>()
    private var generationSeed = 0
    private var entryOrderSeed = 0L
    private var sourceTokenSeed = 0L
    private var pendingRequestIdSeed = 0L
    private var globalFallbackNodeId: WjzFocusNodeId? = null
    private var globalFallbackGeneration: Int? = null
    private var strictRestoreWindow: WjzFocusStrictRestoreWindow? = null
    private var handlingFailureDegrade = false
    private var lastDropExpiredUptimeMillis: Long = 0L
    private var lockedDirectionIntent: WjzFocusLockedDirectionIntent? = null
    private var coordinatorFocusDepth = 0
    /**
     * Group/Lazy restorer 的定向门铃。
     *
     * pendingRequests 才是唯一事实来源，这个 SharedFlow 只负责告诉匹配的 consumer
     * “你可以去队列里看一眼”。即使门铃丢失，consumer 挂载时也会主动扫描队列，
     * 因此不会破坏恢复语义。
     */
    private val _pendingWakeupSignals = MutableSharedFlow<WjzFocusPendingWakeup>(
        extraBufferCapacity = 64
    )
    internal val pendingWakeupSignals: SharedFlow<WjzFocusPendingWakeup> =
        _pendingWakeupSignals.asSharedFlow()
    /**
     * 节点挂载/布局完成的定向门铃。
     *
     * Lazy 和普通组恢复等待的是具体 nodeId，不再监听全局 version。缓冲区满时丢弃旧事件，
     * 因为等待方每次被唤醒都会重新读取 coordinator 当前状态，最新状态才有意义。
     */
    private val _nodeMountWakeupSignals = MutableSharedFlow<WjzFocusNodeId>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val nodeMountWakeupSignals: SharedFlow<WjzFocusNodeId> =
        _nodeMountWakeupSignals.asSharedFlow()
    private val watchdogScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lockWatchdogJob: Job? = null
    var activeLayer by mutableStateOf(WjzFocusLayer.Content)
        private set
    var disabledFocusVersion by mutableIntStateOf(0)
        private set

    internal fun isCoordinatorFocusing(): Boolean = coordinatorFocusDepth > 0

    /**
     * 强制 coordinator 只在主线程访问。
     *
     * 这里没有把内部集合替换成并发集合，是因为焦点系统本质上绑定 Compose UI 线程。
     * Fail fast 比悄悄跨线程读写更容易定位问题，也能避免并发集合掩盖错误调用路径。
     */
    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "WjzFocusCoordinator must be accessed from the main thread."
        }
    }

    /**
     * 当前是否有焦点锁。
     *
     * getter 必须保持纯读取，不能在这里清理过期 token 或消费 pending；否则业务在组合阶段读取
     * 该属性时可能触发 Compose snapshot 读写冲突。过期 token 由 watchdog 主动清理。
     */
    override val isFocusLocked: Boolean
        get() = lockTokens.isNotEmpty()

    private fun putEntry(entry: WjzFocusEntry) {
        val previous = entries[entry.node.id]
        if (previous == null) {
            entryOrders[entry.node.id] = entryOrderSeed++
        }
        if (previous != null && previous.layerScopeKey != entry.layerScopeKey) {
            removeEntryFromIndexes(previous)
        }
        entries[entry.node.id] = entry
        entriesByLayer
            .getOrPut(entry.node.layer) { linkedMapOf() }
            .putOrdered(entry)
        entriesByLayerScope
            .getOrPut(entry.layerScopeKey) { linkedMapOf() }
            .putOrdered(entry)
    }

    private fun removeEntry(nodeId: WjzFocusNodeId): WjzFocusEntry? {
        val entry = entries.remove(nodeId) ?: return null
        removeEntryFromIndexes(entry)
        entryOrders.remove(nodeId)
        return entry
    }

    private fun removeEntryFromIndexes(entry: WjzFocusEntry) {
        entriesByLayer[entry.node.layer]?.let { layerEntries ->
            layerEntries.remove(entry.node.id)
            if (layerEntries.isEmpty()) entriesByLayer.remove(entry.node.layer)
        }
        entriesByLayerScope[entry.layerScopeKey]?.let { layerScopeEntries ->
            layerScopeEntries.remove(entry.node.id)
            if (layerScopeEntries.isEmpty()) entriesByLayerScope.remove(entry.layerScopeKey)
        }
    }

    private fun LinkedHashMap<WjzFocusNodeId, WjzFocusEntry>.putOrdered(entry: WjzFocusEntry) {
        if (containsKey(entry.node.id)) {
            this[entry.node.id] = entry
            return
        }

        val entryOrder = entryOrders.getValue(entry.node.id)
        val currentEntries = this.entries.toList()
        clear()

        var inserted = false
        for ((nodeId, currentEntry) in currentEntries) {
            if (!inserted && entryOrder < entryOrders.getValue(nodeId)) {
                this[entry.node.id] = entry
                inserted = true
            }
            this[nodeId] = currentEntry
        }
        if (!inserted) {
            this[entry.node.id] = entry
        }
    }

    private fun entriesInLayer(layer: WjzFocusLayer): Collection<WjzFocusEntry> {
        return entriesByLayer[layer]?.values ?: emptyList()
    }

    private fun entriesInLayerScope(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?
    ): Collection<WjzFocusEntry> {
        return entriesByLayerScope[WjzFocusLayerScopeKey(layer, scopeId)]?.values ?: emptyList()
    }

    private fun entriesInLayerScope(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?,
        strictScope: Boolean
    ): Collection<WjzFocusEntry> {
        return if (strictScope || scopeId != null) {
            entriesInLayerScope(layer, scopeId)
        } else {
            entriesInLayer(layer)
        }
    }

    /**
     * 注册节点并返回本次挂载 generation。
     *
     * generation 用来区分同一个 [WjzFocusNodeId] 的不同组合生命周期，避免旧 pending 请求误打到重组后的新节点。
     */
    internal fun register(node: WjzFocusNode): Int {
        checkMainThread()
        require(!node.fallback || node.strategy != WjzFocusRestoreStrategy.Container) {
            "WjzFocus fallback node cannot use Container restore strategy: nodeId=${node.id.value}"
        }
        require(!node.globalFallback || node.strategy != WjzFocusRestoreStrategy.Container) {
            "WjzFocus globalFallback node cannot use Container restore strategy: nodeId=${node.id.value}"
        }
        val generation = nextGeneration()
        val previousEntry = entries[node.id]
        if (previousEntry != null) {
            wjzFocusLogger.fWarn {
                "duplicate wjzFocus node registration: nodeId=${node.id.value}, " +
                        "newScope=${node.scopeId?.value}, newLayer=${node.layer}, " +
                        "previousScope=${previousEntry.node.scopeId?.value}, previousLayer=${previousEntry.node.layer}, " +
                        "previousGeneration=${previousEntry.generation}. " +
                        "The later registration will overwrite the former mounted entry."
            }
        }
        putEntry(WjzFocusEntry(
            node = node,
            generation = generation,
            mounted = true,
            placed = false,
            bounds = null,
            routingReady = false
        ))
        if (node.globalFallback) {
            registerGlobalFallbackNode(node.id, generation)
        }
        notifyMountChanged(node.id)
        consumePendingRequests(node)
        validateInternalState("register")
        return generation
    }

    /**
     * 注销节点。
     *
     * 只有 generation 匹配时才注销，避免旧 DisposableEffect 清掉新挂载的同名节点。
     */
    internal fun unregister(nodeId: WjzFocusNodeId, generation: Int) {
        checkMainThread()
        val entry = entries[nodeId] ?: return
        if (entry.generation != generation) return

        unregisterFocusPropertiesInvalidator(nodeId, generation)
        if (entry.node.globalFallback) {
            unregisterGlobalFallbackNode(nodeId, generation)
        }
        removeEntry(nodeId)
        if (entry.hasFocus) {
            clearFocusedSnapshotIfMatches(
                layer = entry.node.layer,
                scopeId = entry.node.scopeId,
                nodeId = nodeId
            )
            clearFocusedLeafSnapshotIfMatches(
                layer = entry.node.layer,
                scopeId = entry.node.scopeId,
                nodeId = nodeId
            )
        }
        notifyMountChanged(nodeId)
        val changed = pendingRequests.removeAll { it.nodeId == nodeId }
        if (changed) notifyPendingChanged()
        validateInternalState("unregister")
    }

    /** 注册一个组件入口 Host。 */
    internal fun registerEntriesHost(host: WjzFocusEntriesHostState) {
        checkMainThread()
        entriesHosts[host.componentId] = host
    }

    /** 注销一个组件入口 Host。 */
    internal fun unregisterEntriesHost(host: WjzFocusEntriesHostState) {
        checkMainThread()
        if (entriesHosts[host.componentId] === host) {
            entriesHosts.remove(host.componentId)
        }
    }

    /** 更新 Host 级方向出口，供锁释放后的最后方向意图复用。 */
    internal fun updateHostExits(
        token: Any,
        scopeId: WjzFocusScopeId,
        exits: List<WjzFocusHostExit>
    ) {
        checkMainThread()
        if (hostExits[token]?.scopeId == scopeId && hostExits[token]?.exits == exits) return
        hostExits[token] = WjzFocusHostExitRecord(
            scopeId = scopeId,
            exits = exits
        )
    }

    /** 注销 Host 级方向出口。 */
    internal fun unregisterHostExits(token: Any) {
        checkMainThread()
        hostExits.remove(token)
    }

    /** 更新节点当前是否持有焦点。 */
    fun updateFocus(nodeId: WjzFocusNodeId, hasFocus: Boolean) {
        checkMainThread()
        val entry = entries[nodeId] ?: return
        if (entry.hasFocus == hasFocus) return

        // Compose FocusState 是真实来源；这里仅缓存派生状态，供 WjzFocus 协议恢复和观察方重新检查。
        val updatedEntry = entry.copy(hasFocus = hasFocus)
        putEntry(updatedEntry)
        if (hasFocus) {
            updateFocusedSnapshot(
                layer = updatedEntry.node.layer,
                scopeId = updatedEntry.node.scopeId,
                nodeId = updatedEntry.node.id
            )
            if (updatedEntry.node.kind == WjzFocusNodeKind.Leaf) {
                lastFocusedScopeByLayer[updatedEntry.node.layer] = updatedEntry.node.scopeId
                updateFocusedLeafSnapshot(
                    layer = updatedEntry.node.layer,
                    scopeId = updatedEntry.node.scopeId,
                    nodeId = updatedEntry.node.id
                )
                recordRecentFocus(
                    layer = updatedEntry.node.layer,
                    nodeId = updatedEntry.node.id,
                    scopeId = updatedEntry.node.scopeId
                )
            }
        } else {
            clearFocusedSnapshotIfMatches(
                layer = updatedEntry.node.layer,
                scopeId = updatedEntry.node.scopeId,
                nodeId = updatedEntry.node.id
            )
            clearFocusedLeafSnapshotIfMatches(
                layer = updatedEntry.node.layer,
                scopeId = updatedEntry.node.scopeId,
                nodeId = updatedEntry.node.id
            )
        }
        validateInternalState("updateFocus")
    }

    /** 只更新已注册节点的 requester，不改变 generation。 */
    internal fun updateRequester(
        nodeId: WjzFocusNodeId,
        generation: Int,
        requester: FocusRequester
    ) {
        checkMainThread()
        val entry = entries[nodeId] ?: return
        if (entry.generation != generation) return
        putEntry(entry.copy(
            node = entry.node.copy(requester = requester)
        ))
    }

    internal fun updateDisabledFocusContext(
        nodeId: WjzFocusNodeId,
        generation: Int,
        disabledFocusContext: WjzDisabledFocusContext
    ) {
        checkMainThread()
        val entry = entries[nodeId] ?: return
        if (entry.generation != generation || entry.disabledFocusContext == disabledFocusContext) return

        val wasDisabled = isDisabledEntry(entry)
        val updatedEntry = entry.copy(disabledFocusContext = disabledFocusContext)
        putEntry(updatedEntry)
        val isDisabled = isDisabledEntry(updatedEntry)
        if (wasDisabled == isDisabled) return

        disabledFocusVersion += 1
        focusPropertiesInvalidators[nodeId]?.invalidator?.invoke()
        if (isDisabled) {
            val handledDisabledFocus = restoreDisabledFocusedEntries()
            if (!handledDisabledFocus && !isFocusLocked) {
                consumePendingRequests()
            }
        } else if (!isFocusLocked) {
            consumePendingRequests()
        }
    }

    /**
     * 更新节点方向路由信息，不改变节点 generation。
     *
     * 这一步只能由底层方向属性节点的安装回调调用。
     * 因为只有安装回调运行时，节点方向处理入口才算真正安装完成。
     * 其中 router handler 只是一个现场入口：真正消费时仍会沿祖先 modifier node 现场遍历确认，
     * 不缓存 router 拓扑。锁释放后的方向意图只消费 routingReady=true 的快照，
     * 避免在方向入口尚未完成安装时使用注册阶段的半成品 routing。
     */
    internal fun updateFocusRouting(
        nodeId: WjzFocusNodeId,
        generation: Int,
        directionHandlers: List<WjzFocusDirectionHandler>,
        exits: List<WjzFocusNodeExit>
    ) {
        checkMainThread()
        val entry = entries[nodeId] ?: return
        if (entry.generation != generation) return

        val node = entry.node
        if (entry.routingReady && node.directionHandlers == directionHandlers && node.exits == exits) return
        putEntry(entry.copy(
            node = node.copy(
                directionHandlers = directionHandlers,
                exits = exits
            ),
            routingReady = true
        ))
    }

    /** 标记节点方向入口已失效，等待 applyFocusProperties 重新安装新的 routing 入口。 */
    internal fun invalidateFocusRouting(
        nodeId: WjzFocusNodeId,
        generation: Int
    ) {
        checkMainThread()
        val entry = entries[nodeId] ?: return
        if (entry.generation != generation || !entry.routingReady) return
        putEntry(entry.copy(routingReady = false))
    }

    /** 注册节点级焦点属性失效回调，用于 disabled-region 变化后主动触发重算。 */
    internal fun registerFocusPropertiesInvalidator(
        nodeId: WjzFocusNodeId,
        generation: Int,
        invalidator: () -> Unit
    ) {
        checkMainThread()
        val entry = entries[nodeId] ?: return
        if (entry.generation != generation) return
        focusPropertiesInvalidators[nodeId] = WjzFocusPropertiesInvalidatorRecord(
            generation = generation,
            invalidator = invalidator
        )
    }

    /** 注销节点级焦点属性失效回调。 */
    internal fun unregisterFocusPropertiesInvalidator(
        nodeId: WjzFocusNodeId,
        generation: Int
    ) {
        checkMainThread()
        val record = focusPropertiesInvalidators[nodeId] ?: return
        if (record.generation == generation) {
            focusPropertiesInvalidators.remove(nodeId)
        }
    }

    /** 标记节点已完成布局，可安全参与 requestFocus。 */
    internal fun markPlaced(
        nodeId: WjzFocusNodeId,
        generation: Int,
        bounds: Rect? = null,
        disabledFocusContext: WjzDisabledFocusContext = WjzDisabledFocusContext()
    ) {
        checkMainThread()
        val entry = entries[nodeId] ?: return
        if (entry.generation != generation) return

        val placedEntry = entry.copy(
            placed = true,
            bounds = bounds ?: entry.bounds,
            disabledFocusContext = disabledFocusContext
        )
        if (placedEntry == entry) return
        putEntry(placedEntry)
        notifyMountChanged(nodeId)
        consumePendingRequests(placedEntry.node)
        retryPendingRestoreLayer(placedEntry.node)
    }

    /** 注册或更新 WjzFocus 禁用区域。禁用区域是阻断区域，只要和节点 bounds 相交就不可请求。 */
    internal fun registerDisabledFocusRegion(
        token: Any,
        bounds: Rect,
        disabledFocusContext: WjzDisabledFocusContext = WjzDisabledFocusContext()
    ) {
        checkMainThread()
        val record = WjzDisabledFocusRegionRecord(
            bounds = bounds,
            group = disabledFocusContext.group,
            zIndex = disabledFocusContext.zIndex
        )
        val previous = disabledFocusRegions.put(token, record)
        if (previous != record) {
            disabledFocusVersion += 1
            invalidateRegisteredFocusProperties()
            val handledDisabledFocus = restoreDisabledFocusedEntries()
            if (!handledDisabledFocus && !isFocusLocked) {
                consumePendingRequests()
            }
        }
    }

    /** 注销 WjzFocus 禁用区域，区域恢复可用后尝试消费仍有效的 pending 请求。 */
    internal fun unregisterDisabledFocusRegion(
        token: Any,
        consumePendingRequests: Boolean = true
    ) {
        checkMainThread()
        val removed = disabledFocusRegions.remove(token) != null
        if (removed) {
            disabledFocusVersion += 1
            invalidateRegisteredFocusProperties()
            if (consumePendingRequests && !isFocusLocked) {
                consumePendingRequests()
            }
        }
    }

    /** 查询节点当前是否持有焦点。该值是由 Compose FocusState 派生的只读缓存，不是业务事实来源。 */
    fun hasFocus(nodeId: WjzFocusNodeId): Boolean {
        val entry = entries[nodeId] ?: return false
        return entry.mounted && entry.hasFocus
    }

    /** 查询指定 layer/scope 当前持有焦点的节点。scopeId 使用严格匹配，避免跨模块滚动。 */
    fun focusedNodeId(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?
    ): WjzFocusNodeId? {
        checkMainThread()
        return focusedSnapshot(layer, scopeId).nodeId
    }

    /** 查询指定 layer/scope 的严格焦点快照。scopeId 严格匹配，不做 null 通配。 */
    internal fun focusedSnapshot(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?
    ): WjzFocusedNodeSnapshot {
        checkMainThread()
        return focusedSnapshotByLayerScope[WjzFocusLayerScopeKey(layer, scopeId)]
            ?: WjzFocusedNodeSnapshot(nodeId = null, version = 0)
    }

    /** 查询指定 layer/scope 当前持有焦点的真实 leaf 节点。scopeId 使用严格匹配。 */
    fun focusedLeafNodeId(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?
    ): WjzFocusNodeId? {
        checkMainThread()
        return focusedLeafSnapshot(layer, scopeId).nodeId
    }

    /** 查询指定 layer/scope 的真实 leaf 焦点快照。scopeId 严格匹配，不做 null 通配。 */
    internal fun focusedLeafSnapshot(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?
    ): WjzFocusedNodeSnapshot {
        checkMainThread()
        return focusedLeafSnapshotByLayerScope[WjzFocusLayerScopeKey(layer, scopeId)]
            ?: WjzFocusedNodeSnapshot(nodeId = null, version = 0)
    }

    fun debugSnapshot(): WjzFocusDebugSnapshot {
        checkMainThread()
        return WjzFocusDebugSnapshot(
            activeLayer = activeLayer,
            registeredNodes = entries.values.map { entry ->
                WjzFocusDebugNode(
                    nodeId = entry.node.id.value,
                    layer = entry.node.layer,
                    scopeId = entry.node.scopeId?.value,
                    kind = entry.node.kind.name,
                    mounted = entry.mounted,
                    placed = entry.placed,
                    hasFocus = entry.hasFocus,
                    routingReady = entry.routingReady,
                    generation = entry.generation
                )
            },
            focusedByLayerScope = focusedSnapshotByLayerScope.map { (key, snapshot) ->
                WjzFocusDebugLayerScopeNode(
                    layer = key.layer,
                    scopeId = key.scopeId?.value,
                    nodeId = snapshot.nodeId?.value,
                    version = snapshot.version
                )
            },
            focusedLeafByLayerScope = focusedLeafSnapshotByLayerScope.map { (key, snapshot) ->
                WjzFocusDebugLayerScopeNode(
                    layer = key.layer,
                    scopeId = key.scopeId?.value,
                    nodeId = snapshot.nodeId?.value,
                    version = snapshot.version
                )
            },
            recentFocus = recentFocusByLayer.map { (layer, records) ->
                WjzFocusDebugRecentLayer(
                    layer = layer,
                    nodes = records.map { record ->
                        WjzFocusDebugRecentNode(
                            nodeId = record.nodeId.value,
                            scopeId = record.scopeId?.value
                        )
                    }
                )
            },
            sourceStack = sourceStack.map { source ->
                WjzFocusDebugSource(
                    layer = source.layer,
                    scopeId = source.scopeId?.value,
                    nodeId = source.nodeId?.value,
                    token = source.token.value
                )
            },
            pendingRequests = pendingRequests.map { request ->
                WjzFocusDebugPendingRequest(
                    intent = request.intent.toString(),
                    layer = request.layer,
                    scopeId = request.scopeId?.value,
                    nodeId = request.nodeId?.value,
                    deadlineUptimeMillis = request.deadlineUptimeMillis,
                    submitDedupeKey = request.submitDedupeKey?.toString()
                )
            },
            lockCount = lockTokens.size,
            lockedDirection = lockedDirectionIntent?.let { intent ->
                WjzFocusDebugLockedDirection(
                    direction = intent.direction.toString(),
                    layer = intent.layer,
                    deadlineUptimeMillis = intent.deadlineUptimeMillis
                )
            },
            disabledRegionCount = disabledFocusRegions.size
        )
    }

    /** 保存 Activity 重建后可复用的轻量焦点历史，不包含 requester、generation、bounds 等运行时状态。 */
    fun saveState(): WjzFocusSavedState {
        checkMainThread()
        return WjzFocusSavedState(
            activeLayer = activeLayer,
            recentFocus = recentFocusByLayer.map { (layer, records) ->
                WjzFocusSavedRecentLayer(
                    layer = layer,
                    nodes = records.map { record ->
                        WjzFocusSavedRecentNode(
                            nodeId = record.nodeId.value,
                            scopeId = record.scopeId?.value
                        )
                    }
                )
            },
            sourceStack = sourceStack.map { source ->
                WjzFocusSavedSource(
                    layer = source.layer,
                    scopeId = source.scopeId?.value,
                    nodeId = source.nodeId?.value,
                    token = source.token.value
                )
            },
            lastFocusedScopes = lastFocusedScopeByLayer.map { (layer, scopeId) ->
                WjzFocusSavedLayerScope(
                    layer = layer,
                    scopeId = scopeId?.value
                )
            }
        )
    }

    /**
     * 导入 Activity 重建前保存的轻量焦点历史。
     *
     * 这里不会立即请求焦点，也不会恢复 requester、generation、bounds、pending、锁等运行时状态；
     * 后续 Host resume 或 pending 消费会基于当前已挂载节点重新校验。
     */
    fun restoreState(savedState: WjzFocusSavedState) {
        restoreStateInternal(
            savedState = savedState,
            restoredActiveLayer = savedState.activeLayer,
            validationSource = "restoreState"
        )
    }

    /**
     * Root coordinator 重建专用恢复入口。
     *
     * Root 不应以 Dialog/Keyboard/Overlay 这类临时 layer 作为初始 active layer 或 source 来源，否则临时窗口
     * 缺席时无 token source restore 可能把 root 弹回不存在的临时层。这里保留稳定来源栈和最近焦点历史。
     */
    fun restoreRootState(savedState: WjzFocusSavedState) {
        restoreStateInternal(
            savedState = savedState,
            restoredActiveLayer = savedState.rootRestoredActiveLayer(),
            restoredSourceStack = savedState.rootRestoredSourceStack(),
            validationSource = "restoreRootState"
        )
    }

    private fun restoreStateInternal(
        savedState: WjzFocusSavedState,
        restoredActiveLayer: WjzFocusLayer,
        restoredSourceStack: List<WjzFocusSavedSource> = savedState.sourceStack,
        validationSource: String
    ) {
        checkMainThread()
        activeLayer = restoredActiveLayer
        strictRestoreWindow = null
        initialResumeRestoredLayers.remove(restoredActiveLayer)

        recentFocusByLayer.clear()
        savedState.recentFocus.forEach { savedLayer ->
            val records = ArrayDeque<RecentFocusRecord>()
            savedLayer.nodes
                .takeLast(MaxRecentFocusRecords)
                .forEach { savedNode ->
                    records.removeAll { it.nodeId.value == savedNode.nodeId }
                    records.addLast(
                        RecentFocusRecord(
                            nodeId = WjzFocusNodeId(savedNode.nodeId),
                            scopeId = savedNode.scopeId?.let(::WjzFocusScopeId)
                        )
                    )
                }
            if (records.isNotEmpty()) {
                recentFocusByLayer[savedLayer.layer] = records
            }
        }

        sourceStack.clear()
        sourceStack.addAll(
            restoredSourceStack.map { savedSource ->
                WjzFocusSource(
                    layer = savedSource.layer,
                    scopeId = savedSource.scopeId?.let(::WjzFocusScopeId),
                    nodeId = savedSource.nodeId?.let(::WjzFocusNodeId),
                    token = WjzFocusSourceToken(savedSource.token)
                )
            }
        )
        sourceTokenSeed = maxOf(
            sourceTokenSeed,
            savedState.sourceStack.maxOfOrNull { it.token } ?: 0L
        )

        lastFocusedScopeByLayer.clear()
        savedState.lastFocusedScopes.forEach { savedScope ->
            lastFocusedScopeByLayer[savedScope.layer] = savedScope.scopeId?.let(::WjzFocusScopeId)
        }

        val changed = pendingRequests.removeAll { it.layer != activeLayer }
        if (changed) notifyPendingChanged()
        validateInternalState(validationSource)
    }

    /** 查询节点最近一次布局写入的 root 坐标 bounds；节点未挂载或尚未布局时返回 null。 */
    fun nodeBounds(nodeId: WjzFocusNodeId): Rect? {
        checkMainThread()
        return entries[nodeId]?.bounds
    }

    /** 测试和内部恢复使用的挂载状态查询。不要把它作为业务 API 使用。 */
    internal fun isMounted(nodeId: WjzFocusNodeId): Boolean {
        return entries[nodeId]?.mounted == true
    }

    /** 按 layer 、scope 、generation 精确判断节点是否仍是当前可请求目标。 */
    private fun isRequestable(
        nodeId: WjzFocusNodeId,
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?,
        expectedGeneration: Int? = null
    ): Boolean {
        val entry = entries[nodeId] ?: return false
        if (expectedGeneration != null && entry.generation != expectedGeneration) return false
        return entry.isRequestableFor(layer, scopeId)
    }

    /** 取得目标节点当前 generation，目标不在指定 layer 、scope 内时返回 null。 */
    private fun generationOf(
        nodeId: WjzFocusNodeId,
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?
    ): Int? {
        val entry = entries[nodeId] ?: return null
        return entry.generation.takeIf { entry.isRequestableFor(layer, scopeId) }
    }

    /** 当前 active layer 是否就是指定 layer。 */
    fun isActiveLayer(layer: WjzFocusLayer): Boolean {
        checkMainThread()
        return activeLayer == layer
    }

    private fun hasRequestableFocusInLayer(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId? = null
    ): Boolean {
        return entriesInLayerScope(layer, scopeId, strictScope = false).any { entry ->
            entry.isSourceCandidateFor(layer, scopeId)
        }
    }

    /**
     * 等待指定节点完成注册和布局。
     *
     * 这是 restorer 的明确时序补偿入口，用 node 挂载/布局门铃代替裸等帧。
     * [expectedGeneration] 不为空时只接受同一组合生命周期的节点，避免旧 pending 恢复到新节点。
     */
    internal suspend fun awaitNodeReady(
        nodeId: WjzFocusNodeId,
        expectedGeneration: Int?,
        timeoutMillis: Long,
        layer: WjzFocusLayer? = null,
        scopeId: WjzFocusScopeId? = null
    ): Boolean {
        checkMainThread()

        fun ready(): Boolean {
            val entry = entries[nodeId] ?: return false
            if (expectedGeneration != null && entry.generation != expectedGeneration) return false
            return if (layer != null) {
                entry.isRequestableFor(layer, scopeId)
            } else {
                entry.mounted &&
                        entry.placed &&
                        (scopeId == null || entry.node.scopeId == scopeId) &&
                        !isDisabledEntry(entry)
            }
        }

        if (ready()) return true

        return withTimeoutOrNull(timeoutMillis) {
            nodeMountWakeupSignals
                .filter { mountedNodeId -> mountedNodeId == nodeId }
                .first { ready() }
            true
        } ?: false
    }

    /**
     * 入队前先拒绝当前状态下已经可判定无法被后续合理消费的请求。
     *
     * missing node 只知道目标 layer 和显式 generation；未布局 entry 还能校验实际 layer/scope/generation。
     * 通过这些前置判断，Enqueued 只表示请求仍有等待挂载、布局或解锁后被消费的可能。
     */
    private fun invalidPendingRequestReason(
        entry: WjzFocusEntry?,
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?,
        expectedGeneration: Int?
    ): String? {
        if (layer != activeLayer) return "inactive-layer"
        if (entry == null) {
            return if (expectedGeneration != null) "generation-mismatch" else null
        }
        if (expectedGeneration != null && entry.generation != expectedGeneration) return "generation-mismatch"
        if (entry.node.layer != layer) return "layer-mismatch"
        if (scopeId != null && entry.node.scopeId != scopeId) return "scope-mismatch"
        return null
    }

    /**
     * 激活指定 layer。
     *
     * [recordSource] 为 true 时会记录当前焦点来源，后续可通过 [restoreSourceLayer] 回到来源 layer 、scope 、node。
     * Dialog/Popup 打开时通常需要记录来源。
     *
     * @param layer 要切换到的目标焦点层。
     * @param recordSource 是否在切层前记录当前来源，供后续恢复使用。
     */
    fun activateLayer(
        layer: WjzFocusLayer,
        recordSource: Boolean = false
    ): WjzFocusSourceToken? {
        try {
            checkMainThread()
            val token = if (recordSource) {
                nextSourceToken().also { sourceStack.add(currentFocusSource(it)) }
            } else {
                null
            }
            if (activeLayer != layer) {
                applyActiveLayer(layer)
            }
            WjzFocusLogger.info {
                "source capture result: recordSource=$recordSource, token=${token?.value}, " +
                        "targetLayer=$layer, activeLayer=$activeLayer, sourceStack=${sourceStack.size}"
            }
            return token
        } finally {
            validateInternalState("activateLayer")
        }
    }

    /** 切换 active layer，不记录来源。普通区域切层可用，不会生成可恢复的来源 token。 */
    fun switchLayer(layer: WjzFocusLayer) {
        checkMainThread()
        if (activeLayer == layer) return

        applyActiveLayer(layer)
    }

    /** 应用 active layer，并丢弃其他 layer 的 pending 请求，避免旧区域请求串入新区域。 */
    private fun applyActiveLayer(layer: WjzFocusLayer) {
        checkMainThread()
        activeLayer = layer
        if (strictRestoreWindow?.layer != layer) {
            strictRestoreWindow = null
        }
        initialResumeRestoredLayers.remove(layer)
        val changed = pendingRequests.removeAll { it.layer != layer }
        if (changed) notifyPendingChanged()
        wjzFocusLogger.fDebug { "activate layer: $layer" }
    }

    /**
     * 恢复最近记录的来源 layer。
     *
     * 优先恢复来源 node，来源 node 不存在时恢复来源 scope 的 active layer 入口。
     * 这通常用于 Dialog/Overlay 关闭后回到打开它之前的主界面来源。
     *
     * @param scopeId 当来源自身没有 scope 时，用它作为恢复 layer 的兜底 scope。
     * @param expectedActiveLayer 不为 null 时，只有当前 active layer 匹配才允许恢复。
     * @param token 指定要恢复的来源 token，为 null 时恢复最近一次记录的来源。
     */
    fun restoreSourceLayer(
        scopeId: WjzFocusScopeId? = null,
        expectedActiveLayer: WjzFocusLayer? = null,
        token: WjzFocusSourceToken? = null
    ): Boolean {
        try {
            checkMainThread()
            if (expectedActiveLayer != null && activeLayer != expectedActiveLayer) {
                WjzFocusLogger.info {
                    "restore source skipped: reason=unexpected-active-layer, " +
                            "expected=$expectedActiveLayer, activeLayer=$activeLayer, token=${token?.value}"
                }
                return false
            }

            val source = popSource(token)
            if (source == null) {
                WjzFocusLogger.info {
                    "restore source skipped: reason=missing-source, token=${token?.value}, " +
                            "activeLayer=$activeLayer, sourceStack=${sourceStack.size}"
                }
                return false
            }
            applyActiveLayer(source.layer)
            val restoreScopeId = source.scopeId ?: scopeId
            val restoredSourceNode = source.nodeId?.let { nodeId ->
                val entry = entries[nodeId] ?: return@let false
                val leaf = entry.asLeafEntryFor(source.layer, source.scopeId)
                if (leaf == null) {
                    wjzFocusLogger.fInfo {
                        "restore source node skipped: nodeId=${nodeId.value}, strategy=${entry.node.strategy}"
                    }
                    return@let false
                }
                requestFocusDetailed(
                    nodeId = leaf.node.id,
                    layer = source.layer,
                    scopeId = source.scopeId,
                    enqueueIfMissing = false,
                    expectedGeneration = leaf.generation
                ) == WjzFocusRequestResult.Focused
            } == true
            val restored = restoredSourceNode || restoreActiveLayer(
                scopeId = restoreScopeId,
                enqueueOnMiss = true
            )
            WjzFocusLogger.info {
                "restore source result: restored=$restored, restoredSourceNode=$restoredSourceNode, " +
                        "sourceLayer=${source.layer}, sourceScope=${source.scopeId?.value}, " +
                        "sourceNode=${source.nodeId?.value}, restoreScope=${restoreScopeId?.value}, " +
                        "token=${source.token.value}"
            }
            return restored
        } finally {
            validateInternalState("restoreSourceLayer")
        }
    }

    /**
     * 恢复当前 active layer 在指定 scope 内的入口。
     *
     * scope 为 null 时表示 scope 通配，不限定模块边界，可能落到该焦点层的全局 fallback。
     * 新代码能明确目标 scope 时，应优先传明确 scope。
     *
     * @param scopeId 希望恢复到的 scope，为 null 时按当前焦点层的可用入口或全局 fallback 恢复。
     */
    fun restoreActiveLayer(
        scopeId: WjzFocusScopeId? = null,
        enqueueOnMiss: Boolean = false
    ): Boolean {
        try {
            checkMainThread()
            val strictWindow = currentStrictRestoreWindow()
            if (strictWindow != null) {
                when (consumeStrictRestoreWindow()) {
                    WjzFocusStrictRestoreWindowResult.Restored -> {
                        return strictWindow.layer == activeLayer && strictWindow.scopeId == scopeId
                    }
                    WjzFocusStrictRestoreWindowResult.Blocked -> return false
                    WjzFocusStrictRestoreWindowResult.Inactive -> Unit
                }
            }
            if (isFocusLocked) {
                if (enqueueOnMiss) enqueueRestoreLayer(activeLayer, scopeId, ResumeRestoreTtlMillis)
                return false
            }

            dropExpiredPendingRequests()
            val entry = enterLeafEntry(
                layer = activeLayer,
                scopeId = scopeId
            )
            if (entry == null) {
                val recentNode = findRecentFocusNode(
                    layer = activeLayer,
                    scopeId = scopeId,
                    allowAnyScope = scopeId == null
                )
                val restoredRecent = recentNode?.let { leaf ->
                    requestFocusDetailed(
                        nodeId = leaf.node.id,
                        layer = activeLayer,
                        scopeId = leaf.node.scopeId,
                        enqueueIfMissing = false,
                        expectedGeneration = leaf.generation
                    ) == WjzFocusRequestResult.Focused
                } == true
                val restored = restoredRecent || restoreGlobalFallback(scopeId)
                if (!restored && enqueueOnMiss) {
                    enqueueRestoreLayer(activeLayer, scopeId, ResumeRestoreTtlMillis)
                }
                return restored
            }

            val restored = requestFocusDetailed(
                nodeId = entry.node.id,
                layer = activeLayer,
                scopeId = scopeId,
                enqueueIfMissing = false,
                expectedGeneration = entry.generation
            ) == WjzFocusRequestResult.Focused
            if (restored) return true

            val restoredFallback = restoreGlobalFallback(scopeId)
            if (!restoredFallback && enqueueOnMiss) {
                enqueueRestoreLayer(activeLayer, scopeId, ResumeRestoreTtlMillis)
            }
            return restoredFallback
        } finally {
            validateInternalState("restoreActiveLayer")
        }
    }

    /**
     * 恢复当前 active layer 在指定 scope 内除 [excludedNodeId] 之外的入口。
     *
     * 这个入口用于 TextField Back/Escape：输入框自身已经持有焦点时，普通 [restoreActiveLayer]
     * 会优先命中当前焦点节点，因此这里严格限制在当前 active layer + 当前 scope 内寻找非当前节点。
     * 找不到可恢复目标时只返回 false，不清除焦点，也不入队普通 RestoreLayer，避免稍后又恢复到被排除节点。
     */
    internal fun restoreActiveLayerExcludingNode(
        excludedNodeId: WjzFocusNodeId,
        scopeId: WjzFocusScopeId? = null
    ): Boolean {
        checkMainThread()
        if (currentStrictRestoreWindow() != null) return false
        if (isFocusLocked) return false

        dropExpiredPendingRequests()
        val entry = enterLeafEntryStrictExcluding(
            layer = activeLayer,
            scopeId = scopeId,
            excludedNodeId = excludedNodeId
        ) ?: return false

        return requestFocusDetailedInternal(
            nodeId = entry.node.id,
            layer = activeLayer,
            scopeId = scopeId,
            enqueueIfMissing = false,
            expectedGeneration = entry.generation,
            allowFailureDegrade = false,
            bypassStrictRestoreWindow = false,
            submitDedupeKey = null
        ) == WjzFocusRequestResult.Focused
    }

    /**
     * Low-level execution API.
     * UI/reusable components should use [submitNodeFocusIntent] or [submitEntryFocusIntent].
     * Router/exit/restorer/coordinator internals may call this directly.
     */
    internal fun requestFocusDetailed(
        nodeId: WjzFocusNodeId,
        layer: WjzFocusLayer = activeLayer,
        scopeId: WjzFocusScopeId? = null,
        enqueueIfMissing: Boolean = true,
        expectedGeneration: Int? = null,
        requestSource: WjzFocusRequestSource = WjzFocusRequestSource.Direct
    ): WjzFocusRequestResult {
        return requestFocusDetailedInternal(
            nodeId = nodeId,
            layer = layer,
            scopeId = scopeId,
            enqueueIfMissing = enqueueIfMissing,
            expectedGeneration = expectedGeneration,
            allowFailureDegrade = true,
            bypassStrictRestoreWindow = false,
            requestSource = requestSource,
            submitDedupeKey = null
        )
    }

    fun submitNodeFocusIntent(
        nodeId: WjzFocusNodeId,
        layer: WjzFocusLayer = activeLayer,
        scopeId: WjzFocusScopeId? = null,
        intent: WjzFocusSubmitIntent,
        expectedGeneration: Int? = null,
        requestSource: WjzFocusRequestSource? = null
    ): WjzFocusRequestResult {
        checkMainThread()
        val resolvedRequestSource = requestSource ?: WjzFocusRequestSource.Node(intent)
        logSubmitFocusIntent(
            intent = intent,
            nodeId = nodeId,
            layer = layer,
            scopeId = scopeId,
            expectedGeneration = expectedGeneration
        )
        return when (intent) {
            is WjzFocusSubmitIntent.InitialEntry -> {
                when {
                    layer != activeLayer -> dropFocusIntent(
                        intent = intent,
                        reason = "initial-entry-inactive-layer",
                        nodeId = nodeId,
                        layer = layer,
                        scopeId = scopeId,
                        expectedGeneration = expectedGeneration
                    )
                    isFocusLocked -> dropFocusIntent(
                        intent = intent,
                        reason = "initial-entry-focus-locked",
                        nodeId = nodeId,
                        layer = layer,
                        scopeId = scopeId,
                        expectedGeneration = expectedGeneration
                    )
                    hasRequestableFocusInLayer(layer) -> dropFocusIntent(
                        intent = intent,
                        reason = "initial-entry-layer-already-focused",
                        nodeId = nodeId,
                        layer = layer,
                        scopeId = scopeId,
                        expectedGeneration = expectedGeneration
                    )
                    else -> requestFocusDetailedInternal(
                        nodeId = nodeId,
                        layer = layer,
                        scopeId = scopeId,
                        enqueueIfMissing = true,
                        expectedGeneration = expectedGeneration,
                        allowFailureDegrade = true,
                        bypassStrictRestoreWindow = false,
                        requestSource = resolvedRequestSource,
                        submitDedupeKey = intent.dedupeKey
                    ).dropInactiveLayerFailure(
                        intent = intent,
                        nodeId = nodeId,
                        layer = layer,
                        scopeId = scopeId,
                        expectedGeneration = expectedGeneration
                    )
                }
            }
            is WjzFocusSubmitIntent.ExternalEntry -> {
                if (intent.activateLayer) {
                    applyActiveLayer(layer)
                }
                if (layer != activeLayer) {
                    if (intent.enqueueUntilLayerActive) {
                        enqueueSubmitNodeRequest(
                            nodeId = nodeId,
                            layer = layer,
                            scopeId = scopeId,
                            expectedGeneration = expectedGeneration,
                            submitDedupeKey = intent.dedupeKey
                        )
                        WjzFocusRequestResult.Enqueued
                    } else {
                        dropFocusIntent(
                            intent = intent,
                            reason = "external-entry-inactive-layer",
                            nodeId = nodeId,
                            layer = layer,
                            scopeId = scopeId,
                            expectedGeneration = expectedGeneration
                        )
                    }
                } else {
                    requestFocusDetailedInternal(
                        nodeId = nodeId,
                        layer = layer,
                        scopeId = scopeId,
                        enqueueIfMissing = intent.enqueueIfMissing,
                        expectedGeneration = expectedGeneration,
                        allowFailureDegrade = true,
                        bypassStrictRestoreWindow = false,
                        requestSource = resolvedRequestSource,
                        submitDedupeKey = intent.dedupeKey
                    )
                }
            }
            is WjzFocusSubmitIntent.LayerEntry -> {
                if (layer != activeLayer) {
                    dropFocusIntent(
                        intent = intent,
                        reason = "layer-entry-inactive-layer",
                        nodeId = nodeId,
                        layer = layer,
                        scopeId = scopeId,
                        expectedGeneration = expectedGeneration
                    )
                } else {
                    requestFocusDetailedInternal(
                        nodeId = nodeId,
                        layer = layer,
                        scopeId = scopeId,
                        enqueueIfMissing = true,
                        expectedGeneration = expectedGeneration,
                        allowFailureDegrade = true,
                        bypassStrictRestoreWindow = false,
                        requestSource = resolvedRequestSource,
                        submitDedupeKey = intent.dedupeKey
                    )
                }
            }
            is WjzFocusSubmitIntent.ContentFallback -> {
                when {
                    layer != activeLayer -> dropFocusIntent(
                        intent = intent,
                        reason = "content-fallback-inactive-layer",
                        nodeId = nodeId,
                        layer = layer,
                        scopeId = scopeId,
                        expectedGeneration = expectedGeneration
                    )
                    hasRequestableFocusInLayer(layer, scopeId) -> dropFocusIntent(
                        intent = intent,
                        reason = "content-fallback-scope-already-focused",
                        nodeId = nodeId,
                        layer = layer,
                        scopeId = scopeId,
                        expectedGeneration = expectedGeneration
                    )
                    else -> {
                        val requestResult = requestFocusDetailedInternal(
                            nodeId = nodeId,
                            layer = layer,
                            scopeId = scopeId,
                            enqueueIfMissing = true,
                            expectedGeneration = expectedGeneration,
                            allowFailureDegrade = true,
                            bypassStrictRestoreWindow = false,
                            requestSource = WjzFocusRequestSource.Fallback,
                            submitDedupeKey = intent.dedupeKey
                        )
                        when (requestResult) {
                            WjzFocusRequestResult.Focused,
                            WjzFocusRequestResult.Enqueued -> requestResult
                            WjzFocusRequestResult.Dropped -> WjzFocusRequestResult.Dropped
                            WjzFocusRequestResult.Failed -> {
                                if (restoreActiveLayer(scopeId = scopeId, enqueueOnMiss = true)) {
                                    WjzFocusRequestResult.Focused
                                } else if (hasPendingRestoreLayer(layer = activeLayer, scopeId = scopeId)) {
                                    WjzFocusRequestResult.Enqueued
                                } else {
                                    WjzFocusRequestResult.Failed
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestFocusDetailedInternal(
        nodeId: WjzFocusNodeId,
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?,
        enqueueIfMissing: Boolean,
        expectedGeneration: Int?,
        allowFailureDegrade: Boolean,
        bypassStrictRestoreWindow: Boolean,
        requestSource: WjzFocusRequestSource = WjzFocusRequestSource.Direct,
        submitDedupeKey: Any?
    ): WjzFocusRequestResult {
        checkMainThread()
        val entry = entries[nodeId]
        val strictWindow = currentStrictRestoreWindow()
        if (strictWindow != null && !bypassStrictRestoreWindow) {
            consumeStrictRestoreWindow()
            logFocusRequestSkipped(
                reason = "strict-restore-window",
                nodeId = nodeId,
                layer = layer,
                scopeId = scopeId,
                expectedGeneration = expectedGeneration,
                entry = entry,
                result = WjzFocusRequestResult.Failed
            )
            return WjzFocusRequestResult.Failed
        }
        if (entry == null || !entry.mounted || !entry.placed) {
            val invalidPendingReason = invalidPendingRequestReason(
                entry = entry,
                layer = layer,
                scopeId = scopeId,
                expectedGeneration = expectedGeneration
            )
            if (invalidPendingReason != null) {
                logFocusRequestSkipped(
                    reason = invalidPendingReason,
                    nodeId = nodeId,
                    layer = layer,
                    scopeId = scopeId,
                    expectedGeneration = expectedGeneration,
                    entry = entry,
                    result = WjzFocusRequestResult.Failed
                )
                return WjzFocusRequestResult.Failed
            }

            val reason = when {
                entry == null -> "missing-node"
                !entry.mounted -> "unmounted-node"
                else -> "not-placed-node"
            }
            val result = if (enqueueIfMissing) {
                WjzFocusRequestResult.Enqueued
            } else {
                WjzFocusRequestResult.Failed
            }
            logFocusRequestSkipped(
                reason = reason,
                nodeId = nodeId,
                layer = layer,
                scopeId = scopeId,
                expectedGeneration = expectedGeneration,
                entry = entry,
                result = result
            )
            if (enqueueIfMissing) {
                enqueuePendingRequest(
                    intent = WjzFocusIntent.RequestNode(
                        nodeId = nodeId,
                        layer = layer,
                        scopeId = scopeId,
                        expectedGeneration = expectedGeneration ?: entry?.generation
                    ),
                    ttlMillis = DefaultPendingTtlMillis,
                    submitDedupeKey = submitDedupeKey
                )
            }
            return result
        }
        if (expectedGeneration != null && entry.generation != expectedGeneration) {
            logFocusRequestSkipped(
                reason = "generation-mismatch",
                nodeId = nodeId,
                layer = layer,
                scopeId = scopeId,
                expectedGeneration = expectedGeneration,
                entry = entry,
                result = WjzFocusRequestResult.Failed
            )
            return WjzFocusRequestResult.Failed
        }
        if (!entry.node.requestPolicy.allow(requestSource)) {
            logFocusRequestSkipped(
                reason = "request-policy-rejected",
                nodeId = nodeId,
                layer = layer,
                scopeId = scopeId,
                expectedGeneration = expectedGeneration,
                entry = entry,
                result = WjzFocusRequestResult.Dropped
            )
            return WjzFocusRequestResult.Dropped
        }
        if (layer != activeLayer || !entry.isRequestableFor(layer, scopeId)) {
            logFocusRequestSkipped(
                reason = when {
                    layer != activeLayer -> "inactive-layer"
                    isDisabledEntry(entry) -> "disabled-region"
                    else -> "scope-mismatch"
                },
                nodeId = nodeId,
                layer = layer,
                scopeId = scopeId,
                expectedGeneration = expectedGeneration,
                entry = entry,
                result = WjzFocusRequestResult.Failed
            )
            return WjzFocusRequestResult.Failed
        }
        if (isFocusLocked) {
            val result = if (enqueueIfMissing) {
                WjzFocusRequestResult.Enqueued
            } else {
                WjzFocusRequestResult.Failed
            }
            logFocusRequestSkipped(
                reason = "focus-locked",
                nodeId = nodeId,
                layer = layer,
                scopeId = scopeId,
                expectedGeneration = expectedGeneration,
                entry = entry,
                result = result
            )
            if (enqueueIfMissing) {
                enqueuePendingRequest(
                    intent = WjzFocusIntent.RequestNode(
                        nodeId = nodeId,
                        layer = layer,
                        scopeId = scopeId,
                        expectedGeneration = entry.generation
                    ),
                    ttlMillis = LockedPendingTtlMillis,
                    submitDedupeKey = submitDedupeKey
                )
            }
            return result
        }

        // WjzFocus 注册节点的原生 requestFocus 统一在这里执行。
        coordinatorFocusDepth += 1
        val focused = try {
            runCatching { entry.node.requester.requestFocus() }
                .onFailure { throwable ->
                    wjzFocusLogger.fWarn {
                        "focus requester threw: nodeId=${nodeId.value}, " +
                                "targetLayer=$layer, activeLayer=$activeLayer, " +
                                "targetScope=${scopeId?.value}, nodeScope=${entry.node.scopeId?.value}, " +
                                "generation=${entry.generation}, expectedGeneration=$expectedGeneration, " +
                                "pending=${pendingRequests.size}, locked=$isFocusLocked, " +
                                "error=${throwable.stackTraceToString()}"
                    }
                }
                .getOrDefault(false)
        } finally {
            coordinatorFocusDepth -= 1
        }
        wjzFocusLogger.fInfo {
            "requestFocus result: nodeId=${nodeId.value}, focused=$focused, " +
                    "layer=$layer, scopeId=${scopeId?.value}, " +
                    "requester=${entry.node.requester}"
        }
        WjzFocusLogger.info {
            "request result: nodeId=${nodeId.value}, " +
                    "result=${if (focused) WjzFocusRequestResult.Focused else WjzFocusRequestResult.Failed}, " +
                    "layer=$layer, scopeId=${scopeId?.value}, generation=${entry.generation}, " +
                    "expectedGeneration=$expectedGeneration, pending=${pendingRequests.size}, locked=$isFocusLocked"
        }
        updateFailure(
            nodeId = nodeId,
            success = focused,
            layer = layer,
            scopeId = scopeId,
            expectedGeneration = expectedGeneration,
            entry = entry,
            allowFailureDegrade = allowFailureDegrade
        )
        return if (focused) WjzFocusRequestResult.Focused else WjzFocusRequestResult.Failed
    }

    /**
     * Low-level execution API for legacy entry requests with detailed result.
     * UI/reusable components should use [submitNodeFocusIntent] or [submitEntryFocusIntent].
     * Router/exit/restorer/coordinator internals may call this directly.
     */
    internal fun requestEntryFocusDetailed(
        entryId: WjzFocusEntryId,
        enqueueIfMissing: Boolean = true
    ): WjzFocusRequestResult {
        checkMainThread()
        val host = entriesHosts[entryId.componentId] ?: return WjzFocusRequestResult.Failed
        val target = host.resolve(entryId).target ?: return WjzFocusRequestResult.Failed
        return requestFocusDetailed(
            nodeId = target.nodeId,
            layer = target.layer,
            scopeId = target.scopeId,
            enqueueIfMissing = enqueueIfMissing,
            requestSource = WjzFocusRequestSource.Entry(entryId)
        )
    }

    /** 提交一个公开 entry 焦点意图，由 coordinator 解析目标并统一仲裁。 */
    fun submitEntryFocusIntent(
        entryId: WjzFocusEntryId,
        intent: WjzFocusSubmitIntent
    ): WjzFocusRequestResult {
        checkMainThread()
        val host = entriesHosts[entryId.componentId] ?: return missingEntryFocusIntentResult(
            entryId = entryId,
            intent = intent,
            reason = "missing-entry-host"
        )
        val target = host.resolve(entryId).target ?: return missingEntryFocusIntentResult(
            entryId = entryId,
            intent = intent,
            reason = "missing-entry-target"
        )
        return submitNodeFocusIntent(
            nodeId = target.nodeId,
            layer = target.layer,
            scopeId = target.scopeId,
            intent = intent,
            requestSource = WjzFocusRequestSource.Entry(entryId, intent)
        )
    }

    private fun enqueueSubmitNodeRequest(
        nodeId: WjzFocusNodeId,
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?,
        expectedGeneration: Int?,
        submitDedupeKey: Any?
    ) {
        enqueuePendingRequest(
            intent = WjzFocusIntent.RequestNode(
                nodeId = nodeId,
                layer = layer,
                scopeId = scopeId,
                expectedGeneration = expectedGeneration
            ),
            ttlMillis = DefaultPendingTtlMillis,
            submitDedupeKey = submitDedupeKey
        )
    }

    private fun missingEntryFocusIntentResult(
        entryId: WjzFocusEntryId,
        intent: WjzFocusSubmitIntent,
        reason: String
    ): WjzFocusRequestResult {
        val result = when (intent) {
            is WjzFocusSubmitIntent.InitialEntry -> WjzFocusRequestResult.Dropped
            is WjzFocusSubmitIntent.ExternalEntry -> WjzFocusRequestResult.Failed
            is WjzFocusSubmitIntent.LayerEntry -> WjzFocusRequestResult.Dropped
            is WjzFocusSubmitIntent.ContentFallback -> WjzFocusRequestResult.Dropped
        }
        wjzFocusLogger.fInfo {
            "drop focus intent: reason=$reason, result=$result, intent=$intent, " +
                    "entryId=${entryId.value}, activeLayer=$activeLayer, pending=${pendingRequests.size}, " +
                    "locked=$isFocusLocked"
        }
        return result
    }

    private fun dropFocusIntent(
        intent: WjzFocusSubmitIntent,
        reason: String,
        nodeId: WjzFocusNodeId,
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?,
        expectedGeneration: Int?
    ): WjzFocusRequestResult {
        wjzFocusLogger.fInfo {
            "drop focus intent: reason=$reason, intent=$intent, nodeId=${nodeId.value}, " +
                    "targetLayer=$layer, activeLayer=$activeLayer, targetScope=${scopeId?.value}, " +
                    "expectedGeneration=$expectedGeneration, pending=${pendingRequests.size}, locked=$isFocusLocked"
        }
        return WjzFocusRequestResult.Dropped
    }

    private fun logSubmitFocusIntent(
        intent: WjzFocusSubmitIntent,
        nodeId: WjzFocusNodeId,
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?,
        expectedGeneration: Int?
    ) {
        wjzFocusLogger.fDebug {
            "submit focus intent: intent=$intent, nodeId=${nodeId.value}, targetLayer=$layer, " +
                    "activeLayer=$activeLayer, targetScope=${scopeId?.value}, " +
                    "expectedGeneration=$expectedGeneration, pending=${pendingRequests.size}, locked=$isFocusLocked"
        }
        WjzFocusLogger.verbose {
            "request source: intent=$intent, nodeId=${nodeId.value}, targetLayer=$layer, " +
                    "activeLayer=$activeLayer, targetScope=${scopeId?.value}, " +
                    "expectedGeneration=$expectedGeneration, pending=${pendingRequests.size}, locked=$isFocusLocked"
        }
    }

    private fun WjzFocusRequestResult.dropInactiveLayerFailure(
        intent: WjzFocusSubmitIntent,
        nodeId: WjzFocusNodeId,
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?,
        expectedGeneration: Int?
    ): WjzFocusRequestResult {
        if (this != WjzFocusRequestResult.Failed || layer == activeLayer) return this
        return dropFocusIntent(
            intent = intent,
            reason = "initial-entry-inactive-layer",
            nodeId = nodeId,
            layer = layer,
            scopeId = scopeId,
            expectedGeneration = expectedGeneration
        )
    }

    /** 入队一个恢复 layer 、scope 的 pending 请求，供稍后在目标 layer 可恢复时消费。 */
    internal fun enqueueRestoreLayer(
        layer: WjzFocusLayer = activeLayer,
        scopeId: WjzFocusScopeId? = null,
        ttlMillis: Long = DefaultPendingTtlMillis
    ) {
        checkMainThread()
        enqueuePendingRequest(
            intent = WjzFocusIntent.RestoreLayer(
                layer = layer,
                scopeId = scopeId
            ),
            ttlMillis = ttlMillis
        )
    }

    /** 入队 Host 入口恢复请求，并立即尝试用 WjzFocus 自己的恢复路径消费一次。 */
    internal fun enqueueAndTryRestoreLayer(
        layer: WjzFocusLayer = activeLayer,
        scopeId: WjzFocusScopeId? = null,
        ttlMillis: Long = ResumeRestoreTtlMillis
    ) {
        checkMainThread()
        enqueueRestoreLayer(layer, scopeId, ttlMillis)
        if (!isFocusLocked) {
            consumePendingRequests()
        }
    }

    /** 入队一个 disabled-region 专用的严格恢复请求，只允许回到指定 layer + scope。 */
    private fun enqueueRestoreDisabledScope(
        layer: WjzFocusLayer = activeLayer,
        scopeId: WjzFocusScopeId?,
        ttlMillis: Long = DefaultPendingTtlMillis
    ) {
        checkMainThread()
        enqueuePendingRequest(
            intent = WjzFocusIntent.RestoreDisabledScope(
                layer = layer,
                scopeId = scopeId
            ),
            ttlMillis = ttlMillis,
            prioritize = true
        )
    }

    /**
     * 入队一个普通焦点组恢复请求。
     *
     * 普通组恢复假设目标节点属于当前组合树中的非 lazy 区域，不需要滚动到业务 item。
     * 请求仍然进入 pending 队列，由匹配的 [wjzFocusGroupRestorerHost] 消费，避免调用方绕过 generation 、锁定和 scope 校验。
     */
    internal fun enqueueGroupRestore(
        nodeId: WjzFocusNodeId,
        layer: WjzFocusLayer = activeLayer,
        scopeId: WjzFocusScopeId? = null,
        restorerId: String,
        listId: String,
        fallbackNodeId: WjzFocusNodeId? = null,
        ttlMillis: Long = DefaultPendingTtlMillis
    ) {
        checkMainThread()
        enqueuePendingRequest(
            intent = WjzFocusIntent.RestoreGroup(
                nodeId = nodeId,
                layer = layer,
                scopeId = scopeId,
                restorerId = restorerId,
                listId = listId,
                fallbackNodeId = fallbackNodeId
            ),
            ttlMillis = ttlMillis
        )
    }

    /**
     * 入队一个 Lazy item 恢复请求。
     *
     * 目标 item 可能尚未可见或尚未重新挂载，因此请求由 [WjzLazyFocusRestorerHost] 在对应列表可处理时消费。
     *
     * 这条路径会先等待 item 可见，再校验目标 node 的 generation，避免滚动完成后命中旧组合生命周期里的失效节点。
     *
     * @param nodeId 最终要恢复焦点的节点。
     * @param itemKey Lazy 列表中的业务 item key。
     * @param layer 目标 layer。
     * @param scopeId 目标 scope。
     * @param restorerId 恢复器身份，用于区分不同恢复器。
     * @param listId 列表身份，用于区分不同 Lazy 列表。
     * @param ttlMillis 请求在 pending 队列中的存活时间。
     */
    internal fun enqueueLazyRestore(
        nodeId: WjzFocusNodeId,
        itemKey: WjzFocusItemKey,
        layer: WjzFocusLayer = activeLayer,
        scopeId: WjzFocusScopeId? = null,
        restorerId: String,
        listId: String,
        ttlMillis: Long = LazyPendingTtlMillis
    ) {
        checkMainThread()
        enqueuePendingRequest(
            intent = WjzFocusIntent.RestoreLazyItem(
                nodeId = nodeId,
                itemKey = itemKey,
                layer = layer,
                scopeId = scopeId,
                restorerId = restorerId,
                listId = listId
            ),
            ttlMillis = ttlMillis
        )
    }

    /** 注册普通焦点组恢复器。同一个 layer 、scope 、restorer 、list 只保留最新恢复器。 */
    internal fun registerGroupRestorer(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?,
        restorerId: String,
        listId: String,
        restorer: WjzFocusGroupRestorer
    ) {
        checkMainThread()
        groupRestorers[WjzFocusGroupRestorerKey(layer, scopeId, restorerId, listId)] = restorer
    }

    /** 注销普通焦点组恢复器。只有同一实例匹配时才移除，避免旧 effect 清掉新恢复器。 */
    internal fun unregisterGroupRestorer(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?,
        restorerId: String,
        listId: String,
        restorer: WjzFocusGroupRestorer
    ) {
        checkMainThread()
        val key = WjzFocusGroupRestorerKey(layer, scopeId, restorerId, listId)
        if (groupRestorers[key] == restorer) {
            groupRestorers.remove(key)
        }
    }

    /** 注册 Lazy 列表恢复器。同一个 layer 、scope 、restorer 、list 只保留最新恢复器。 */
    internal fun registerLazyRestorer(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?,
        restorerId: String,
        listId: String,
        restorer: WjzLazyFocusRestorer
    ) {
        checkMainThread()
        lazyRestorers[WjzFocusLazyRestorerKey(layer, scopeId, restorerId, listId)] = restorer
    }

    /** 注销 Lazy 列表恢复器。只有同一实例匹配时才移除，避免旧 effect 清掉新恢复器。 */
    internal fun unregisterLazyRestorer(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?,
        restorerId: String,
        listId: String,
        restorer: WjzLazyFocusRestorer
    ) {
        checkMainThread()
        val key = WjzFocusLazyRestorerKey(layer, scopeId, restorerId, listId)
        if (lazyRestorers[key] == restorer) {
            lazyRestorers.remove(key)
        }
    }

    /** 按 scope 、layer 、node 条件取消 pending 请求，参数为 null 表示不限制该条件。 */
    fun cancelPendingRequests(
        scopeId: WjzFocusScopeId? = null,
        layer: WjzFocusLayer? = null,
        nodeId: WjzFocusNodeId? = null
    ) {
        checkMainThread()
        val changed = pendingRequests.removeAll { request ->
            (scopeId == null || request.scopeId == scopeId) &&
                    (layer == null || request.layer == layer) &&
                    (nodeId == null || request.nodeId == nodeId)
        }
        if (changed) notifyPendingChanged()
    }

    /** 释放 coordinator 内部协程资源。只应由创建并持有它的 Composable Host 生命周期调用。 */
    internal fun dispose() {
        lockWatchdogJob?.cancel()
        watchdogScope.coroutineContext[Job]?.cancel()
    }

    /**
     * Host 在窗口 、生命周期恢复时的受控恢复入口。
     *
     * 多个 Host 共享同一 coordinator 时，只有当前 active layer 且属于最近焦点 scope或已有 pending 的 Host 才允许恢复，
     * 避免所有 Host 依次 fallback 抢焦点。
     */
    fun restoreHostOnResume(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId
    ): Boolean {
        checkMainThread()
        if (activeLayer != layer) return false
        if (!hasResumeRestoreClaim(layer, scopeId)) {
            if (initialResumeRestoredLayers.contains(layer)) return false
            initialResumeRestoredLayers.add(layer)
        }

        return restoreActiveLayer(scopeId, enqueueOnMiss = true)
    }

    /** 判断指定 Host 是否有资格在 resume 、window-focus 时恢复焦点。 */
    private fun hasResumeRestoreClaim(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId
    ): Boolean {
        if (lastFocusedScopeByLayer[layer] == scopeId) return true
        if (entries.values.any { it.isSourceCandidateFor(layer, scopeId) }) return true
        return pendingRequests.any { request ->
            request.layer == layer && request.scopeId == scopeId
        }
    }

    /** 记录当前全局 fallback 节点及其 generation。 */
    private fun registerGlobalFallbackNode(nodeId: WjzFocusNodeId, generation: Int) {
        globalFallbackNodeId = nodeId
        globalFallbackGeneration = generation
    }

    /** 注销全局 fallback。如果还有其他 fallback 节点，则选择最后注册的可用节点。 */
    private fun unregisterGlobalFallbackNode(nodeId: WjzFocusNodeId, generation: Int) {
        if (globalFallbackNodeId != nodeId || globalFallbackGeneration != generation) return

        val fallback = entries.values
            .lastOrNull {
                it.node.id != nodeId &&
                        it.mounted &&
                        it.node.globalFallback &&
                        !isDisabledEntry(it)
            }

        globalFallbackNodeId = fallback?.node?.id
        globalFallbackGeneration = fallback?.generation
    }

    /**
     * 消费一个普通焦点组恢复意图。
     *
     * 锁定时只续期，不执行恢复。未锁定时把目标交给匹配的 [WjzFocusGroupRestorer]，成功后移除 pending。
     */
    internal suspend fun consumeGroupRestore(intent: WjzFocusIntent.RestoreGroup): Boolean {
        checkMainThread()
        if (isFocusLocked) {
            extendPendingRequest(intent, LockedPendingTtlMillis)
            WjzFocusLogger.verbose {
                "pending consume: mode=group-restore, result=locked-extended, intent=$intent, pending=${pendingRequests.size}"
            }
            return false
        }
        if (currentStrictRestoreWindow() != null) {
            WjzFocusLogger.verbose {
                "pending consume: mode=group-restore, result=strict-window-blocked, intent=$intent, pending=${pendingRequests.size}"
            }
            return false
        }

        val restorer = groupRestorers[
            WjzFocusGroupRestorerKey(
                layer = intent.layer,
                scopeId = intent.scopeId,
                restorerId = intent.restorerId,
                listId = intent.listId
            )
        ]
        if (restorer == null) {
            WjzFocusLogger.verbose {
                "pending consume: mode=group-restore, result=missing-restorer, intent=$intent, pending=${pendingRequests.size}"
            }
            updateFailure(
                nodeId = intent.nodeId,
                success = false,
                layer = intent.layer,
                scopeId = intent.scopeId,
                expectedGeneration = null,
                entry = entries[intent.nodeId],
                reason = "missing-group-restorer"
            )
            return false
        }

        val expectedGeneration = generationOf(intent.nodeId, intent.layer, intent.scopeId)
        val pendingRequestId = pendingRequests.lastOrNull { it.intent == intent }?.id
        WjzFocusLogger.verbose {
            "pending consume: mode=group-restore, result=attempt, intent=$intent, " +
                    "expectedGeneration=$expectedGeneration, pending=${pendingRequests.size}"
        }
        val restored = restorer.restore(
            WjzGroupFocusTarget(
                nodeId = intent.nodeId,
                layer = intent.layer,
                scopeId = intent.scopeId,
                restorerId = intent.restorerId,
                listId = intent.listId,
                expectedGeneration = expectedGeneration,
                fallbackNodeId = intent.fallbackNodeId
            )
        )
        if (restored) {
            if (pendingRequestId != null && removePendingRequest(pendingRequestId)) {
                notifyPendingChanged()
            }
            WjzFocusLogger.verbose {
                "pending consume: mode=group-restore, result=restored, intent=$intent, pending=${pendingRequests.size}"
            }
        } else {
            WjzFocusLogger.verbose {
                "pending consume: mode=group-restore, result=failed, intent=$intent, pending=${pendingRequests.size}"
            }
            updateFailure(
                nodeId = intent.nodeId,
                success = false,
                layer = intent.layer,
                scopeId = intent.scopeId,
                expectedGeneration = expectedGeneration,
                entry = entries[intent.nodeId],
                reason = "group-restore-failed"
            )
        }
        return restored
    }

    /**
     * 消费一个 Lazy 恢复意图。
     *
     * 锁定时只续期，不执行恢复。未锁定时把目标交给匹配的 [WjzLazyFocusRestorer]，成功后移除 pending。
     */
    internal suspend fun consumeLazyRestore(intent: WjzFocusIntent.RestoreLazyItem): Boolean {
        checkMainThread()
        if (isFocusLocked) {
            extendPendingRequest(intent, LockedPendingTtlMillis)
            WjzFocusLogger.verbose {
                "pending consume: mode=lazy-restore, result=locked-extended, intent=$intent, pending=${pendingRequests.size}"
            }
            return false
        }
        if (currentStrictRestoreWindow() != null) {
            WjzFocusLogger.verbose {
                "pending consume: mode=lazy-restore, result=strict-window-blocked, intent=$intent, pending=${pendingRequests.size}"
            }
            return false
        }

        val restorer = lazyRestorers[
            WjzFocusLazyRestorerKey(
                layer = intent.layer,
                scopeId = intent.scopeId,
                restorerId = intent.restorerId,
                listId = intent.listId
            )
        ]
        if (restorer == null) {
            WjzFocusLogger.verbose {
                "pending consume: mode=lazy-restore, result=missing-restorer, intent=$intent, pending=${pendingRequests.size}"
            }
            updateFailure(
                nodeId = intent.nodeId,
                success = false,
                layer = intent.layer,
                scopeId = intent.scopeId,
                expectedGeneration = null,
                entry = entries[intent.nodeId],
                reason = "missing-lazy-restorer"
            )
            return false
        }

        val expectedGeneration = generationOf(intent.nodeId, intent.layer, intent.scopeId)
        val pendingRequestId = pendingRequests.lastOrNull { it.intent == intent }?.id
        WjzFocusLogger.verbose {
            "pending consume: mode=lazy-restore, result=attempt, intent=$intent, " +
                    "expectedGeneration=$expectedGeneration, pending=${pendingRequests.size}"
        }
        val restored = restorer.restore(
            WjzLazyFocusTarget(
                nodeId = intent.nodeId,
                itemKey = intent.itemKey,
                layer = intent.layer,
                scopeId = intent.scopeId,
                restorerId = intent.restorerId,
                listId = intent.listId,
                expectedGeneration = expectedGeneration
            )
        )
        if (restored) {
            if (pendingRequestId != null && removePendingRequest(pendingRequestId)) {
                notifyPendingChanged()
            }
            WjzFocusLogger.verbose {
                "pending consume: mode=lazy-restore, result=restored, intent=$intent, pending=${pendingRequests.size}"
            }
        } else {
            WjzFocusLogger.verbose {
                "pending consume: mode=lazy-restore, result=failed, intent=$intent, pending=${pendingRequests.size}"
            }
            updateFailure(
                nodeId = intent.nodeId,
                success = false,
                layer = intent.layer,
                scopeId = intent.scopeId,
                expectedGeneration = expectedGeneration,
                entry = entries[intent.nodeId],
                reason = "lazy-restore-failed"
            )
        }
        return restored
    }

    /**
     * 执行 Lazy 目标恢复。
     *
     * 流程是：滚动到 item，等待下一帧，等待 item 可见且目标 node 按 generation 挂载，
     * 最后请求该 node。generation 检查用于拒绝旧组合生命周期留下的过期请求。
     * 这里等待 item 可见，是因为 Lazy 列表里的目标节点在不可见时通常还没有真正挂载。
     */
    internal suspend fun restoreLazyTarget(
        target: WjzLazyFocusTarget,
        scrollToItem: suspend (WjzFocusItemKey) -> Unit,
        isItemVisible: (WjzFocusItemKey) -> Boolean,
        restoreTimeoutMillis: Long = WjzLazyFocusDefaultRestoreTimeoutMillis
    ): Boolean {
        checkMainThread()
        if (isFocusLocked) {
            extendPendingRequest(
                intent = WjzFocusIntent.RestoreLazyItem(
                    nodeId = target.nodeId,
                    itemKey = target.itemKey,
                    layer = target.layer,
                    scopeId = target.scopeId,
                    restorerId = target.restorerId,
                    listId = target.listId
                ),
                ttlMillis = LockedPendingTtlMillis
            )
            return false
        }

        runCatching { scrollToItem(target.itemKey) }
            .onFailure {
                return false
            }

        val ready = awaitLazyTargetReady(
            target = target,
            isItemVisible = isItemVisible,
            restoreTimeoutMillis = restoreTimeoutMillis
        )
        if (!ready) {
            return false
        }

        val expectedGeneration = target.expectedGeneration
            ?: generationOf(target.nodeId, target.layer, target.scopeId)
            ?: return false
        if (!isRequestable(target.nodeId, target.layer, target.scopeId, expectedGeneration)) {
            return false
        }

        return requestFocusDetailed(
            nodeId = target.nodeId,
            layer = target.layer,
            scopeId = target.scopeId,
            enqueueIfMissing = false,
            expectedGeneration = expectedGeneration
        ) == WjzFocusRequestResult.Focused
    }

    /**
     * 执行普通焦点组目标恢复。
     *
     * 普通组不需要 lazy 列表的滚动和可见性等待；它只负责在目标节点已挂载时完成恢复请求。
     * 锁定、generation、layer 和 scope 校验仍然沿用 coordinator 的统一规则。
     */
    internal suspend fun restoreGroupTarget(
        target: WjzGroupFocusTarget
    ): Boolean {
        checkMainThread()
        if (isFocusLocked) {
            extendPendingRequest(
                intent = WjzFocusIntent.RestoreGroup(
                    nodeId = target.nodeId,
                    layer = target.layer,
                    scopeId = target.scopeId,
                    restorerId = target.restorerId,
                    listId = target.listId,
                    fallbackNodeId = target.fallbackNodeId
                ),
                ttlMillis = LockedPendingTtlMillis
            )
            return false
        }

        val initialGeneration = target.expectedGeneration
            ?: generationOf(target.nodeId, target.layer, target.scopeId)
        val targetReady = awaitNodeReady(
            nodeId = target.nodeId,
            expectedGeneration = initialGeneration,
            timeoutMillis = DefaultPendingTtlMillis,
            layer = target.layer,
            scopeId = target.scopeId
        )
        val expectedGeneration = initialGeneration
            ?: generationOf(target.nodeId, target.layer, target.scopeId)
        if (
            expectedGeneration != null &&
            targetReady &&
            requestFocusDetailed(
                nodeId = target.nodeId,
                layer = target.layer,
                scopeId = target.scopeId,
                enqueueIfMissing = false,
                expectedGeneration = expectedGeneration
            ) == WjzFocusRequestResult.Focused
        ) {
            return true
        }

        val fallbackNodeId = target.fallbackNodeId
        if (fallbackNodeId != null) {
            val initialFallbackGeneration = generationOf(fallbackNodeId, target.layer, target.scopeId)
            val fallbackReady = awaitNodeReady(
                nodeId = fallbackNodeId,
                expectedGeneration = initialFallbackGeneration,
                timeoutMillis = DefaultPendingTtlMillis,
                layer = target.layer,
                scopeId = target.scopeId
            )
            val fallbackGeneration = initialFallbackGeneration
                ?: generationOf(fallbackNodeId, target.layer, target.scopeId)
            if (
                fallbackGeneration != null &&
                fallbackReady &&
                requestFocusDetailed(
                    nodeId = fallbackNodeId,
                    layer = target.layer,
                    scopeId = target.scopeId,
                    enqueueIfMissing = false,
                    expectedGeneration = fallbackGeneration
                ) == WjzFocusRequestResult.Focused
            ) {
                return true
            }
        }

        return restoreActiveLayer(target.scopeId)
    }

    /**
     * 等待 Lazy 目标进入“可请求”状态。
     *
     * 这里合并两类信号：
     *
     * 1. [snapshotFlow] 监听业务提供的可见性判断；
     * 2. [nodeMountWakeupSignals] 只唤醒等待当前 nodeId 的协程。
     *
     * 每次唤醒后都会重新读取 coordinator 当前状态，因此 SharedFlow 只是触发器，
     * 不是状态来源。真正的 ready 条件仍然是 item 可见且节点 mounted + placed + generation 匹配。
     */
    private suspend fun awaitLazyTargetReady(
        target: WjzLazyFocusTarget,
        isItemVisible: (WjzFocusItemKey) -> Boolean,
        restoreTimeoutMillis: Long
    ): Boolean {
        fun ready(): Boolean {
            return isItemVisible(target.itemKey) &&
                    isRequestable(
                        nodeId = target.nodeId,
                        layer = target.layer,
                        scopeId = target.scopeId,
                        expectedGeneration = target.expectedGeneration
                    )
        }

        if (ready()) return true
        if (isItemVisible(target.itemKey)) {
            return awaitNodeReady(
                nodeId = target.nodeId,
                expectedGeneration = target.expectedGeneration,
                timeoutMillis = restoreTimeoutMillis,
                layer = target.layer,
                scopeId = target.scopeId
            )
        }

        return withTimeoutOrNull(restoreTimeoutMillis) {
            merge(
                snapshotFlow { isItemVisible(target.itemKey) }.map {},
                nodeMountWakeupSignals
                    .filter { nodeId -> nodeId == target.nodeId }
                    .map {}
            ).first { ready() }
            true
        } ?: false
    }

    /** 取得指定普通焦点组 restorer 、list 当前可消费的 pending 恢复意图。 */
    internal fun pendingGroupIntents(
        layer: WjzFocusLayer = activeLayer,
        scopeId: WjzFocusScopeId? = null,
        restorerId: String,
        listId: String
    ): List<WjzFocusIntent.RestoreGroup> {
        checkMainThread()
        if (currentStrictRestoreWindow() != null) return emptyList()
        dropExpiredPendingRequests()
        return pendingRequests.mapNotNull { request ->
            (request.intent as? WjzFocusIntent.RestoreGroup)
                ?.takeIf {
                    it.layer == layer &&
                            it.scopeId == scopeId &&
                            it.restorerId == restorerId &&
                            it.listId == listId
                }
        }
    }

    /** 取得指定 Lazy restorer 、list 当前可消费的 pending 恢复意图。 */
    internal fun pendingLazyIntents(
        layer: WjzFocusLayer = activeLayer,
        scopeId: WjzFocusScopeId? = null,
        restorerId: String,
        listId: String
    ): List<WjzFocusIntent.RestoreLazyItem> {
        checkMainThread()
        if (currentStrictRestoreWindow() != null) return emptyList()
        dropExpiredPendingRequests()
        return pendingRequests.mapNotNull { request ->
            (request.intent as? WjzFocusIntent.RestoreLazyItem)
                ?.takeIf {
                    it.layer == layer &&
                            it.scopeId == scopeId &&
                            it.restorerId == restorerId &&
                            it.listId == listId
                }
        }
    }

    /** 当前 pending 队列数量，读取前会先丢弃过期请求。 */
    internal fun pendingRequestCount(): Int {
        checkMainThread()
        dropExpiredPendingRequests()
        return pendingRequests.size
    }

    /** 某节点连续请求失败次数，用于测试和降级判断。 */
    internal fun failureCount(nodeId: WjzFocusNodeId): Int {
        checkMainThread()
        return failureCounts[nodeId]?.count ?: 0
    }

    /**
     * Host onEnter 的受控入口请求。
     *
     * 已聚焦节点优先，容器策略会向内部末端节点下钻；找到目标后必须交回 [requestFocusDetailed]，
     * 统一校验 node exists、active layer、disabled region、requester result 和 generation。
     *
     * @param layer 要进入的 layer。
     * @param scopeId 要进入的 scope。
     */
    internal fun enterFocusRequest(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?
    ): WjzFocusEnterRequestResult {
        checkMainThread()
        if (isFocusLocked || layer != activeLayer) return WjzFocusEnterRequestResult.Cancelled

        dropExpiredPendingRequests()
        val entry = enterLeafEntry(layer, scopeId) ?: return WjzFocusEnterRequestResult.NativeSearch
        return when (
            requestFocusDetailed(
                nodeId = entry.node.id,
                layer = layer,
                scopeId = scopeId,
                enqueueIfMissing = false,
                expectedGeneration = entry.generation
            )
        ) {
            WjzFocusRequestResult.Focused -> WjzFocusEnterRequestResult.Focused
            WjzFocusRequestResult.Dropped,
            WjzFocusRequestResult.Enqueued,
            WjzFocusRequestResult.Failed -> WjzFocusEnterRequestResult.Failed
        }
    }

    /** 添加焦点锁 token，并延长已存在 pending 请求的有效时间，避免过渡期内 TTL 过早到期。 */
    override fun lockFocus(token: Any) {
        checkMainThread()
        lockTokens.add(
            WjzFocusLockRecord(
                token = token,
                expiresAtUptimeMillis = SystemClock.uptimeMillis() + FocusLockMaxTimeoutMillis
            )
        )
        extendLockedPendingRequests()
        scheduleLockWatchdog()
    }

    /** 锁定期间只保留最后一次方向输入，并顺手续期已有 pending。 */
    override fun recordLockedDirectionIntent(direction: FocusDirection): Boolean {
        checkMainThread()
        if (!isFocusLocked) return false

        val now = SystemClock.uptimeMillis()
        lockedDirectionIntent = WjzFocusLockedDirectionIntent(
            direction = direction,
            layer = activeLayer,
            deadlineUptimeMillis = now + LockedPendingTtlMillis
        )
        extendLockedPendingRequests()
        WjzFocusLogger.info {
            "locked direction captured: direction=$direction, layer=$activeLayer, " +
                    "lockCount=${lockTokens.size}, pending=${pendingRequests.size}"
        }
        return true
    }

    /** 释放焦点锁 token，最后一个锁释放后刷新并消费 pending 请求。 */
    override fun unlockFocus(token: Any) {
        checkMainThread()
        val index = lockTokens.indexOfLast { it.token == token }
        if (index >= 0) {
            lockTokens.removeAt(index)
        }
        if (!isFocusLocked) {
            lockWatchdogJob?.cancel()
            lockWatchdogJob = null
            refreshPendingRequests(LockedPendingTtlMillis)
            consumePendingRequests()
            consumeLockedDirectionIntent()
        }
    }

    /**
     * 主动清理过期锁。
     *
     * 锁超时不能依赖 [isFocusLocked] getter 被动触发，否则在没有新按键或新组合读取时，异常 token 可能长期滞留。
     * watchdog 到期后会主动释放过期 token，并在所有锁释放后消费 pending。
     */
    private fun scheduleLockWatchdog() {
        val nextExpiresAt = lockTokens.minByOrNull { it.expiresAtUptimeMillis }?.expiresAtUptimeMillis ?: return
        val delayMillis = (nextExpiresAt - SystemClock.uptimeMillis()).coerceAtLeast(0L)
        lockWatchdogJob?.cancel()
        lockWatchdogJob = watchdogScope.launch {
            delay(delayMillis)
            checkMainThread()
            val now = SystemClock.uptimeMillis()
            val changed = lockTokens.removeAll { it.expiresAtUptimeMillis <= now }
            if (changed) {
                wjzFocusLogger.fWarn { "focus lock token expired and was force released" }
            }
            if (lockTokens.isEmpty()) {
                lockWatchdogJob?.cancel()
                lockWatchdogJob = null
                refreshPendingRequests(DefaultPendingTtlMillis)
                consumePendingRequests()
                consumeLockedDirectionIntent()
            } else if (lockTokens.isNotEmpty()) {
                scheduleLockWatchdog()
            }
        }
    }

    /** 生成新的节点挂载 generation。 */
    private fun nextGeneration(): Int {
        generationSeed += 1
        return generationSeed
    }

    /** 生成新的来源记录 token。 */
    private fun nextSourceToken(): WjzFocusSourceToken {
        sourceTokenSeed += 1
        return WjzFocusSourceToken(sourceTokenSeed)
    }

    /** 生成新的 pending 请求 id。 */
    private fun nextPendingRequestId(): Long {
        pendingRequestIdSeed += 1
        return pendingRequestIdSeed
    }

    /** 捕获当前 active layer 中最后一个可直接恢复的 leaf 焦点，作为可恢复来源。 */
    private fun currentFocusSource(token: WjzFocusSourceToken): WjzFocusSource {
        val focused = entries.values.lastOrNull { entry ->
            entry.isSourceCandidateFor(activeLayer, entry.node.scopeId)
        }?.let { entry ->
            entry.asLeafEntryFor(activeLayer, entry.node.scopeId)
        }
        val source = focused ?: findRecentFocusNode(
            layer = activeLayer,
            allowAnyScope = true
        )
        wjzFocusLogger.fInfo {
            val sourceFrom = when {
                focused != null -> "focused-leaf"
                source != null -> "recent"
                else -> "none"
            }
            "current focus source: layer=$activeLayer, sourceNode=${source?.node?.id?.value}, " +
                    "sourceScope=${source?.node?.scopeId?.value}, from=$sourceFrom"
        }
        return WjzFocusSource(
            layer = activeLayer,
            scopeId = source?.node?.scopeId,
            nodeId = source?.node?.id,
            token = token
        )
    }

    /** 弹出指定 token 的来源，token 为空时弹出最近来源。 */
    private fun popSource(token: WjzFocusSourceToken?): WjzFocusSource? {
        val index = if (token == null) {
            sourceStack.lastIndex
        } else {
            sourceStack.indexOfLast { it.token == token }
        }
        if (index < 0) return null
        return sourceStack.removeAt(index)
    }

    /** 新增 pending 请求，相同 dedupe key 的旧请求会先被替换。 */
    private fun enqueuePendingRequest(
        intent: WjzFocusIntent,
        ttlMillis: Long,
        prioritize: Boolean = false,
        submitDedupeKey: Any? = null
    ) {
        try {
            checkMainThread()
            val now = SystemClock.uptimeMillis()
            pendingRequests.removeAll { request ->
                request.intent.dedupeKey == intent.dedupeKey ||
                        (submitDedupeKey != null && request.submitDedupeKey == submitDedupeKey)
            }
            val request = WjzFocusPendingRequest(
                id = nextPendingRequestId(),
                intent = intent,
                deadlineUptimeMillis = now + ttlMillis,
                submitDedupeKey = submitDedupeKey
            )
            if (prioritize) {
                pendingRequests.add(0, request)
            } else {
                pendingRequests.add(request)
            }
            notifyPendingChanged(intent)
            wjzFocusLogger.fDebug { "enqueue focus intent: $intent" }
            WjzFocusLogger.verbose {
                "pending enqueue: intent=$intent, ttlMillis=$ttlMillis, prioritize=$prioritize, " +
                        "submitDedupeKey=$submitDedupeKey, pending=${pendingRequests.size}, locked=$isFocusLocked"
            }
        } finally {
            validateInternalState("enqueuePendingRequest")
        }
    }

    /** 节点刚挂载时消费等待该节点的 pending 请求。scope 为 null 的请求按 scope 通配匹配。 */
    private fun consumePendingRequests(node: WjzFocusNode) {
        try {
            checkMainThread()
            if (isFocusLocked || node.layer != activeLayer) return

            val currentEntry = entries[node.id] ?: return
            if (!currentEntry.placed) return
            when (consumeStrictRestoreWindow()) {
                WjzFocusStrictRestoreWindowResult.Restored -> return
                WjzFocusStrictRestoreWindowResult.Inactive -> Unit
                WjzFocusStrictRestoreWindowResult.Blocked -> return
            }

            dropExpiredPendingRequests()
            val requests = pendingRequests.filter { request ->
                val intent = request.intent as? WjzFocusIntent.RequestNode ?: return@filter false
                intent.nodeId == node.id &&
                        intent.layer == node.layer &&
                        isRequestable(
                            nodeId = node.id,
                            layer = node.layer,
                            scopeId = intent.scopeId,
                            expectedGeneration = intent.expectedGeneration
                        ) &&
                        (intent.scopeId == null || intent.scopeId == node.scopeId)
            }
            if (requests.isEmpty()) return

            requests.forEach { request ->
                val intent = request.intent as? WjzFocusIntent.RequestNode ?: return@forEach
                WjzFocusLogger.verbose {
                    "pending consume: mode=node-mount, intent=$intent, nodeId=${node.id.value}, pending=${pendingRequests.size}"
                }
                if (requestFocusDetailed(
                    nodeId = intent.nodeId,
                    layer = intent.layer,
                    scopeId = intent.scopeId,
                    enqueueIfMissing = false,
                    expectedGeneration = intent.expectedGeneration
                ) == WjzFocusRequestResult.Focused) {
                    pendingRequests.remove(request)
                    notifyPendingChanged()
                }
            }
        } finally {
            validateInternalState("consumePendingRequests(node)")
        }
    }

    /** 节点完成布局后，重试同 active layer 且同 scope 的 layer 恢复请求。 */
    private fun retryPendingRestoreLayer(node: WjzFocusNode) {
        checkMainThread()
        if (isFocusLocked || node.layer != activeLayer) return

        val currentEntry = entries[node.id] ?: return
        if (!currentEntry.placed) return
        when (consumeStrictRestoreWindow()) {
            WjzFocusStrictRestoreWindowResult.Restored -> return
            WjzFocusStrictRestoreWindowResult.Inactive -> Unit
            WjzFocusStrictRestoreWindowResult.Blocked -> return
        }

        val hasRestoreLayerRequest = pendingRequests.any { request ->
            when (val intent = request.intent) {
                is WjzFocusIntent.RestoreLayer -> {
                    intent.layer == node.layer &&
                            (intent.scopeId == null || intent.scopeId == node.scopeId)
                }
                is WjzFocusIntent.RestoreDisabledScope -> {
                    intent.layer == node.layer && intent.scopeId == node.scopeId
                }
                else -> false
            }
        }
        if (hasRestoreLayerRequest) {
            consumePendingRequests()
        }
    }

    /** 扫描并消费当前可执行的 pending 请求。Lazy 请求由 Lazy Host 单独消费。 */
    private fun consumePendingRequests() {
        try {
            checkMainThread()
            dropExpiredPendingRequests()
            when (consumeStrictRestoreWindow()) {
                WjzFocusStrictRestoreWindowResult.Restored -> return
                WjzFocusStrictRestoreWindowResult.Inactive -> Unit
                WjzFocusStrictRestoreWindowResult.Blocked -> return
            }
            pendingRequests
                .toList()
                .forEach { request ->
                    when (val intent = request.intent) {
                        is WjzFocusIntent.RequestNode -> {
                            WjzFocusLogger.verbose {
                                "pending consume: mode=scan, intent=$intent, pending=${pendingRequests.size}"
                            }
                            if (requestFocusDetailed(
                                    nodeId = intent.nodeId,
                                    layer = intent.layer,
                                    scopeId = intent.scopeId,
                                    enqueueIfMissing = false,
                                    expectedGeneration = intent.expectedGeneration
                                ) == WjzFocusRequestResult.Focused
                            ) {
                                pendingRequests.remove(request)
                                notifyPendingChanged()
                            }
                        }

                        is WjzFocusIntent.RestoreLayer -> {
                            WjzFocusLogger.verbose {
                                "pending consume: mode=restore-layer, intent=$intent, pending=${pendingRequests.size}"
                            }
                            if (restoreActiveLayer(intent.scopeId)) {
                                pendingRequests.remove(request)
                                notifyPendingChanged()
                            }
                        }

                        is WjzFocusIntent.RestoreDisabledScope -> {
                            WjzFocusLogger.verbose {
                                "pending consume: mode=restore-disabled-scope, intent=$intent, pending=${pendingRequests.size}"
                            }
                            if (intent.layer == activeLayer && restoreDisabledFocusedScope(intent.scopeId)) {
                                pendingRequests.remove(request)
                                notifyPendingChanged()
                            }
                        }

                        is WjzFocusIntent.RestoreGroup -> Unit
                        is WjzFocusIntent.RestoreLazyItem -> Unit
                    }
                }
        } finally {
            validateInternalState("consumePendingRequests")
        }
    }

    /** 消费锁定期 coalesce 后的最后一次方向意图；没有显式路由/出口时直接丢弃，避免猜测式跳转。 */
    private fun consumeLockedDirectionIntent() {
        checkMainThread()
        if (isFocusLocked) return

        val intent = lockedDirectionIntent ?: return
        lockedDirectionIntent = null

        if (intent.layer != activeLayer || intent.deadlineUptimeMillis <= SystemClock.uptimeMillis()) {
            WjzFocusLogger.info {
                "locked direction dropped: reason=invalid-layer-or-expired, direction=${intent.direction}, " +
                        "intentLayer=${intent.layer}, activeLayer=$activeLayer"
            }
            return
        }

        val focusedEntry = entries.values.lastOrNull { entry ->
            entry.isSourceCandidateFor(activeLayer, null)
        }?.asLeafEntryFor(activeLayer, null)
        if (focusedEntry == null) {
            WjzFocusLogger.info {
                "locked direction dropped: reason=missing-focused-leaf, direction=${intent.direction}, " +
                        "activeLayer=$activeLayer"
            }
            return
        }
        if (!focusedEntry.routingReady) {
            wjzFocusLogger.fWarn {
                "drop locked focus direction intent because focused node routing is not confirmed: " +
                        "nodeId=${focusedEntry.node.id.value}, direction=${intent.direction}"
            }
            WjzFocusLogger.warn {
                "locked direction dropped: reason=routing-not-ready, nodeId=${focusedEntry.node.id.value}, " +
                        "direction=${intent.direction}"
            }
            return
        }

        val handledByRouter = focusedEntry.node.directionHandlers.any { handler ->
            handler.handle(intent.direction, this)
        }
        if (handledByRouter) {
            WjzFocusLogger.info {
                "locked direction consumed: source=router, nodeId=${focusedEntry.node.id.value}, " +
                        "direction=${intent.direction}"
            }
            return
        }

        val consumedByNodeExit = focusedEntry.node.exits
            .firstOrNull { exit -> exit.direction == intent.direction }
            ?.consume(this) == true
        if (consumedByNodeExit) {
            WjzFocusLogger.info {
                "locked direction consumed: source=node-exit, nodeId=${focusedEntry.node.id.value}, " +
                        "direction=${intent.direction}"
            }
            return
        }

        val consumedByHostExit = hostExits.values
            .lastOrNull { hostExit ->
                hostExit.scopeId == focusedEntry.node.scopeId &&
                        hostExit.exits.any { exit -> exit.direction == intent.direction }
            }
            ?.exits
            ?.firstOrNull { exit -> exit.direction == intent.direction }
            ?.consume(this) == true
        WjzFocusLogger.info {
            "locked direction consumed: source=${if (consumedByHostExit) "host-exit" else "none"}, " +
                    "nodeId=${focusedEntry.node.id.value}, direction=${intent.direction}"
        }
    }

    /**
     * 当前 layer 、scope 无法恢复时，尝试请求全局 fallback。
     *
     * `fallback` 只在本 scope 内兜底，`globalFallback` 则用于当前 layer 范围更宽的最终兜底。
     */
    private fun restoreGlobalFallback(scopeId: WjzFocusScopeId?): Boolean {
        if (currentStrictRestoreWindow() != null) return false
        val currentFallback = globalFallbackNodeId
            ?.let { entries[it] }
            ?.takeIf {
                it.generation == globalFallbackGeneration &&
                        it.node.globalFallback &&
                        it.isRequestableFor(activeLayer, null)
            }
        val fallback = currentFallback ?: entriesInLayer(activeLayer)
            .lastOrNull {
                it.node.globalFallback &&
                        it.isRequestableFor(activeLayer, null)
            }

        globalFallbackNodeId = fallback?.node?.id
        globalFallbackGeneration = fallback?.generation

        return when (fallback?.node?.strategy) {
            WjzFocusRestoreStrategy.RequestFocus -> {
                val leaf = fallback.asLeafEntryFor(activeLayer, null) ?: return false
                requestFocusDetailed(
                    nodeId = leaf.node.id,
                    layer = activeLayer,
                    scopeId = leaf.node.scopeId,
                    enqueueIfMissing = false,
                    expectedGeneration = leaf.generation
                ) == WjzFocusRequestResult.Focused
            }
            WjzFocusRestoreStrategy.Container -> {
                val restoreScopeId = fallback.node.scopeId ?: scopeId
                val leaf = enterLeafEntry(activeLayer, restoreScopeId) ?: return false
                requestFocusDetailed(
                    nodeId = leaf.node.id,
                    layer = activeLayer,
                    scopeId = restoreScopeId,
                    enqueueIfMissing = false,
                    expectedGeneration = leaf.generation
                ) == WjzFocusRequestResult.Focused
            }
            null -> false
        }
    }

    private fun restoreDisabledFocusedScope(scopeId: WjzFocusScopeId?): Boolean {
        checkMainThread()
        if (isFocusLocked) return false

        dropExpiredPendingRequests()
        val entry = enterLeafEntryStrict(activeLayer, scopeId) ?: return false
        return requestFocusDetailedInternal(
            nodeId = entry.node.id,
            layer = activeLayer,
            scopeId = scopeId,
            enqueueIfMissing = false,
            expectedGeneration = entry.generation,
            allowFailureDegrade = false,
            bypassStrictRestoreWindow = true,
            submitDedupeKey = null
        ) == WjzFocusRequestResult.Focused
    }

    /** 找到进入某个 layer、scope 时最合适的末端节点。已聚焦节点优先，其次才是 fallback 节点。 */
    private fun enterLeafEntry(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?
    ): WjzFocusLeafEntry? {
        val entries = entriesInLayerScope(layer, scopeId, strictScope = false)
        val focused = entries.lastOrNull {
            it.isRequestableLeafTargetFor(layer, scopeId) && it.hasFocus
        }
        focused?.asLeafEntryFor(layer, scopeId)?.let { return it }

        return entries.lastOrNull {
            it.isRequestableLeafTargetFor(layer, scopeId) && it.node.fallback
        }?.asLeafEntryFor(layer, scopeId)
    }

    /** 严格限制在当前 layer + 当前 scopeId 内寻找末端节点，scopeId 为 null 时也不做通配。 */
    private fun enterLeafEntryStrict(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?
    ): WjzFocusLeafEntry? {
        val entries = entriesInLayerScope(layer, scopeId, strictScope = true)
        val focused = entries.lastOrNull {
            it.isStrictRequestFocusLeafFor(layer, scopeId) && it.hasFocus
        }
        focused?.asLeafEntryFor(layer, scopeId)?.let { return it }

        return entries.lastOrNull {
            it.isStrictRequestFocusLeafFor(layer, scopeId) && it.node.fallback
        }?.asLeafEntryFor(layer, scopeId)
    }

    /** 严格限制在当前 layer + 当前 scopeId 内寻找末端节点，并排除指定节点。 */
    private fun enterLeafEntryStrictExcluding(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?,
        excludedNodeId: WjzFocusNodeId
    ): WjzFocusLeafEntry? {
        val entries = entriesInLayerScope(layer, scopeId, strictScope = true)
        val focused = entries.lastOrNull {
            it.node.id != excludedNodeId &&
                    it.isStrictRequestFocusLeafFor(layer, scopeId) &&
                    it.hasFocus
        }
        focused?.asLeafEntryFor(layer, scopeId)?.let { return it }

        return entries.lastOrNull {
            it.node.id != excludedNodeId &&
                    it.isStrictRequestFocusLeafFor(layer, scopeId) &&
                    it.node.fallback
        }?.asLeafEntryFor(layer, scopeId)
    }

    /** 更新失败计数，连续失败过多时降级为恢复当前 layer。 */
    private fun updateFailure(
        nodeId: WjzFocusNodeId,
        success: Boolean,
        layer: WjzFocusLayer = activeLayer,
        scopeId: WjzFocusScopeId? = null,
        expectedGeneration: Int? = null,
        entry: WjzFocusEntry? = entries[nodeId],
        reason: String = "requester-returned-false",
        allowFailureDegrade: Boolean = true
    ) {
        checkMainThread()
        if (success) {
            failureCounts.remove(nodeId)
            return
        }
        if (!allowFailureDegrade) return

        val now = SystemClock.uptimeMillis()
        val previous = failureCounts[nodeId]
        // 超出时间窗口的旧失败视为过期，重新从 1 计数，避免几分钟前的偶发失败凑齐阈值。
        val count = if (previous != null && now - previous.lastFailureUptimeMillis <= FailureCountResetWindowMillis) {
            previous.count + 1
        } else {
            1
        }
        failureCounts[nodeId] = WjzFocusFailureRecord(count = count, lastFailureUptimeMillis = now)
        wjzFocusLogger.fWarn {
            "focus request failed: reason=$reason, result=${WjzFocusRequestResult.Failed}, nodeId=${nodeId.value}, " +
                    "count=$count/$FailureDegradeThreshold, targetLayer=$layer, " +
                    "activeLayer=$activeLayer, targetScope=${scopeId?.value}, " +
                    "nodeScope=${entry?.node?.scopeId?.value}, nodeLayer=${entry?.node?.layer}, " +
                    "mounted=${entry?.mounted}, hasFocus=${entry?.hasFocus}, " +
                    "generation=${entry?.generation}, expectedGeneration=$expectedGeneration, " +
                    "pending=${pendingRequests.size}, locked=$isFocusLocked"
        }
        if (count >= FailureDegradeThreshold && !handlingFailureDegrade) {
            failureCounts.remove(nodeId)
            handlingFailureDegrade = true
            try {
                wjzFocusLogger.fWarn {
                    "focus failure degrade: nodeId=${nodeId.value}, activeLayer=$activeLayer, " +
                            "targetLayer=$layer, targetScope=${scopeId?.value}, " +
                            "nodeScope=${entry?.node?.scopeId?.value}, pending=${pendingRequests.size}"
                }
                handleFocusFailureDegrade(
                    nodeId = nodeId,
                    targetLayer = layer,
                    targetScopeId = scopeId ?: entry?.node?.scopeId
                )
            } finally {
                handlingFailureDegrade = false
            }
        }
    }

    /** 按 Wjz 自己的焦点上下文逐级降级，避免直接恢复 layer 导致焦点大幅跳动。 */
    private fun handleFocusFailureDegrade(
        nodeId: WjzFocusNodeId,
        targetLayer: WjzFocusLayer,
        targetScopeId: WjzFocusScopeId?
    ) {
        checkMainThread()
        val strictWindow = currentStrictRestoreWindow()
        if (strictWindow != null) {
            when (consumeStrictRestoreWindow()) {
                WjzFocusStrictRestoreWindowResult.Restored -> return
                WjzFocusStrictRestoreWindowResult.Blocked -> {
                    return
                }
                WjzFocusStrictRestoreWindowResult.Inactive -> Unit
            }
        }
        val recentInScope = entries.values.lastOrNull { candidate ->
            candidate.node.id != nodeId &&
                    candidate.isSourceCandidateFor(targetLayer, targetScopeId)
        }?.asLeafEntryFor(targetLayer, targetScopeId)
        if (recentInScope != null && requestFocusDetailed(
                nodeId = recentInScope.node.id,
                layer = targetLayer,
                scopeId = targetScopeId,
                enqueueIfMissing = false,
                expectedGeneration = recentInScope.generation
            ) == WjzFocusRequestResult.Focused
        ) {
            return
        }

        val fallbackInScope = entries.values.lastOrNull { candidate ->
            candidate.node.id != nodeId &&
                    candidate.node.fallback &&
                    candidate.isRequestableLeafTargetFor(targetLayer, targetScopeId)
        }?.asLeafEntryFor(targetLayer, targetScopeId)
        if (fallbackInScope != null && requestFocusDetailed(
                nodeId = fallbackInScope.node.id,
                layer = targetLayer,
                scopeId = targetScopeId,
                enqueueIfMissing = false,
                expectedGeneration = fallbackInScope.generation
            ) == WjzFocusRequestResult.Focused
        ) {
            return
        }

        val globalFallback = (
                globalFallbackNodeId
                    ?.let { entries[it] }
                    ?.takeIf { candidate ->
                        candidate.node.id != nodeId &&
                                candidate.generation == globalFallbackGeneration &&
                                candidate.node.globalFallback &&
                                candidate.isRequestableLeafTargetFor(targetLayer, null)
                    }
                    ?: entries.values.lastOrNull { candidate ->
                        candidate.node.id != nodeId &&
                                candidate.node.globalFallback &&
                                candidate.isRequestableLeafTargetFor(targetLayer, null)
                    }
                )?.asLeafEntryFor(targetLayer, null)
        if (globalFallback != null && requestFocusDetailed(
                nodeId = globalFallback.node.id,
                layer = targetLayer,
                scopeId = globalFallback.node.scopeId,
                enqueueIfMissing = false,
                expectedGeneration = globalFallback.generation
            ) == WjzFocusRequestResult.Focused
        ) {
            return
        }

        if (targetLayer == activeLayer && restoreActiveLayer(targetScopeId)) return
        enqueueRestoreLayer(targetLayer)
        if (!isFocusLocked) {
            consumePendingRequests()
        }
    }

    private fun WjzFocusEntry.isRequestableFor(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?
    ): Boolean {
        return isRequestableIn(layer, scopeId) && !isDisabledEntry(this)
    }

    private fun WjzFocusEntry.isRequestableLeafTargetFor(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?
    ): Boolean {
        return isRequestableFor(layer, scopeId) &&
                node.kind == WjzFocusNodeKind.Leaf
    }

    private fun WjzFocusEntry.asLeafEntryFor(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?
    ): WjzFocusLeafEntry? {
        return if (isRequestableLeafTargetFor(layer, scopeId)) {
            WjzFocusLeafEntry(this)
        } else {
            null
        }
    }

    private fun WjzFocusEntry.isSourceCandidateFor(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?
    ): Boolean {
        return hasFocus && isRequestableLeafTargetFor(layer, scopeId)
    }

    private fun WjzFocusEntry.isRecentFocusCandidateFor(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?,
        allowAnyScope: Boolean
    ): Boolean {
        return mounted &&
                placed &&
                node.layer == layer &&
                node.kind == WjzFocusNodeKind.Leaf &&
                (allowAnyScope || node.scopeId == scopeId) &&
                !isDisabledEntry(this)
    }

    private fun WjzFocusEntry.isStrictlyRequestableFor(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?
    ): Boolean {
        return isInLayerScope(layer, scopeId) &&
                placed &&
                !isDisabledEntry(this)
    }

    private fun WjzFocusEntry.isStrictRequestFocusLeafFor(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?
    ): Boolean {
        return isStrictlyRequestableFor(layer, scopeId) &&
                node.kind == WjzFocusNodeKind.Leaf
    }

    internal fun isBoundsDisabled(
        bounds: Rect?,
        disabledFocusContext: WjzDisabledFocusContext = WjzDisabledFocusContext()
    ): Boolean {
        bounds ?: return false
        return disabledFocusRegions.values.any { region ->
            shouldApplyDisabledRegion(
                region = region,
                nodeBounds = bounds,
                disabledFocusContext = disabledFocusContext
            )
        }
    }

    internal fun shouldApplyDisabledFocusProperties(
        nodeId: WjzFocusNodeId,
        generation: Int,
        bounds: Rect?,
        disabledFocusContext: WjzDisabledFocusContext = WjzDisabledFocusContext()
    ): Boolean {
        checkMainThread()
        val entry = entries[nodeId] ?: return false
        if (entry.generation != generation) return false
        if (!isBoundsDisabled(bounds, disabledFocusContext)) return false
        if (!entry.hasFocus) return true
        if (entry.node.layer != activeLayer) return true
        return hasStrictRestoreCandidate(
            layer = entry.node.layer,
            scopeId = entry.node.scopeId
        )
    }

    private fun isDisabledEntry(entry: WjzFocusEntry): Boolean {
        return isBoundsDisabled(entry.bounds, entry.disabledFocusContext)
    }

    private fun shouldApplyDisabledRegion(
        region: WjzDisabledFocusRegionRecord,
        nodeBounds: Rect,
        disabledFocusContext: WjzDisabledFocusContext
    ): Boolean {
        if (!region.bounds.overlaps(nodeBounds)) return false
        if (region.zIndex < disabledFocusContext.zIndex) return false
        return region.group == null || region.group == disabledFocusContext.group
    }

    private fun restoreDisabledFocusedEntries(): Boolean {
        val entry = entries.values.lastOrNull {
            it.hasFocus &&
                    it.node.layer == activeLayer &&
                    isDisabledEntry(it)
        }
            ?: return false

        beginStrictRestoreWindow(activeLayer, entry.node.scopeId)
        consumeStrictRestoreWindow()
        return true
    }

    private fun beginStrictRestoreWindow(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?
    ) {
        checkMainThread()
        strictRestoreWindow = WjzFocusStrictRestoreWindow(layer = layer, scopeId = scopeId)
        val changed = pendingRequests.removeAll { request ->
            val intent = request.intent as? WjzFocusIntent.RestoreDisabledScope ?: return@removeAll false
            !strictRestoreWindow!!.matches(intent.layer, intent.scopeId)
        }
        if (changed) notifyPendingChanged()
    }

    private fun currentStrictRestoreWindow(): WjzFocusStrictRestoreWindow? {
        checkMainThread()
        val window = strictRestoreWindow ?: return null
        if (window.layer != activeLayer) {
            strictRestoreWindow = null
            return null
        }
        return window
    }

    private fun consumeStrictRestoreWindow(): WjzFocusStrictRestoreWindowResult {
        checkMainThread()
        val window = currentStrictRestoreWindow() ?: return WjzFocusStrictRestoreWindowResult.Inactive
        if (isFocusLocked) return WjzFocusStrictRestoreWindowResult.Blocked

        if (restoreDisabledFocusedScope(window.scopeId)) {
            clearStrictRestoreWindow(window)
            return WjzFocusStrictRestoreWindowResult.Restored
        }

        if (hasDisabledFocusedEntry(window.layer, window.scopeId)) {
            if (!hasStrictRestoreCandidate(window.layer, window.scopeId)) {
                clearStrictRestoreWindow(window)
                return WjzFocusStrictRestoreWindowResult.Inactive
            }
            if (!hasPendingRestoreDisabledScope(window.layer, window.scopeId)) {
                enqueueRestoreDisabledScope(window.layer, window.scopeId)
            }
            return WjzFocusStrictRestoreWindowResult.Blocked
        }

        clearStrictRestoreWindow(window)
        return WjzFocusStrictRestoreWindowResult.Inactive
    }

    private fun clearStrictRestoreWindow(
        window: WjzFocusStrictRestoreWindow,
        removePendingIntent: Boolean = true
    ) {
        checkMainThread()
        if (strictRestoreWindow != window) return
        strictRestoreWindow = null
        if (!removePendingIntent) return

        val changed = pendingRequests.removeAll { request ->
            val intent = request.intent as? WjzFocusIntent.RestoreDisabledScope ?: return@removeAll false
            window.matches(intent.layer, intent.scopeId)
        }
        if (changed) notifyPendingChanged()
    }

    private fun hasDisabledFocusedEntry(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?
    ): Boolean {
        return entries.values.any {
            it.hasFocus &&
                    it.node.layer == layer &&
                    it.node.scopeId == scopeId &&
                    isDisabledEntry(it)
        }
    }

    private fun hasStrictRestoreCandidate(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?
    ): Boolean {
        return enterLeafEntryStrict(layer, scopeId) != null
    }

    private fun invalidateRegisteredFocusProperties() {
        checkMainThread()
        val invalidators = focusPropertiesInvalidators.values.toList()
        invalidators.forEach { record -> record.invalidator() }
    }

    private fun hasPendingRestoreDisabledScope(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?
    ): Boolean {
        return pendingRequests.any { request ->
            val intent = request.intent as? WjzFocusIntent.RestoreDisabledScope ?: return@any false
            intent.layer == layer && intent.scopeId == scopeId
        }
    }

    private fun hasPendingRestoreLayer(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?
    ): Boolean {
        return pendingRequests.any { request ->
            val intent = request.intent as? WjzFocusIntent.RestoreLayer ?: return@any false
            intent.layer == layer && intent.scopeId == scopeId
        }
    }

    private fun validateInternalState(reason: String) {
        checkMainThread()
        val violations = collectInternalStateViolations()
        if (violations.isEmpty()) return

        val message = "wjzFocus internal state invalid: reason=$reason, violations=${violations.joinToString()}"
        if (shouldFailFastInternalStateValidation()) {
            error(message)
        }

        wjzFocusLogger.fWarn { message }
        repairInternalState(reason)

        val remainingViolations = collectInternalStateViolations()
        if (remainingViolations.isNotEmpty()) {
            wjzFocusLogger.fWarn {
                "wjzFocus internal state repair incomplete: reason=$reason, " +
                        "violations=${remainingViolations.joinToString()}"
            }
        }
    }

    private fun repairInternalState(reason: String) {
        checkMainThread()
        val now = SystemClock.uptimeMillis()
        var changed = false
        var pendingChanged = false

        entries.values.forEach { entry ->
            entryOrders.getOrPut(entry.node.id) { entryOrderSeed++ }
        }
        val removedStaleOrders = entryOrders.keys.removeAll { it !in entries }
        if (removedStaleOrders || !entryIndexesMatchEntries()) {
            rebuildEntryIndexes()
            changed = true
        }

        focusedSnapshotByLayerScope.keys.toList().forEach { key ->
            val snapshot = focusedSnapshotByLayerScope[key] ?: return@forEach
            val nodeId = snapshot.nodeId ?: return@forEach
            val entry = entries[nodeId]
            if (entry == null ||
                entry.node.layer != key.layer ||
                entry.node.scopeId != key.scopeId ||
                !entry.mounted ||
                !entry.hasFocus
            ) {
                updateFocusedSnapshot(key.layer, key.scopeId, null)
                changed = true
            }
        }

        focusedLeafSnapshotByLayerScope.keys.toList().forEach { key ->
            val snapshot = focusedLeafSnapshotByLayerScope[key] ?: return@forEach
            val nodeId = snapshot.nodeId ?: return@forEach
            val entry = entries[nodeId]
            if (entry == null ||
                entry.node.layer != key.layer ||
                entry.node.scopeId != key.scopeId ||
                !entry.mounted ||
                !entry.hasFocus ||
                entry.node.kind != WjzFocusNodeKind.Leaf
            ) {
                updateFocusedLeafSnapshot(key.layer, key.scopeId, null)
                changed = true
            }
        }

        recentFocusByLayer.entries.toList().forEach { (layer, records) ->
            val removed = records.removeAll { record -> !isValidRecentRecord(layer, record) }
            while (records.size > MaxRecentFocusRecords) {
                records.removeFirst()
                changed = true
            }
            if (removed || records.isEmpty()) changed = true
            if (records.isEmpty()) recentFocusByLayer.remove(layer)
        }

        val invalidSources = sourceStack.filter { source ->
            val nodeId = source.nodeId ?: return@filter false
            !isValidSourceNode(source.layer, source.scopeId, nodeId)
        }
        if (invalidSources.isNotEmpty()) {
            sourceStack.removeAll(invalidSources.toSet())
            changed = true
            wjzFocusLogger.fWarn {
                "wjzFocus source history repaired: reason=$reason, removed=${invalidSources.size}, " +
                        "sources=${invalidSources.joinToString { source ->
                            "${source.layer}:${source.scopeId?.value}:${source.nodeId?.value}:${source.token.value}"
                        }}"
            }
        }

        val removedPending = pendingRequests.removeAll { request ->
            isInvalidPendingRequest(request, now)
        }
        if (removedPending) {
            changed = true
            pendingChanged = true
        }

        val removedExpiredLocks = lockTokens.removeAll { it.expiresAtUptimeMillis <= now }
        if (removedExpiredLocks) {
            changed = true
            if (lockTokens.isEmpty()) {
                lockWatchdogJob?.cancel()
                lockWatchdogJob = null
            } else {
                scheduleLockWatchdog()
            }
        }
        val lockedDirection = lockedDirectionIntent
        if (lockedDirection != null &&
            (lockedDirection.deadlineUptimeMillis <= now || lockedDirection.layer != activeLayer)
        ) {
            lockedDirectionIntent = null
            changed = true
        }

        val removedPendingAfterLockRepair = pendingRequests.removeAll { request ->
            isInvalidPendingRequest(request, now)
        }
        if (removedPendingAfterLockRepair) {
            changed = true
            pendingChanged = true
        }

        val fallback = validGlobalFallbackEntry()
        if (globalFallbackNodeId != fallback?.node?.id || globalFallbackGeneration != fallback?.generation) {
            globalFallbackNodeId = fallback?.node?.id
            globalFallbackGeneration = fallback?.generation
            changed = true
        }

        if (pendingChanged) notifyPendingChanged()
        if (changed) {
            wjzFocusLogger.fWarn { "wjzFocus internal state repaired: reason=$reason" }
        }
    }

    private fun collectInternalStateViolations(): List<String> {
        val now = SystemClock.uptimeMillis()
        val violations = mutableListOf<String>()

        entries.values.forEach { entry ->
            if (entry.node.fallback && entry.node.kind != WjzFocusNodeKind.Leaf) {
                violations += "fallback-container:${entry.node.id.value}"
            }
            if (entry.node.globalFallback && entry.node.kind != WjzFocusNodeKind.Leaf) {
                violations += "globalFallback-container:${entry.node.id.value}"
            }
            if (entry.node.id !in entryOrders) {
                violations += "missing-entry-order:${entry.node.id.value}"
            }
        }
        entryOrders.keys.forEach { nodeId ->
            if (nodeId !in entries) violations += "stale-entry-order:${nodeId.value}"
        }
        if (!entryIndexesMatchEntries()) {
            violations += "entry-index-mismatch"
        }

        focusedSnapshotByLayerScope.forEach { (key, snapshot) ->
            val nodeId = snapshot.nodeId ?: return@forEach
            val entry = entries[nodeId]
            if (entry == null ||
                entry.node.layer != key.layer ||
                entry.node.scopeId != key.scopeId ||
                !entry.mounted ||
                !entry.hasFocus
            ) {
                violations += "snapshot-invalid:${key.layer}:${key.scopeId?.value}:${nodeId.value}"
            }
        }
        focusedLeafSnapshotByLayerScope.forEach { (key, snapshot) ->
            val nodeId = snapshot.nodeId ?: return@forEach
            val entry = entries[nodeId]
            if (entry == null ||
                entry.node.layer != key.layer ||
                entry.node.scopeId != key.scopeId ||
                !entry.mounted ||
                !entry.hasFocus ||
                entry.node.kind != WjzFocusNodeKind.Leaf
            ) {
                violations += "leaf-snapshot-invalid:${key.layer}:${key.scopeId?.value}:${nodeId.value}"
            }
        }

        recentFocusByLayer.forEach { (layer, records) ->
            if (records.size > MaxRecentFocusRecords) {
                violations += "recent-overflow:$layer:${records.size}"
            }
            records.forEach { record ->
                if (!isValidRecentRecord(layer, record)) {
                    violations += "recent-invalid:$layer:${record.scopeId?.value}:${record.nodeId.value}"
                }
            }
        }
        sourceStack.forEach { source ->
            val nodeId = source.nodeId ?: return@forEach
            if (!isValidSourceNode(source.layer, source.scopeId, nodeId)) {
                violations += "source-invalid:${source.layer}:${source.scopeId?.value}:${nodeId.value}"
            }
        }
        pendingRequests.forEach { request ->
            if (isInvalidPendingRequest(request, now)) {
                violations += "pending-invalid:${request.intent}"
            }
        }
        lockTokens.forEach { record ->
            if (record.expiresAtUptimeMillis <= now) violations += "lock-expired"
        }
        lockedDirectionIntent?.let { intent ->
            if (intent.deadlineUptimeMillis <= now) violations += "locked-direction-expired"
            if (intent.layer != activeLayer) violations += "locked-direction-layer:${intent.layer}:$activeLayer"
        }

        val fallbackNodeId = globalFallbackNodeId
        val fallbackGeneration = globalFallbackGeneration
        if (fallbackNodeId == null) {
            if (fallbackGeneration != null) violations += "globalFallback-generation-without-node"
        } else {
            val entry = entries[fallbackNodeId]
            if (entry == null ||
                entry.generation != fallbackGeneration ||
                !entry.node.globalFallback ||
                entry.node.kind != WjzFocusNodeKind.Leaf ||
                !entry.mounted
            ) {
                violations += "globalFallback-invalid:${fallbackNodeId.value}"
            }
        }

        return violations
    }

    private fun shouldFailFastInternalStateValidation(): Boolean {
        return BuildConfig.DEBUG || BuildConfig.BUILD_TYPE.equals("r8Test", ignoreCase = true)
    }

    private fun entryIndexesMatchEntries(): Boolean {
        if (entriesByLayer.values.sumOf { it.size } != entries.size) return false
        if (entriesByLayerScope.values.sumOf { it.size } != entries.size) return false
        entries.forEach { (nodeId, entry) ->
            if (entriesByLayer[entry.node.layer]?.get(nodeId) !== entry) return false
            if (entriesByLayerScope[entry.layerScopeKey]?.get(nodeId) !== entry) return false
        }
        entriesByLayer.forEach { (layer, layerEntries) ->
            layerEntries.forEach { (nodeId, entry) ->
                if (entry.node.layer != layer || entries[nodeId] !== entry) return false
            }
        }
        entriesByLayerScope.forEach { (key, scopeEntries) ->
            scopeEntries.forEach { (nodeId, entry) ->
                if (entry.layerScopeKey != key || entries[nodeId] !== entry) return false
            }
        }
        return true
    }

    private fun rebuildEntryIndexes() {
        entriesByLayer.clear()
        entriesByLayerScope.clear()
        entries.values
            .sortedBy { entryOrders.getValue(it.node.id) }
            .forEach { entry ->
                entriesByLayer
                    .getOrPut(entry.node.layer) { linkedMapOf() }[entry.node.id] = entry
                entriesByLayerScope
                    .getOrPut(entry.layerScopeKey) { linkedMapOf() }[entry.node.id] = entry
            }
    }

    private fun isValidRecentRecord(layer: WjzFocusLayer, record: RecentFocusRecord): Boolean {
        val entry = entries[record.nodeId] ?: return true
        return entry.node.layer == layer &&
                entry.node.scopeId == record.scopeId &&
                entry.node.kind == WjzFocusNodeKind.Leaf
    }

    private fun isValidSourceNode(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?,
        nodeId: WjzFocusNodeId
    ): Boolean {
        val entry = entries[nodeId] ?: return true
        if (!entry.mounted) return true
        return entry.node.layer == layer &&
                entry.node.scopeId == scopeId &&
                entry.node.kind == WjzFocusNodeKind.Leaf
    }

    private fun isInvalidPendingRequest(request: WjzFocusPendingRequest, now: Long): Boolean {
        if (!isFocusLocked && request.deadlineUptimeMillis <= now) return true
        return when (val intent = request.intent) {
            is WjzFocusIntent.RequestNode -> {
                val entry = entries[intent.nodeId] ?: return intent.expectedGeneration != null
                entry.node.kind != WjzFocusNodeKind.Leaf ||
                        entry.node.layer != intent.layer ||
                        (intent.scopeId != null && entry.node.scopeId != intent.scopeId) ||
                        (intent.expectedGeneration != null && entry.generation != intent.expectedGeneration)
            }
            is WjzFocusIntent.RestoreLayer -> false
            is WjzFocusIntent.RestoreDisabledScope -> false
            is WjzFocusIntent.RestoreGroup -> {
                val entry = entries[intent.nodeId]
                val invalidTarget = entry != null &&
                        (entry.node.kind != WjzFocusNodeKind.Leaf ||
                                entry.node.layer != intent.layer ||
                                (intent.scopeId != null && entry.node.scopeId != intent.scopeId))
                val fallbackNodeId = intent.fallbackNodeId
                val invalidFallback = fallbackNodeId != null &&
                        entries[fallbackNodeId]?.let { fallback ->
                            fallback.node.kind != WjzFocusNodeKind.Leaf ||
                                    fallback.node.layer != intent.layer ||
                                    (intent.scopeId != null && fallback.node.scopeId != intent.scopeId)
                        } == true
                invalidTarget || invalidFallback
            }
            is WjzFocusIntent.RestoreLazyItem -> {
                val entry = entries[intent.nodeId] ?: return false
                entry.node.kind != WjzFocusNodeKind.Leaf ||
                        entry.node.layer != intent.layer ||
                        (intent.scopeId != null && entry.node.scopeId != intent.scopeId)
            }
        }
    }

    private fun validGlobalFallbackEntry(): WjzFocusEntry? {
        val current = globalFallbackNodeId
            ?.let { entries[it] }
            ?.takeIf {
                it.generation == globalFallbackGeneration &&
                        it.node.globalFallback &&
                        it.node.kind == WjzFocusNodeKind.Leaf &&
                        it.mounted &&
                        !isDisabledEntry(it)
            }
        return current ?: entries.values.lastOrNull {
            it.node.globalFallback &&
                    it.node.kind == WjzFocusNodeKind.Leaf &&
                    it.mounted &&
                    !isDisabledEntry(it)
        }
    }

    private fun recordRecentFocus(
        layer: WjzFocusLayer,
        nodeId: WjzFocusNodeId,
        scopeId: WjzFocusScopeId?
    ) {
        val records = recentFocusByLayer.getOrPut(layer) { ArrayDeque() }
        records.removeAll { it.nodeId == nodeId }
        records.addLast(
            RecentFocusRecord(
                nodeId = nodeId,
                scopeId = scopeId
            )
        )
        while (records.size > MaxRecentFocusRecords) {
            records.removeFirst()
        }
    }

    private fun findRecentFocusNode(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId? = null,
        allowAnyScope: Boolean = false
    ): WjzFocusLeafEntry? {
        val records = recentFocusByLayer[layer] ?: return null
        for (index in records.indices.reversed()) {
            val record = records[index]
            if (!allowAnyScope && record.scopeId != scopeId) continue

            val entry = entries[record.nodeId] ?: continue
            if (!entry.isRecentFocusCandidateFor(layer, scopeId, allowAnyScope)) continue

            return entry.asLeafEntryFor(layer, scopeId)
        }
        return null
    }

    private fun updateFocusedSnapshot(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?,
        nodeId: WjzFocusNodeId?
    ) {
        val key = WjzFocusLayerScopeKey(layer, scopeId)
        val previous = focusedSnapshotByLayerScope[key]
        if (previous?.nodeId == nodeId) return
        focusedSnapshotByLayerScope[key] = WjzFocusedNodeSnapshot(
            nodeId = nodeId,
            version = (previous?.version ?: 0) + 1
        )
    }

    private fun updateFocusedLeafSnapshot(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?,
        nodeId: WjzFocusNodeId?
    ) {
        val key = WjzFocusLayerScopeKey(layer, scopeId)
        val previous = focusedLeafSnapshotByLayerScope[key]
        if (previous?.nodeId == nodeId) return
        focusedLeafSnapshotByLayerScope[key] = WjzFocusedNodeSnapshot(
            nodeId = nodeId,
            version = (previous?.version ?: 0) + 1
        )
    }

    private fun clearFocusedSnapshotIfMatches(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?,
        nodeId: WjzFocusNodeId
    ) {
        val key = WjzFocusLayerScopeKey(layer, scopeId)
        val snapshot = focusedSnapshotByLayerScope[key] ?: return
        if (snapshot.nodeId != nodeId) return
        val replacementNodeId = entriesInLayerScope(layer, scopeId).lastOrNull { entry ->
            entry.node.id != nodeId &&
                    entry.hasFocus &&
                    entry.isInLayerScope(layer, scopeId) &&
                    !isDisabledEntry(entry)
        }?.node?.id
        updateFocusedSnapshot(
            layer = layer,
            scopeId = scopeId,
            nodeId = replacementNodeId
        )
    }

    private fun clearFocusedLeafSnapshotIfMatches(
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?,
        nodeId: WjzFocusNodeId
    ) {
        val key = WjzFocusLayerScopeKey(layer, scopeId)
        val snapshot = focusedLeafSnapshotByLayerScope[key] ?: return
        if (snapshot.nodeId != nodeId) return
        val replacementNodeId = entriesInLayerScope(layer, scopeId).lastOrNull { entry ->
            entry.node.id != nodeId &&
                    entry.isSourceCandidateFor(layer, scopeId)
        }?.node?.id
        updateFocusedLeafSnapshot(
            layer = layer,
            scopeId = scopeId,
            nodeId = replacementNodeId
        )
    }

    /** 丢弃已过期 pending 请求。锁定期间不丢弃，等待解锁后统一处理。 */
    private fun dropExpiredPendingRequests() {
        checkMainThread()
        if (isFocusLocked) return

        val now = SystemClock.uptimeMillis()
        // 节流：高频读路径会反复调用，间隔过短直接跳过，避免 removeAll 触发空 snapshot 写。
        if (now - lastDropExpiredUptimeMillis < DropExpiredThrottleMillis) return
        lastDropExpiredUptimeMillis = now
        val changed = pendingRequests.removeAll { it.deadlineUptimeMillis <= now }
        if (changed) notifyPendingChanged()
    }

    private fun logFocusRequestSkipped(
        reason: String,
        nodeId: WjzFocusNodeId,
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?,
        expectedGeneration: Int?,
        entry: WjzFocusEntry? = entries[nodeId],
        result: WjzFocusRequestResult
    ) {
        wjzFocusLogger.fInfo {
            "focus request skipped: reason=$reason, result=$result, nodeId=${nodeId.value}, " +
                    "targetLayer=$layer, activeLayer=$activeLayer, " +
                    "targetScope=${scopeId?.value}, nodeScope=${entry?.node?.scopeId?.value}, " +
                    "nodeLayer=${entry?.node?.layer}, mounted=${entry?.mounted}, " +
                    "generation=${entry?.generation}, expectedGeneration=$expectedGeneration, " +
                    "pending=${pendingRequests.size}, locked=$isFocusLocked"
        }
    }

    /** 锁定期间延长 pending 请求，避免动画、过渡导致请求过早过期。 */
    private fun extendLockedPendingRequests() {
        checkMainThread()
        if (!isFocusLocked) return

        val now = SystemClock.uptimeMillis()
        val extendedDeadline = now + LockedPendingTtlMillis
        for (i in pendingRequests.indices) {
            val request = pendingRequests[i]
            if (request.deadlineUptimeMillis < extendedDeadline) {
                pendingRequests[i] = request.copy(deadlineUptimeMillis = extendedDeadline)
            }
        }
        notifyPendingChanged()
    }

    /** 只延长某个指定 intent 的 pending 请求。 */
    private fun extendPendingRequest(intent: WjzFocusIntent, @Suppress("SameParameterValue") ttlMillis: Long) {
        checkMainThread()
        val now = SystemClock.uptimeMillis()
        for (i in pendingRequests.indices) {
            val request = pendingRequests[i]
            if (request.intent == intent) {
                pendingRequests[i] = request.copy(deadlineUptimeMillis = now + ttlMillis)
            }
        }
        notifyPendingChanged()
    }

    /** 按 request id 删除指定 pending，避免挂起恢复期间误删后续入队的等值 intent。 */
    private fun removePendingRequest(id: Long): Boolean {
        return pendingRequests.removeAll { request -> request.id == id }
    }

    /** 统一刷新 pending 队列的截止时间。 */
    private fun refreshPendingRequests(@Suppress("SameParameterValue") ttlMillis: Long) {
        checkMainThread()
        if (pendingRequests.isEmpty()) return

        val deadline = SystemClock.uptimeMillis() + ttlMillis
        for (i in pendingRequests.indices) {
            pendingRequests[i] = pendingRequests[i].copy(deadlineUptimeMillis = deadline)
        }
        notifyPendingChanged()
    }

    /** 只对 group/lazy restorer 发定向门铃；pending 队列仍是唯一事实来源。 */
    private fun notifyPendingChanged(intent: WjzFocusIntent? = null) {
        checkMainThread()
        val wakeup = intent?.pendingWakeup ?: return
        _pendingWakeupSignals.tryEmit(wakeup)
    }

    /** 只唤醒等待该 nodeId 的恢复协程，避免任意节点挂载都触发全局重算。 */
    private fun notifyMountChanged(nodeId: WjzFocusNodeId) {
        _nodeMountWakeupSignals.tryEmit(nodeId)
    }
}

/** coordinator 内保存的节点运行时状态。 */
private data class WjzFocusEntry(
    val node: WjzFocusNode,
    /** 本次组合生命周期的版本号，用于拒绝旧 pending 请求。 */
    val generation: Int,
    /** 节点是否处于已注册/已挂载状态。 */
    val mounted: Boolean,
    /** 节点是否已经完成布局，能够安全执行 requestFocus。 */
    val placed: Boolean,
    /** 节点在当前 root 内的布局区域，用于 WjzFocus 内部禁用区域过滤。 */
    val bounds: Rect?,
    /** 节点所在 disabled focus 运行时上下文，用于隔离同屏多实例和层级覆盖。 */
    val disabledFocusContext: WjzDisabledFocusContext = WjzDisabledFocusContext(),
    /** 是否已经由 applyFocusProperties 安装过方向处理入口；router 命中仍在消费时现场遍历确认。 */
    val routingReady: Boolean = false,
    /**
     * 节点当前是否持有焦点的派生缓存。
     *
     * 它只由 [WjzFocusCoordinator.updateFocus] 从 Compose FocusState 同步，
     * 用于 WjzFocus 最近焦点、fallback、source、restore 协议选择候选。
     * 真正执行请求时必须重新校验节点存在、active layer、禁用区域、requester 结果和 generation。
     */
    val hasFocus: Boolean = false
) {
    val layerScopeKey: WjzFocusLayerScopeKey
        get() = WjzFocusLayerScopeKey(node.layer, node.scopeId)

    /**
     * 判断节点是否可在指定 layer 、scope 中请求。
     *
     * scope 为 null 时表示通配该 layer 内任意 scope，这是兼容 、全局语义，
     * 新代码能明确目标 scope 时应传具体 scope。
     */
    fun isRequestableIn(layer: WjzFocusLayer, scopeId: WjzFocusScopeId?): Boolean {
        return mounted &&
                placed &&
                node.layer == layer &&
                (scopeId == null || node.scopeId == scopeId)
    }

    /** 判断节点是否严格属于指定 layer 、scope。scope 必须相等，不做通配。 */
    fun isInLayerScope(layer: WjzFocusLayer, scopeId: WjzFocusScopeId?): Boolean {
        return mounted &&
                node.layer == layer &&
                node.scopeId == scopeId
    }

    /** 判断节点是否是指定 layer 、scope 内可直接 requestFocus 的末端节点。 */
    fun isRequestFocusLeafIn(layer: WjzFocusLayer, scopeId: WjzFocusScopeId?): Boolean {
        return isRequestableIn(layer, scopeId) &&
                node.kind == WjzFocusNodeKind.Leaf
    }

    /** 判断节点所属 layer 是否就是当前 active layer。 */
    fun canRequestFocus(activeLayer: WjzFocusLayer): Boolean {
        return mounted && placed && node.layer == activeLayer
    }
}

/** 已经通过 direct leaf 资格校验的 entry，只在 source/recent/restore 候选路径内部流转。 */
private data class WjzFocusLeafEntry(
    private val entry: WjzFocusEntry
) {
    val node: WjzFocusNode
        get() = entry.node

    val generation: Int
        get() = entry.generation

    val routingReady: Boolean
        get() = entry.routingReady
}

internal data class WjzFocusedNodeSnapshot(
    val nodeId: WjzFocusNodeId?,
    val version: Int
)

private data class WjzFocusLayerScopeKey(
    val layer: WjzFocusLayer,
    val scopeId: WjzFocusScopeId?
)

/** 每个 layer 内最近成功持焦点的节点记录。 */
private data class RecentFocusRecord(
    val nodeId: WjzFocusNodeId,
    val scopeId: WjzFocusScopeId?
)

/** 打开 Dialog/Overlay 等临时 layer 前记录的来源焦点。 */
private data class WjzFocusSource(
    val layer: WjzFocusLayer,
    val scopeId: WjzFocusScopeId?,
    val nodeId: WjzFocusNodeId?,
    val token: WjzFocusSourceToken
)

/** pending 队列中的一条请求及其过期时间。 */
private data class WjzFocusPendingRequest(
    val id: Long,
    val intent: WjzFocusIntent,
    val deadlineUptimeMillis: Long,
    val submitDedupeKey: Any? = null
) {
    /** 该请求目标 layer。 */
    val layer: WjzFocusLayer
        get() = intent.layer

    /** 该请求目标 scope。 */
    val scopeId: WjzFocusScopeId?
        get() = intent.scopeId

    /** 该请求目标 node，恢复 layer 请求没有具体 node。 */
    val nodeId: WjzFocusNodeId?
        get() = when (intent) {
            is WjzFocusIntent.RequestNode -> intent.nodeId
            is WjzFocusIntent.RestoreDisabledScope -> null
            is WjzFocusIntent.RestoreGroup -> intent.nodeId
            is WjzFocusIntent.RestoreLazyItem -> intent.nodeId
            is WjzFocusIntent.RestoreLayer -> null
        }
}

/** 当前锁 token 的运行时记录。 */
private data class WjzFocusLockRecord(
    val token: Any,
    val expiresAtUptimeMillis: Long
)

/** 锁定期 coalesce 后的最后一次方向意图。 */
private data class WjzFocusLockedDirectionIntent(
    val direction: FocusDirection,
    val layer: WjzFocusLayer,
    val deadlineUptimeMillis: Long
)

/** Host 级出口运行时记录。 */
private data class WjzFocusHostExitRecord(
    val scopeId: WjzFocusScopeId,
    val exits: List<WjzFocusHostExit>
)

/** 节点连续失败的运行时记录，带最后失败时间用于按时间窗口清零。 */
private data class WjzFocusFailureRecord(
    val count: Int,
    val lastFailureUptimeMillis: Long
)

/** 已注册节点的焦点属性失效回调，generation 用于过滤旧 Modifier.Node。 */
private data class WjzFocusPropertiesInvalidatorRecord(
    val generation: Int,
    val invalidator: () -> Unit
)

private data class WjzDisabledFocusRegionRecord(
    val bounds: Rect,
    val group: Any?,
    val zIndex: Float
)

/** disabled-region 触发后的严格恢复窗口，只允许当前 layer + 当前 scope 先完成恢复。 */
private data class WjzFocusStrictRestoreWindow(
    val layer: WjzFocusLayer,
    val scopeId: WjzFocusScopeId?
) {
    fun matches(layer: WjzFocusLayer, scopeId: WjzFocusScopeId?): Boolean {
        return this.layer == layer && this.scopeId == scopeId
    }
}

/** 严格恢复窗口的一次消费结果。 */
private enum class WjzFocusStrictRestoreWindowResult {
    Inactive,
    Restored,
    Blocked
}

/** 普通焦点组恢复器注册 key，用于区分不同 layer 、scope 、list。 */
private data class WjzFocusGroupRestorerKey(
    val layer: WjzFocusLayer,
    val scopeId: WjzFocusScopeId?,
    val restorerId: String,
    val listId: String
)

/** Lazy 恢复器注册 key，用于区分不同 layer 、scope 、list。 */
private data class WjzFocusLazyRestorerKey(
    val layer: WjzFocusLayer,
    val scopeId: WjzFocusScopeId?,
    val restorerId: String,
    val listId: String
)

/** pending 去重 key，同一目标的新请求会替换旧请求。 */
private val WjzFocusIntent.dedupeKey: Any
    get() = when (this) {
        is WjzFocusIntent.RequestNode -> WjzFocusRequestDedupeKey(
            nodeId = nodeId,
            layer = layer,
            scopeId = scopeId
        )
        is WjzFocusIntent.RestoreLayer -> "restore-layer-$layer-${scopeId?.value}"
        is WjzFocusIntent.RestoreDisabledScope ->
            "restore-disabled-scope-$layer-${scopeId?.value}"
        is WjzFocusIntent.RestoreGroup -> WjzFocusGroupDedupeKey(
            nodeId = nodeId,
            layer = layer,
            scopeId = scopeId,
            restorerId = restorerId,
            listId = listId,
            fallbackNodeId = fallbackNodeId
        )
        is WjzFocusIntent.RestoreLazyItem -> WjzFocusLazyDedupeKey(
            nodeId = nodeId,
            itemKey = itemKey,
            layer = layer,
            scopeId = scopeId,
            restorerId = restorerId,
            listId = listId
        )
    }

private val WjzFocusIntent.pendingWakeup: WjzFocusPendingWakeup?
    get() = when (this) {
        is WjzFocusIntent.RestoreGroup -> WjzFocusPendingWakeup(
            layer = layer,
            scopeId = scopeId,
            restorerId = restorerId,
            listId = listId,
            kind = WjzFocusPendingWakeupKind.Group
        )
        is WjzFocusIntent.RestoreLazyItem -> WjzFocusPendingWakeup(
            layer = layer,
            scopeId = scopeId,
            restorerId = restorerId,
            listId = listId,
            kind = WjzFocusPendingWakeupKind.Lazy
        )
        else -> null
    }

/** 普通焦点组恢复 pending 的去重 key。 */
private data class WjzFocusGroupDedupeKey(
    val nodeId: WjzFocusNodeId,
    val layer: WjzFocusLayer,
    val scopeId: WjzFocusScopeId?,
    val restorerId: String,
    val listId: String,
    val fallbackNodeId: WjzFocusNodeId?
)

/** Lazy 恢复 pending 的去重 key。 */
private data class WjzFocusLazyDedupeKey(
    val nodeId: WjzFocusNodeId,
    val itemKey: WjzFocusItemKey,
    val layer: WjzFocusLayer,
    val scopeId: WjzFocusScopeId?,
    val restorerId: String,
    val listId: String
)

/** 普通 node 请求 pending 的去重 key。 */
private data class WjzFocusRequestDedupeKey(
    val nodeId: WjzFocusNodeId,
    val layer: WjzFocusLayer,
    val scopeId: WjzFocusScopeId?
)

/** 当前组合树中的焦点协调器，没有 Host 时为 null。 */
val LocalWjzFocusCoordinator = compositionLocalOf<WjzFocusCoordinator?> { null }
/** 当前组合树中的默认焦点 scope，由 [WjzFocusHost] 提供，用于给节点补齐最近的模块边界。 */
val LocalWjzFocusScopeId = compositionLocalOf<WjzFocusScopeId?> { null }

private fun WjzFocusSavedState.rootRestoredActiveLayer(): WjzFocusLayer {
    if (!activeLayer.isTemporaryRootLayer) return activeLayer
    return sourceStack
        .asReversed()
        .firstOrNull { !it.layer.isTemporaryRootLayer }
        ?.layer
        ?: WjzFocusLayer.Content
}

private fun WjzFocusSavedState.rootRestoredSourceStack(): List<WjzFocusSavedSource> {
    return sourceStack.filter { source -> !source.layer.isTemporaryRootLayer }
}

private val WjzFocusLayer.isTemporaryRootLayer: Boolean
    get() = when (this) {
        WjzFocusLayer.Dialog,
        WjzFocusLayer.Keyboard,
        WjzFocusLayer.Overlay -> true
        WjzFocusLayer.Content,
        WjzFocusLayer.TopNav,
        WjzFocusLayer.Drawer,
        WjzFocusLayer.Player,
        WjzFocusLayer.Action -> false
    }

/** 创建并 remember 一个 [WjzFocusCoordinator]，供某个焦点区域或独立窗口复用。 */
@Composable
fun rememberWjzFocusCoordinator(
    initialSavedState: WjzFocusSavedState? = null,
    restoreAsRoot: Boolean = false
): WjzFocusCoordinator {
    val coordinator = remember {
        WjzFocusCoordinator().apply {
            initialSavedState?.let { savedState ->
                if (restoreAsRoot) {
                    restoreRootState(savedState)
                } else {
                    restoreState(savedState)
                }
            }
        }
    }
    DisposableEffect(coordinator) {
        onDispose {
            coordinator.dispose()
        }
    }
    return coordinator
}
