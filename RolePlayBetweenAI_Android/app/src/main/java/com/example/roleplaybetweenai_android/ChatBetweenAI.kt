package com.example.roleplaybetweenai_android

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatBetweenAI {
    val npcs = mutableMapOf<String, Character>()
    var initialNpc: String? = null
    val history = mutableListOf<Map<String, Any?>>()
    val changeLog = mutableListOf<Map<String, Any?>>()
    val seqHistoryLengthDict = mutableMapOf<Int, Int>()
    companion object {
        const val TOKEN_FOR_SPLIT_DIFFERENT_NPC = "<split_for_different_npc>"
        const val TOKEN_FOR_SPLIT_NPC_AND_CONTENT = "<split_for_npc_and_content>"
        const val TOKEN_FOR_SPLIT_NPC_BEFORE_CHANGE = "<split_for_npc_and_before_change>"
        const val TOKEN_FOR_SPLIT_CHANGE_BEFORE_AND_AFTER = "<split_for_change_before_and_after>"
        const val DELIMITER_COLUMN_FOR_CSV = "<DELIMITER_COLUMN_FOR_CSV>"
        const val DELIMITER_ROW_FOR_CSV = "<DELIMITER_ROW_FOR_CSV>"
    }

    fun getUsedToken(): Int {
        return npcs.values.sumOf { it.usedTokens }
    }

    suspend fun argue(rnd: Int = 1,key:String,modelName:String,enableThinking:Boolean,enableSearch: Boolean, stream: Boolean, temperature: Float):Flow<Int> = flow{
        var status=0
        if (npcs.size != 2) {
            throw IllegalArgumentException("目前只支持 2 个 NPC 的交流")
        }
        var rnd = rnd
        val first = npcs[initialNpc] ?: throw IllegalStateException("初始 NPC 未设置")
        val secondName = npcs.keys.first { it != initialNpc }
        val second = npcs[secondName] ?: throw IllegalStateException("找不到第二个 NPC")

        // 第一轮对话
        if (history.filter { n->n.get("role")!="system" }.isEmpty()) {
            history.add(mapOf(
                "role" to initialNpc,
                "content" to first.initial,
                "role_seq" to first.currentSeq
            ))

            val res = second.chatTo(key,modelName,first.initial, enableThinking, enableSearch, stream, temperature)
            history.add(mapOf(
                "role" to second.name,
                "content" to res,
                "role_seq" to second.currentSeq
            ))
            rnd -= 1
            seqHistoryLengthDict[0] = history.size
            emit(status)
            status+=1
            if (rnd < 1){
                emit(-999)
                return@flow
            }
        }

        // 后续轮次
        for (i in 1 .. rnd) {
            oneRoundArgueBetweenTwoAI(key,modelName,first, second,enableThinking, enableSearch, stream, temperature)
            emit(status)
            status+=1
        }
    }

    private suspend fun oneRoundArgueBetweenTwoAI(
        key:String,modelName:String,
        first: Character,
        second: Character,
        enableThinking: Boolean,
        enableSearch: Boolean,
        stream: Boolean,
        temperature: Float
    ) {
        val firstInput = second.lastOutput
        val firstOutput = first.chatTo(key,modelName,firstInput, enableThinking, enableSearch, stream, temperature)

        val secondOutput = second.chatTo(key,modelName,firstOutput, enableThinking, enableSearch, stream, temperature)

        history.add(mapOf(
            "role" to first.name,
            "content" to firstOutput,
            "role_seq" to first.currentSeq
        ))

        history.add(mapOf(
            "role" to second.name,
            "content" to secondOutput,
            "role_seq" to second.currentSeq
        ))

        seqHistoryLengthDict[second.currentSeq] = history.size
    }

//    fun changeHistory(idx: Int, s: String) {
//        if (history[idx]["role"] == "system") {
//            throw IllegalStateException("不能修改历史 prompt！")
//        }
//
//        val origin = history[idx]["content"] as String
//        val originRole = history[idx]["role"] as String
//        val roleSeq = history[idx]["role_seq"] as Int
//
//        // 更新历史记录
//        val newEntry = history[idx].toMutableMap().apply { put("content", s) }
//        history[idx] = newEntry
//
//        // 添加到变更日志
//        changeLog.add(mapOf(
//            "role" to originRole,
//            "origin" to origin,
//            "updated" to s,
//            "role_seq" to roleSeq
//        ))
//
//        // 更新对应 NPC 的历史
//        val npc = npcs[originRole] ?: return
//        val npcHistory = npc.getFullHistory()
//
//        // 找到对应的历史记录并更新
//        val npcIdx = npcHistory.indexOfFirst {
//            it.role == "assistant" && it.currentSeq == roleSeq
//        }
//
//        if (npcIdx != -1) {
//            npc.updateMessageContent(npcIdx, s)
//
//            // 如果是最后一条消息，更新 lastOutput
//            if (npcHistory.lastIndex == npcIdx) {
//                npc.lastOutput = s
//            }
//        }
//
//        // 更新其他 NPC 的输入
//        if (initialNpc == originRole) {
//            npcs.values.forEach { otherNpc ->
//                if (otherNpc.name != originRole) {
//                    val otherHistory = otherNpc.getFullHistory()
//                    val inputIdx = otherHistory.indexOfFirst {
//                        it.role == "user" && it.currentSeq == roleSeq
//                    }
//
//                    if (inputIdx != -1) {
//                        otherNpc.updateMessageContent(inputIdx, s)
//                    }
//                }
//            }
//        }
//    }
    fun truncate(lastSeq: Int) {
        if ((npcs[initialNpc]?.currentSeq ?: 0) <= lastSeq) return

        val truncateIndex = seqHistoryLengthDict[lastSeq] ?: return
        history.subList(truncateIndex, history.size).clear()

        npcs.values.forEach { npc ->
            npc.truncateHistory(lastSeq)
        }
    }

    fun addSystemPrompt(promptDict: Map<String, String>) {
        promptDict.forEach { (name, prompt) ->
            if (!npcs.containsKey(name)) {
                throw IllegalArgumentException("$name 不存在！")
            }
        }

        promptDict.forEach { (name, prompt) ->
            npcs[name]?.addSystemPrompt(prompt)
            history.add(mapOf(
                "role" to "system",
                "content" to prompt,
                "role_seq" to npcs[name]?.currentSeq
            ))
        }

    }

    fun historyToTxt(historyList: List<Map<String, Any?>>): String {
        return historyList.joinToString(TOKEN_FOR_SPLIT_DIFFERENT_NPC) { entry ->
            listOf(
                entry["role"] as String,
                entry["content"] as String,
                entry["role_seq"].toString()
            ).joinToString(TOKEN_FOR_SPLIT_NPC_AND_CONTENT)
        }
    }

    fun changeLogToTxt(): String {
        return changeLog.joinToString(TOKEN_FOR_SPLIT_DIFFERENT_NPC) { log ->
            listOf(
                log["role"] as String,
                log["origin"] as String,
                log["updated"] as String,
                log["role_seq"].toString()
            ).joinToString(TOKEN_FOR_SPLIT_CHANGE_BEFORE_AND_AFTER)
        }
    }

    private fun generateFileName(): String {
        return npcs.keys.joinToString("_") + SimpleDateFormat(
            "yyyyMMddHHmmss",
            Locale.getDefault()
        ).format(Date())
    }

    fun exportHistory(context: Context,name: String = "") {
        val fileName = if (name.isNotEmpty()) name else generateFileName()
        val dir = context.getFilesDir().toString()+"$fileName.csv"
        File(dir).writeText(historyToTxt(history))
    }

    fun exportNpc(context: Context,name: String = "") {
        val fileName = if (name.isNotEmpty()) name else generateFileName()
        val csvContent = buildString {

            npcs.values.forEach { char ->
                val row = listOf(
                    char.prompt,
                    char.name,
                    char.initial,
                    historyToTxt(char.getFullHistory().map {
                        mapOf(
                            "role" to it.role.toString(),
                            "content" to it.content.toString(),
                            "role_seq" to it.currentSeq.toString()
                        )
                    }),
                    char.lastOutput
                ).joinToString ( DELIMITER_COLUMN_FOR_CSV ) + DELIMITER_ROW_FOR_CSV
                append(row)
            }
        }
        val dir = context.getFilesDir().toString()+"$fileName.csv"
        File(dir).writeText(csvContent)
    }

    fun exportChangeLog(context: Context,name: String = "") {
        val fileName = if (name.isNotEmpty()) name else generateFileName()
        val dir = context.getFilesDir().toString()+"$fileName.csv"
        File(dir).writeText(changeLogToTxt())
    }

    fun export(context: Context,baseName: String) {
        exportNpc(context,"${baseName}_NPC")
        exportHistory(context,"${baseName}_history")
        exportChangeLog(context,"${baseName}_change_log")
    }


    fun txtToHistory(s: String, type: String = "Chat"): List<Map<String, Any>> {
        if (s.isEmpty()) return emptyList()

        return s.split(TOKEN_FOR_SPLIT_DIFFERENT_NPC).map { entry ->
            val parts = entry.split(TOKEN_FOR_SPLIT_NPC_AND_CONTENT)
            when (type) {
                "Chat" -> mapOf(
                    "role" to parts[0],
                    "content" to parts[1],
                    "role_seq" to parts[2].toInt()
                )
                else -> mapOf(
                    "role" to parts[0],
                    "content" to parts[1],
                    "current_seq" to parts[2].toInt()
                )
            }
        }
    }

    fun importHistory(context: Context,filename: String) {
        val dir = context.getFilesDir().toString()+"$filename"
        val content = File(dir).readText()
        history.clear()
        history.addAll(txtToHistory(content))

        // 重建序列字典
        seqHistoryLengthDict.clear()
        history.forEachIndexed { index, entry ->
            (entry["role_seq"] as? Int)?.let { seq ->
                seqHistoryLengthDict[seq] = index + 1
            }
        }
    }

    fun importNpc(context: Context,filename: String) {
        val dir = context.getFilesDir().toString()+"$filename"
        val content = File(dir).readText().split(DELIMITER_ROW_FOR_CSV)

        if (content.size < 2) return

        for (line in content) {
            val values = line.split(DELIMITER_COLUMN_FOR_CSV)
            if (values.size < 5) continue

            val name = values[1]
            val prompt = values[0]
            val initial = values[2]
            val historyText = values[3]
            val lastOutput = values[4]

            val char = Character(name, prompt, initial)
            char.lastOutput = lastOutput
            char.importHistory(txtToHistory(historyText, "NPC"))

            npcs[name] = char

            if (initial.isNotEmpty()) {
                initialNpc = name
            }
        }
    }

    fun importChangeLog(context: Context,filename: String) {
        val dir = context.getFilesDir().toString()+"$filename"
        val content = File(dir).readText()
        changeLog.clear()

        if (content.isEmpty()) return

        content.split(TOKEN_FOR_SPLIT_DIFFERENT_NPC).forEach { entry ->
            val parts = entry.split(TOKEN_FOR_SPLIT_CHANGE_BEFORE_AND_AFTER)
            if (parts.size >= 4) {
                changeLog.add(mapOf(
                    "role" to parts[0],
                    "origin" to parts[1],
                    "updated" to parts[2],
                    "role_seq" to parts[3].toInt()
                ))
            }
        }
    }

    fun import(context: Context,baseName: String) {
        importNpc(context,"${baseName}_NPC.csv")
        importHistory(context,"${baseName}_history.csv")
        importChangeLog(context,"${baseName}_change_log.csv")
    }

    fun exportDialogueToCsv(fileName: String) {
        val step = npcs.size
        if (step == 0) return

        val csvContent = buildString {
            // 标题行
            appendLine(npcs.keys.joinToString(","))

            // 内容行
            var idx = 0
            while (idx < history.size) {
                val round = history.subList(idx, minOf(idx + step, history.size))
                val row = npcs.keys.map { npcName ->
                    round.find { it["role"] == npcName }?.get("content")?.toString() ?: ""
                }
                appendLine(row.joinToString(","))
                idx += step
            }
        }

        File("$fileName.csv").writeText(csvContent)
    }

}