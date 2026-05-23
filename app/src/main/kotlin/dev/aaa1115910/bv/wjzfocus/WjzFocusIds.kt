package dev.aaa1115910.bv.wjzfocus

private const val WjzFocusScopeNodeSeparator = "|"
private const val WjzFocusEncodedItemEntryPrefix = "__wjzfocusencodeitem__"
private val WjzFocusHexChars = "0123456789ABCDEF".toCharArray()

/**
 * 焦点系统内稳定的节点 id，表示最终执行焦点请求的具体目标。
 *
 * 这是 coordinator 查表使用的完整 id。业务如果直接构造 [WjzFocusNodeId]，scope 不会自动参与拼接；
 * 需要 scope 内相对 id 时应使用 [WjzFocusLocalId]，再通过 [WjzFocusScopeId.resolve] 生成完整 node id。
 */
data class WjzFocusNodeId(
    val value: String
)

/**
 * 焦点模块/Host 的作用域 id，用于表达模块边界和精确寻址范围。
 *
 * 调用方可以混用 `/` 与 `\`，构造时会统一归一化为 `/`。scope id 允许用 `/`
 * 表达层级，例如 `settings/dialog`，但不允许包含连续 `//`、尾随 `/` 或 scope/local
 * 专用分隔符 `|`。前导 `/` 不在 scope 层禁止；真正拼接节点时仍会用 `|` 明确边界。
 * scope 与 local 拼接成完整 node id 时使用 `scope|local`，避免日志中
 * 无法区分 scope 边界和 local 层级。
 */
class WjzFocusScopeId(rawValue: String) {
    val value: String = normalizeWjzFocusPath(rawValue).also { normalized ->
        validateWjzFocusScopeId(normalized)
    }

    override fun equals(other: Any?): Boolean {
        return other is WjzFocusScopeId && value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value
}

/**
 * scope 内相对节点 id。
 *
 * [WjzFocusLocalId] 只描述当前 [WjzFocusScopeId] 内部的相对路径，例如 `tabs/selected`。
 * 它不是 coordinator 查表用的完整 [WjzFocusNodeId]，不能直接替代完整 node id 使用。
 *
 * local id 允许用 `/` 表达 scope 内层级，但不允许为空、首尾带 `/`、包含连续 `/` 或包含 `|`。
 * 完整 node id 的 scope/local 边界只由 [WjzFocusScopeId.resolve] 写入的 `|` 表示。
 */
class WjzFocusLocalId(rawValue: String) {
    val value: String = normalizeWjzFocusPath(rawValue).also { normalized ->
        validateWjzFocusLocalId(normalized)
    }

    override fun equals(other: Any?): Boolean {
        return other is WjzFocusLocalId && value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value
}

/**
 * 创建 scope 内相对节点 id。
 *
 * [parts] 会用 `/` 拼成一个 local path。每个 part 都可以是业务稳定 key，但最终结果仍必须是
 * 非空、无首尾 `/`、无连续 `/` 的相对路径。
 */
fun wjzFocusLocalId(vararg parts: Any): WjzFocusLocalId {
    require(parts.isNotEmpty()) { "wjz focus local id parts must not be empty" }
    return WjzFocusLocalId(parts.joinToString("/") { it.toString() })
}

/**
 * 把 scope 内相对 id 解析成 coordinator 查表使用的完整 node id。
 *
 * 只有显式使用 [WjzFocusLocalId] 的 local API 才会在当前 scope 下 resolve。
 * [localId] 已在构造时禁止 `|`，因此这里生成的 `scope|local` 边界始终唯一可拆。
 */
fun WjzFocusScopeId.resolve(localId: WjzFocusLocalId): WjzFocusNodeId {
    return WjzFocusNodeId("$value$WjzFocusScopeNodeSeparator${localId.value}")
}

/**
 * 如果完整 node id 属于指定 scope，转成该 scope 内的相对 local id。
 *
 * 这是 [WjzFocusScopeId.resolve] 的反向 typed helper，用于调用方需要在 Local/Node
 * 注册路径之间切换时避免直接拆 [WjzFocusNodeId.value]。
 */
fun WjzFocusNodeId.toLocalIdOrNull(scopeId: WjzFocusScopeId?): WjzFocusLocalId? {
    val scope = scopeId ?: return null
    val scopePrefix = "${scope.value}$WjzFocusScopeNodeSeparator"
    if (!value.startsWith(scopePrefix)) return null
    return WjzFocusLocalId(value.substring(scopePrefix.length))
}

/**
 * 生成 entry/default provider 可返回的目标。
 *
 * 这个 helper 与 local 注册 API 使用同一套 [resolve] 规则，确保 entry target 和真实注册 node id 以相同方式从 scope + local id 得到。
 * 返回目标默认落在 [WjzFocusLayer.Content]，scopeId 固定为当前 scope。
 */
fun WjzFocusScopeId.target(localId: WjzFocusLocalId): WjzFocusDefaultTarget {
    return WjzFocusDefaultTarget(
        nodeId = resolve(localId),
        scopeId = this
    )
}

/** 将用户输入里的 Windows 风格路径分隔符统一成 WjzFocus 内部使用的 `/`。 */
private fun normalizeWjzFocusPath(rawValue: String): String {
    return rawValue.replace('\\', '/')
}

/**
 * scope/local/entry 共用的路径级校验。
 *
 * 该函数只处理通用路径规则；component/local entry 的“不能包含分隔符”等更强限制由各自类型完成。
 */
private fun validateWjzFocusPath(
    value: String,
    name: String,
    allowLeadingSlash: Boolean
) {
    require(value.isNotBlank()) { "wjz focus $name must not be blank" }
    require(WjzFocusScopeNodeSeparator !in value) {
        "wjz focus $name must not contain '$WjzFocusScopeNodeSeparator': $value"
    }
    require(allowLeadingSlash || !value.startsWith("/")) {
        "wjz focus $name must not start with '/': $value"
    }
    require(!value.endsWith("/")) {
        "wjz focus $name must not end with '/': $value"
    }
    require("//" !in value) { "wjz focus $name must not contain consecutive '/': $value" }
}

private fun validateWjzFocusScopeId(value: String) {
    validateWjzFocusPath(
        value = value,
        name = "scope id",
        allowLeadingSlash = true
    )
}

private fun validateWjzFocusLocalId(value: String) {
    validateWjzFocusPath(
        value = value,
        name = "local id",
        allowLeadingSlash = false
    )
}

/**
 * Lazy item 的稳定业务 key。
 *
 * 该 key 用于滚动后恢复焦点，不等价于完整 node id。调用方应保证同一 Lazy 容器内唯一。
 */
@JvmInline
value class WjzFocusItemKey(
    val value: String
) {
    override fun toString(): String = value
}

/**
 * 把业务 item key 编码成 WjzFocus 内部使用的安全 entry id。
 *
 * 业务 key 只表示恢复和滚动身份，允许包含 `/`、`\`、`|` 等协议分隔符；但
 * [WjzFocusTargetEntry.id] 属于 component 内 local entry id，不能包含这些字符。
 * 因此 Lazy/Grid/Scroll 这类 item 容器必须先把业务 key 的 UTF-8 字节转成十六进制，
 * 再加固定前缀，得到只包含英文字母、数字和前缀下划线的内部 entry id。
 *
 * 该编码不需要反解；原始业务 key 会继续保存在 [WjzFocusItemKey] 中用于滚动恢复。
 */
fun wjzFocusEncodeItemEntryId(rawKey: String): String {
    val bytes = rawKey.encodeToByteArray()
    return buildString(WjzFocusEncodedItemEntryPrefix.length + bytes.size * 2) {
        append(WjzFocusEncodedItemEntryPrefix)
        bytes.forEach { byte ->
            // Byte.toInt() 会保留符号位；先转成无符号 0..255，再拆成两个 hex 字符。
            val value = byte.toInt() and 0xFF
            append(WjzFocusHexChars[value ushr 4])
            append(WjzFocusHexChars[value and 0x0F])
        }
    }
}

/**
 * 统一生成列表/网格 item 的真实 nodeId。
 *
 * [listId] 表示容器身份，[itemEntryId] 是由业务 key 编码得到的内部安全 entry id。
 * 二者共同进入 nodeId，避免同一个业务 item 出现在多个列表/网格时互相覆盖注册记录。
 */
fun wjzFocusItemNodeId(
    listId: String,
    itemEntryId: String
): String {
    // itemEntryId 已经是编码后的安全 id，可以作为 local path 的最后一段参与拼接。
    return "$listId/item/$itemEntryId"
}

internal const val WjzFocusDefaultEntryName = "default"

private val WjzFocusEntrySeparators = charArrayOf('/', '\\', '|')

/**
 * 外部公开 entry 的组件 id。
 *
 * component id 是 [WjzFocusEntryId] 的第一段，只用于组件间通信，不能包含 `/`、`\` 或 `|`。
 * `default` 被保留给默认入口名，不能作为 component id。
 */
@JvmInline
value class WjzFocusComponentId(
    val value: String
) {
    init {
        require(value.isNotBlank()) { "wjz focus component id must not be blank" }
        require(WjzFocusEntrySeparators.none { it in value }) {
            "wjz focus component id must not contain '/', '\\' or '|'"
        }
        require(value != WjzFocusDefaultEntryName) {
            "wjz focus component id '$WjzFocusDefaultEntryName' is reserved"
        }
    }
}

/**
 * 外部公开 entry 的本地入口名。
 *
 * 它是 [WjzFocusEntryId] 的第二段。普通业务通过 `entry("xxx")` 暴露具名入口；
 * 默认入口由 [WjzFocusDefaultEntryName] 表示，不应手动构造同名 local entry。
 */
@JvmInline
value class WjzFocusLocalEntryId(
    val value: String
) {
    init {
        require(value.isNotBlank()) { "wjz focus local entry id must not be blank" }
        require(WjzFocusEntrySeparators.none { it in value }) {
            "wjz focus local entry id must not contain '/', '\\' or '|'"
        }
        require(value != WjzFocusDefaultEntryName) {
            "wjz focus local entry id '$WjzFocusDefaultEntryName' is reserved"
        }
    }
}

/**
 * 外部公开 entry id，格式为 `component/entry`。
 *
 * Entry id 服务组件间通信，不用于内部相邻移动。输入会先把 `\` 归一化为 `/`，
 * 然后校验不含 `|`、不含连续 `//`、不以 `/` 开头或结尾，并最终规范化为完整 `component/entry`。
 * [parse] 允许传入单段 `component`，会自动解析为 `component/default`。
 */
class WjzFocusEntryId(rawValue: String) {
    val value: String

    val componentId: WjzFocusComponentId
        get() = WjzFocusComponentId(splitEntryPath(value)[0])

    val localEntryValue: String
        get() = splitEntryPath(value).getOrNull(1) ?: WjzFocusDefaultEntryName

    init {
        val parts = splitEntryPath(rawValue)
        require(parts.size == 2) {
            "wjz focus entry id must be normalized full path 'component/entry': $rawValue"
        }
        val componentId = WjzFocusComponentId(parts[0])
        val localEntry = parts[1]
        if (localEntry != WjzFocusDefaultEntryName) {
            WjzFocusLocalEntryId(localEntry)
        }
        value = "${componentId.value}/$localEntry"
    }

    override fun equals(other: Any?): Boolean {
        return other is WjzFocusEntryId && value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        /** 解析公开 entry 路径；单段路径会指向该 component 的默认入口。 */
        fun parse(rawPath: String): WjzFocusEntryId {
            val parts = splitEntryPath(rawPath)
            val componentId = WjzFocusComponentId(parts[0])
            val localEntry = parts.getOrNull(1) ?: WjzFocusDefaultEntryName
            if (localEntry != WjzFocusDefaultEntryName) {
                WjzFocusLocalEntryId(localEntry)
            }
            return WjzFocusEntryId("${componentId.value}/$localEntry")
        }

        /** 构造指定 component 的默认入口 id。 */
        fun defaultOf(componentId: WjzFocusComponentId): WjzFocusEntryId {
            return WjzFocusEntryId("${componentId.value}/$WjzFocusDefaultEntryName")
        }
    }
}

/** 构造指定 component 下的具名 entry。 */
fun WjzFocusComponentId.entry(localEntryId: WjzFocusLocalEntryId): WjzFocusEntryId {
    return WjzFocusEntryId("$value/${localEntryId.value}")
}

/** 构造指定 component 的默认 entry。 */
fun WjzFocusComponentId.defaultEntry(): WjzFocusEntryId {
    return WjzFocusEntryId.defaultOf(this)
}

/**
 * 解析 entry path 并执行路径级校验。
 *
 * 这里不再把 `|` 当成路径分隔符，避免完整 node id 的 scope/local 分隔符和公开 entry 分隔符混用。
 */
private fun splitEntryPath(rawPath: String): List<String> {
    val normalized = normalizeWjzFocusPath(rawPath)
    validateWjzFocusPath(
        value = normalized,
        name = "entry path",
        allowLeadingSlash = false
    )
    val parts = normalized.split('/')
    require(parts.size in 1..2) {
        "wjz focus entry path must be 'component' or 'component/entry': $rawPath"
    }
    return parts
}

