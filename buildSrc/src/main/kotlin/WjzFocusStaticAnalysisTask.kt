import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class WjzFocusStaticAnalysisTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceRoots: ConfigurableFileCollection

    private val wjzFocusPath = path("dev/aaa1115910/bv/wjzfocus")
    private val componentPath = path("dev/aaa1115910/bv/component")
    private val coordinatorPath = path("dev/aaa1115910/bv/wjzfocus/WjzFocusCoordinator.kt")

    private val composeFocusImportPrefix = "androidx.compose.ui." + "focus."
    private val composeFocusImportAllowList = setOf("FocusDirection")
    private val forbiddenComposeFocusTokens = listOf(
        "Modifier." + "focusable(",
        "." + "focusable(",
        "Modifier." + "focusRequester(",
        "." + "focusRequester(",
        "Modifier." + "focusTarget()",
        "." + "focusTarget()",
        "Modifier." + "focusProperties",
        "." + "focusProperties",
        "Local" + "FocusManager",
        "Focus" + "Manager",
        "move" + "Focus(",
        "clear" + "Focus(",
        "request" + "Focus(",
        "capture" + "Focus("
    )
    private val forbiddenComposeOnFocusChangedPattern = Regex("""\.\s*onFocusChanged\b""")
    private val forbiddenComposeOnFocusChangedQualifiedPattern = Regex(
        """\bandroidx\.compose\.ui\.focus\.onFocusChanged\b"""
    )
    private val ordinaryOnFocusChangedReceivers = setOf("this", "super")
    private val modifierChainContextPatterns = listOf(
        Regex("""\bModifier\b"""),
        Regex("""\bmodifier\b"""),
        Regex("""\.\s*then\s*\("""),
        Regex("""\.\s*(?:padding|size|width|height|fillMaxWidth|fillMaxHeight|fillMaxSize|wrapContentSize)\s*\("""),
        Regex("""\.\s*(?:background|border|clip|alpha|graphicsLayer|drawBehind|drawWithContent)\s*\("""),
        Regex("""\.\s*(?:clickable|combinedClickable|selectable|toggleable|wjzFocus|wjzFocusRequest|wjzFocusGroup)\s*\(""")
    )
    private val lowLevelRequestTokens = listOf(
        "request" + "FocusDetailed(",
        "request" + "EntryFocusDetailed(",
        ".request" + "Focus("
    )
    private val lowLevelEnqueueTokens = listOf(
        "enqueue" + "RestoreLayer(",
        "enqueue" + "GroupRestore(",
        "enqueue" + "LazyRestore(",
        "enqueue" + "RequestFocus("
    )
    private val lowLevelFocusStateAccessPatterns = listOf(
        Regex("""\b(?:coordinator|focusCoordinator|wjzFocusCoordinator)\??\.hasFocus\s*\("""),
        Regex("""\b(?:entry|focusEntry|wjzFocusEntry)\??\.hasFocus\b""")
    )
    private val submitIntentTokens = listOf(
        "submit" + "NodeFocusIntent(",
        "submit" + "EntryFocusIntent("
    )

    @TaskAction
    fun run() {
        runFixtureSelfCheck()

        val files = sourceRoots.files
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .sortedBy { it.invariantSeparatorsPath }

        val violations = buildList {
            files.forEach { file ->
                val relativePath = file.relativeTo(project.rootDir).invariantSeparatorsPath
                val normalizedPath = relativePath.replace('/', File.separatorChar)
                val lines = file.readLines()
                val isWjzFocus = normalizedPath.contains(wjzFocusPath)
                val isTest = normalizedPath.isTestPath()

                if (!isWjzFocus) {
                    checkBusinessComposeFocusUsage(relativePath, lines)
                    checkWjzFocusLowLevelAccess(relativePath, lines)
                    if (!isTest) {
                        checkWjzFocusLowLevelFocusStateAccess(relativePath, lines)
                    }
                }

                if (normalizedPath.contains(componentPath)) {
                    checkComponentSubmitIntent(relativePath, lines)
                }

                if (normalizedPath.endsWith(coordinatorPath)) {
                    checkCoordinatorLeafStateWrites(relativePath, lines)
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("WjzFocus static analysis failed:")
                    violations.forEach { appendLine("- $it") }
                }
            )
        }
    }

    private fun MutableList<String>.checkBusinessComposeFocusUsage(
        relativePath: String,
        lines: List<String>
    ) {
        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("import $composeFocusImportPrefix")) {
                val importedName = trimmed.substringAfter(composeFocusImportPrefix).substringBefore(" ")
                if (importedName !in composeFocusImportAllowList) {
                    add(violation(relativePath, index, "业务/UI 代码禁止导入 Compose 原生焦点 API：$trimmed"))
                }
            }
            forbiddenComposeFocusTokens
                .filter { token -> line.contains(token) }
                .forEach { token ->
                    add(violation(relativePath, index, "业务/UI 代码禁止直接使用 Compose 原生焦点 API：$token"))
                }
            forbiddenComposeOnFocusChangedViolation(lines, index)?.let { token ->
                add(violation(relativePath, index, "业务/UI 代码禁止直接使用 Compose 原生焦点 API：$token"))
            }
        }
    }

    private fun forbiddenComposeOnFocusChangedViolation(lines: List<String>, lineIndex: Int): String? {
        val line = lines[lineIndex]
        if (forbiddenComposeOnFocusChangedQualifiedPattern.containsMatchIn(line)) {
            return "androidx.compose.ui.focus." + "onFocusChanged"
        }

        return forbiddenComposeOnFocusChangedPattern.findAll(line)
            .firstOrNull { matchResult ->
                val receiver = line.take(matchResult.range.first)
                    .trimEnd()
                    .takeLastWhile { it.isLetterOrDigit() || it == '_' }
                receiver !in ordinaryOnFocusChangedReceivers &&
                    hasModifierChainContext(lines, lineIndex, matchResult.range.first)
            }
            ?.let { "." + "onFocusChanged" }
    }

    private fun hasModifierChainContext(
        lines: List<String>,
        lineIndex: Int,
        matchStart: Int
    ): Boolean {
        val context = buildString {
            val firstLine = (lineIndex - 6).coerceAtLeast(0)
            for (index in firstLine..lineIndex) {
                val line = lines[index]
                appendLine(if (index == lineIndex) line.take(matchStart) else line)
            }
        }
        return modifierChainContextPatterns.any { pattern -> pattern.containsMatchIn(context) }
    }

    private fun MutableList<String>.checkWjzFocusLowLevelAccess(
        relativePath: String,
        lines: List<String>
    ) {
        lines.forEachIndexed { index, line ->
            lowLevelRequestTokens
                .filter { token -> line.contains(token) }
                .forEach { token ->
                    add(violation(relativePath, index, "WjzFocus 外禁止直接调用低层 request 入口：$token"))
                }
            lowLevelEnqueueTokens
                .filter { token -> line.contains(token) }
                .forEach { token ->
                    add(violation(relativePath, index, "WjzFocus 外禁止直接调用低层 pending/enqueue 入口：$token"))
                }
        }
    }

    private fun MutableList<String>.checkWjzFocusLowLevelFocusStateAccess(
        relativePath: String,
        lines: List<String>
    ) {
        lines.forEachIndexed { index, line ->
            lowLevelFocusStateAccessPatterns
                .filter { pattern -> pattern.containsMatchIn(line) }
                .forEach {
                    add(violation(relativePath, index, "WjzFocus 外禁止裸读/写低层 focus 状态：.hasFocus"))
                }
        }
    }

    private fun MutableList<String>.checkComponentSubmitIntent(
        relativePath: String,
        lines: List<String>
    ) {
        lines.forEachIndexed { index, line ->
            val submitToken = submitIntentTokens.firstOrNull { line.contains(it) } ?: return@forEachIndexed
            val callText = collectCallText(lines, index, submitToken)
            if (!callText.contains("intent =")) {
                add(violation(relativePath, index, "通用组件提交焦点意图必须显式传入命名参数 intent"))
            }
            if (isInitialFocusContext(lines, index) && !callText.contains("WjzFocusSubmitIntent.InitialEntry")) {
                add(violation(relativePath, index, "通用组件初始首焦必须提交 WjzFocusSubmitIntent.InitialEntry"))
            }
        }
    }

    private fun MutableList<String>.checkCoordinatorLeafStateWrites(
        relativePath: String,
        lines: List<String>
    ) {
        var currentFunction: String? = null
        val allowedFunctions = setOf(
            "restoreStateInternal",
            "repairInternalState",
            "updateFocus",
            "activateLayer",
            "recordRecentFocus",
            "popSource",
            "updateFocusedLeafSnapshot",
            "clearFocusedLeafSnapshotIfMatches"
        )
        val functionPattern = Regex("""^\s*(?:private\s+|internal\s+|public\s+)?fun\s+([A-Za-z0-9_]+)\s*\(""")

        lines.forEachIndexed { index, line ->
            functionPattern.find(line)?.let { currentFunction = it.groupValues[1] }
            val writesLeafState =
                containsIndexedAssignment(line, "focusedLeafSnapshotByLayerScope") ||
                    line.contains("focusedLeafSnapshotByLayerScope.remove(") ||
                    line.contains("focusedLeafSnapshotByLayerScope.clear(") ||
                    containsIndexedAssignment(line, "recentFocusByLayer") ||
                    line.contains("recentFocusByLayer.getOrPut(") ||
                    line.contains("recentFocusByLayer.remove(") ||
                    line.contains("recentFocusByLayer.clear(") ||
                    line.contains("sourceStack.add(") ||
                    line.contains("sourceStack.addAll(") ||
                    line.contains("sourceStack.remove") ||
                    line.contains("sourceStack.clear(") ||
                    containsIndexedAssignment(line, "lastFocusedScopeByLayer") ||
                    line.contains("lastFocusedScopeByLayer.remove(") ||
                    line.contains("lastFocusedScopeByLayer.clear(")
            if (writesLeafState && currentFunction !in allowedFunctions) {
                add(violation(relativePath, index, "coordinator leaf 状态写入必须集中在 helper 中"))
            }
        }
    }

    private fun containsIndexedAssignment(line: String, name: String): Boolean {
        val nameIndex = line.indexOf("$name[")
        if (nameIndex < 0) return false
        val closeIndex = line.indexOf("] =", startIndex = nameIndex)
        if (closeIndex < 0) return false
        val operatorIndex = closeIndex + 1
        return line.getOrNull(operatorIndex + 2) != '='
    }

    private fun isInitialFocusContext(lines: List<String>, lineIndex: Int): Boolean {
        val firstLine = (lineIndex - 20).coerceAtLeast(0)
        val context = lines.subList(firstLine, lineIndex + 1).joinToString("\n")
        return listOf(
            "initialFocus",
            "InitialFocus",
            "canRequestInitialFocus",
            "autoRequestEntryFocus",
            "initial entry"
        ).any { token -> context.contains(token) }
    }

    private fun runFixtureSelfCheck() {
        val cases = listOf(
            StaticAnalysisFixture(
                name = "D1 business compose focus import is rejected",
                relativePath = "app/src/main/kotlin/dev/aaa1115910/bv/screen/BadFocus.kt",
                lines = listOf(
                    "import androidx.compose.ui.focus.FocusRequester",
                    "fun bad() {}"
                ),
                expectedViolation = "Compose 原生焦点"
            ),
            StaticAnalysisFixture(
                name = "D1 FocusDirection import is allowed",
                relativePath = "app/src/main/kotlin/dev/aaa1115910/bv/screen/AllowedDirection.kt",
                lines = listOf(
                    "import androidx.compose.ui.focus.FocusDirection",
                    "fun good(direction: FocusDirection) = direction"
                ),
                expectedViolation = null
            ),
            StaticAnalysisFixture(
                name = "D2 business low level request is rejected",
                relativePath = "app/src/main/kotlin/dev/aaa1115910/bv/screen/BadRequest.kt",
                lines = listOf(
                    "fun bad(coordinator: Any) {",
                    "    coordinator.requestEntryFocusDetailed(\"entry\")",
                    "}"
                ),
                expectedViolation = "低层 request"
            ),
            StaticAnalysisFixture(
                name = "D2 wjzfocus low level request is allowed",
                relativePath = "app/src/main/kotlin/dev/aaa1115910/bv/wjzfocus/WjzFocusRouter.kt",
                lines = listOf(
                    "fun good(coordinator: Any) {",
                    "    coordinator.requestFocusDetailed(nodeId)",
                    "}"
                ),
                expectedViolation = null
            ),
            StaticAnalysisFixture(
                name = "D3 business enqueue is rejected",
                relativePath = "app/src/main/kotlin/dev/aaa1115910/bv/component/BadEnqueue.kt",
                lines = listOf(
                    "fun bad(coordinator: Any) {",
                    "    coordinator.enqueueRestoreLayer(layer)",
                    "}"
                ),
                expectedViolation = "pending/enqueue"
            ),
            StaticAnalysisFixture(
                name = "D4 business low level focus state access is rejected",
                relativePath = "app/src/main/kotlin/dev/aaa1115910/bv/screen/BadFocusState.kt",
                lines = listOf(
                    "fun bad(focusEntry: Any) {",
                    "    if (focusEntry.hasFocus) return",
                    "}"
                ),
                expectedViolation = "focus 状态"
            ),
            StaticAnalysisFixture(
                name = "D4 test low level focus state access is allowed",
                relativePath = "app/src/androidTest/kotlin/dev/aaa1115910/bv/wjzfocus/WjzFocusTest.kt",
                lines = listOf(
                    "fun good(focusEntry: Any) {",
                    "    if (focusEntry.hasFocus) return",
                    "}"
                ),
                expectedViolation = null
            ),
            StaticAnalysisFixture(
                name = "D4 coordinator direct leaf state write is rejected",
                relativePath = "app/src/main/kotlin/dev/aaa1115910/bv/wjzfocus/WjzFocusCoordinator.kt",
                lines = listOf(
                    "class WjzFocusCoordinator {",
                    "    fun bad(entry: Any) {",
                    "        sourceStack.add(entry)",
                    "    }",
                    "}"
                ),
                expectedViolation = "leaf 状态写入"
            ),
            StaticAnalysisFixture(
                name = "D4 coordinator helper write is allowed",
                relativePath = "app/src/main/kotlin/dev/aaa1115910/bv/wjzfocus/WjzFocusCoordinator.kt",
                lines = listOf(
                    "class WjzFocusCoordinator {",
                    "    private fun recordRecentFocus() {",
                    "        recentFocusByLayer.getOrPut(layer) { ArrayDeque() }",
                    "    }",
                    "}"
                ),
                expectedViolation = null
            ),
            StaticAnalysisFixture(
                name = "D4 coordinator restoreStateInternal write is allowed",
                relativePath = "app/src/main/kotlin/dev/aaa1115910/bv/wjzfocus/WjzFocusCoordinator.kt",
                lines = listOf(
                    "class WjzFocusCoordinator {",
                    "    private fun restoreStateInternal(savedState: Any) {",
                    "        recentFocusByLayer.clear()",
                    "        recentFocusByLayer[layer] = recent",
                    "        sourceStack.clear()",
                    "        sourceStack.addAll(restoredSourceStack)",
                    "        lastFocusedScopeByLayer.clear()",
                    "        lastFocusedScopeByLayer[layer] = scope",
                    "    }",
                    "}"
                ),
                expectedViolation = null
            ),
            StaticAnalysisFixture(
                name = "D4 coordinator popSource write is allowed",
                relativePath = "app/src/main/kotlin/dev/aaa1115910/bv/wjzfocus/WjzFocusCoordinator.kt",
                lines = listOf(
                    "class WjzFocusCoordinator {",
                    "    private fun popSource(index: Int) {",
                    "        sourceStack.removeAt(index)",
                    "    }",
                    "}"
                ),
                expectedViolation = null
            ),
            StaticAnalysisFixture(
                name = "D5 component initial focus must use InitialEntry",
                relativePath = "app/src/main/kotlin/dev/aaa1115910/bv/component/BvTabRow.kt",
                lines = listOf(
                    "val canRequestInitialFocus = true",
                    "coordinator.submitNodeFocusIntent(",
                    "    nodeId = targetNodeId,",
                    "    intent = WjzFocusSubmitIntent.ExternalEntry(dedupeKey = key)",
                    ")"
                ),
                expectedViolation = "初始首焦"
            ),
            StaticAnalysisFixture(
                name = "D5 component BvTabRow initial focus fixture is allowed",
                relativePath = "app/src/main/kotlin/dev/aaa1115910/bv/component/BvTabRow.kt",
                lines = listOf(
                    "val canRequestInitialFocus = true",
                    "coordinator.submitNodeFocusIntent(",
                    "    nodeId = targetNodeId,",
                    "    intent = WjzFocusSubmitIntent.InitialEntry(dedupeKey = key)",
                    ")"
                ),
                expectedViolation = null
            )
        )

        val failures = cases.mapNotNull { fixture ->
            val violations = analyzeFile(fixture.relativePath, fixture.lines)
            val expected = fixture.expectedViolation
            when {
                expected == null && violations.isNotEmpty() -> {
                    "${fixture.name}: expected PASS, got ${violations.joinToString()}"
                }
                expected != null && violations.none { it.contains(expected) } -> {
                    "${fixture.name}: expected violation containing '$expected', got ${violations.joinToString()}"
                }
                else -> null
            }
        }
        if (failures.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("WjzFocus static analysis fixture self-check failed:")
                    failures.forEach { appendLine("- $it") }
                }
            )
        }
    }

    private fun analyzeFile(relativePath: String, lines: List<String>): List<String> {
        val normalizedPath = relativePath.replace('/', File.separatorChar)
        val isWjzFocus = normalizedPath.contains(wjzFocusPath)
        val isTest = normalizedPath.isTestPath()
        return buildList {
            if (!isWjzFocus) {
                checkBusinessComposeFocusUsage(relativePath, lines)
                checkWjzFocusLowLevelAccess(relativePath, lines)
                if (!isTest) {
                    checkWjzFocusLowLevelFocusStateAccess(relativePath, lines)
                }
            }
            if (normalizedPath.contains(componentPath)) {
                checkComponentSubmitIntent(relativePath, lines)
            }
            if (normalizedPath.endsWith(coordinatorPath)) {
                checkCoordinatorLeafStateWrites(relativePath, lines)
            }
        }
    }

    private data class StaticAnalysisFixture(
        val name: String,
        val relativePath: String,
        val lines: List<String>,
        val expectedViolation: String?
    )

    private fun violation(relativePath: String, lineIndex: Int, message: String): String {
        return "$relativePath:${lineIndex + 1}: $message"
    }

    private fun collectCallText(lines: List<String>, startIndex: Int, token: String): String {
        val builder = StringBuilder()
        var depth = 0
        var started = false
        for (lineIndex in startIndex until lines.size) {
            val line = lines[lineIndex]
            val scanFrom = if (!started) line.indexOf(token).takeIf { it >= 0 } ?: 0 else 0
            val fragment = line.substring(scanFrom)
            builder.appendLine(fragment)
            fragment.forEach { char ->
                when (char) {
                    '(' -> {
                        depth++
                        started = true
                    }

                    ')' -> depth--
                }
            }
            if (started && depth <= 0) break
        }
        return builder.toString()
    }

    private fun path(value: String): String {
        return value.replace('/', File.separatorChar)
    }

    private fun String.isTestPath(): Boolean {
        return contains(path("src/test/")) || contains(path("src/androidTest/"))
    }
}
