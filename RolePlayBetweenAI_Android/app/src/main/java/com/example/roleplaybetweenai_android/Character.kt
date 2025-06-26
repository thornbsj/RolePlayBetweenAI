package com.example.roleplaybetweenai_android

import com.alibaba.dashscope.aigc.generation.Generation
import com.alibaba.dashscope.aigc.generation.GenerationParam
import com.alibaba.dashscope.aigc.generation.GenerationResult
import io.reactivex.Flowable
import com.alibaba.dashscope.common.Message
import com.alibaba.dashscope.common.Role
import com.alibaba.dashscope.utils.JsonUtils
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName

data class Msg(
    val role: String,
    val content: String,
    @SerializedName("current_seq")
    val currentSeq: Int
)

class Character(
    val name: String,
    val prompt: String,
    val initial: String = ""
) {
    public var history = mutableListOf<Msg>().apply {
        add(Msg("system", prompt, -1))
    }

    var currentSeq = -1
        public set

    var lastOutput = initial
        public set

    var usedTokens = 0
        public set

    init {
        if (initial.isNotEmpty()) {
            currentSeq += 1
            history.add(Msg("assistant", initial, currentSeq))
            lastOutput = initial
        }
    }

    fun addMessage(role: String, content: String) {
        if (role == "assistant") {
            currentSeq += 1
            lastOutput = content
        }
        history.add(Msg(role, content, currentSeq))
    }

    fun buildIptHistory(): List<Map<String, String>> {
        return history.map {
            mapOf("role" to it.role, "content" to it.content)
        }
    }

    fun getFullHistory(): List<Msg> = history.toList()

    fun reset() {
        history.clear()
        history.add(Msg("system", prompt, -1))
        currentSeq = -1
        lastOutput = ""
        usedTokens = 0

        if (initial.isNotEmpty()) {
            addMessage("assistant", initial)
        }
    }

    fun getLastMessage(): Msg? = history.lastOrNull()
    fun chatTo(key:String,modelName:String,s:String,enable_Thinking:Boolean,enable_search:Boolean,stream:Boolean,temprature:Float):String{
        addMessage("user",s)
        //currentSeq+=1
        val iptMsg = createMessageArray()
        val params = GenerationParam.builder()
            .apiKey(key)
            .model(modelName)
            .enableSearch(enable_search)
            .messages(iptMsg)
            .resultFormat(GenerationParam.ResultFormat.MESSAGE)
            .incrementalOutput(stream)
            .enableThinking(enable_Thinking)
            .temperature(temprature)
            .build()
        val gen = Generation();
        if(stream){
            val GenResult = gen.streamCall(params)
            var res = ""
            GenResult.blockingForEach { i-> res+=parse_json_to_history(JsonUtils.toJson(i))}
            addMessage("assistant",res)
            return res
        }
        val GenResult = gen.call(params)
        val res = parse_json_to_history(JsonUtils.toJson(GenResult))
        addMessage("assistant",res)
        return res
    }
    fun createMessageArray():List<Message>{
        val res = ArrayList<Message>()
        val roleDict = mapOf("assistant" to Role.ASSISTANT,"system" to Role.SYSTEM,"user" to Role.USER);
        for (msg in history){
            val current_Role:Role = roleDict[msg.role] ?: Role.USER;
            res.add(createMessage(current_Role,msg.content));
        }
        return res
    }

    fun parse_json_to_history(jsonStr:String):String{
        val root = JsonParser.parseString(jsonStr).getAsJsonObject();
        val contents = root.getAsJsonObject("output")
            .getAsJsonArray("choices").map{it.asJsonObject.getAsJsonObject("message").get("content").asString}
            .joinToString ("")
        try{
            val totalTokens = root.getAsJsonObject("usage")
            .get("total_tokens").getAsInt()
            usedTokens += totalTokens
        }catch(e: Exception){
            usedTokens = 0
        }
        
        return contents
    }

    fun createMessage(role:Role,content:String):Message{
        return Message.builder().role(role.getValue()).content(content).build();
    }

    fun truncateHistory(lastSeq: Int){
        history = history.filter { n ->
            (n.currentSeq as Int <= lastSeq) &&
                    !(n.currentSeq as Int == lastSeq && n.role != "assistant")
        }.toMutableList()
        lastOutput = history
            .last { it.role == "assistant" }
            .content
        currentSeq = lastSeq
    }

    fun addSystemPrompt(prompt: String){
        if(history.lastOrNull()?.role.equals("system")){
            history[history.size-1] = Msg("system", prompt, currentSeq)
        }else{
            history.add(Msg("system", prompt, currentSeq))
        }
    }

    fun importHistory(historyList: List<Map<String, Any>>) {
        history.clear()
        historyList.forEach { entry ->
            history.add(Msg(
                role = entry["role"] as String,
                content = entry["content"] as String,
                currentSeq = (entry["current_seq"] as? Int) ?: 0
            ))
        }

        // 更新当前状态
        lastOutput = history.lastOrNull { it.role == "assistant" }?.content ?: ""
        currentSeq = history.maxOfOrNull { it.currentSeq } ?: -1
    }
}