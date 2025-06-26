package com.example.roleplaybetweenai_android

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roleplaybetweenai_android.MainActivity.GlobalVariables
import com.example.roleplaybetweenai_android.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class UiState {
    object Idle : UiState()
    data class Signal(val count: Int) : UiState()
    data class Error(val err:String) : UiState()
    data class Finish(val isFinish:Boolean):UiState()
    data class Working(val isWorking: Boolean):UiState()
}

class ChatView: ViewModel(){
    private val _uiState= MutableStateFlow<UiState>(UiState.Idle)
    private val _isWidgetsEnabled = MutableLiveData(true)
    val uiState: StateFlow<UiState> = _uiState
    var lastError = ""
    val isWidgetsEnabled: LiveData<Boolean> = _isWidgetsEnabled

    fun EnableWidgets(){
        _isWidgetsEnabled.value=true
    }

    fun DisbleWidgets(){
        _isWidgetsEnabled.value=false
    }

    suspend fun startRequest(binding: ActivityMainBinding){
        if(GlobalVariables.current_Chat.npcs.size!=2){
            lastError = "请添加至少1条系统提示词"
            _uiState.value = UiState.Error("请添加至少1条系统提示词")
            return
        }
        // 检查rnd和temprature
        try{
            binding.roundNumber.text.toString().toInt()
        }catch (e:Exception){
            lastError = "轮数必须是一个整数数字"
            _uiState.value = UiState.Error("轮数必须是一个整数数字")
            return
        }
        try{
            binding.temprature.text.toString().toFloat()
        }catch (e:Exception){
            lastError ="temperature必须是0~1之间的小数。"
            _uiState.value = UiState.Error("temperature必须是0~1之间的小数。")
            return
        }
        val rnd = binding.roundNumber.text.toString().toInt()
        val temperature:Float = binding.temprature.text.toString().toFloat()
        val key = binding.editTextTextPassword.text.toString()
        val modelName = binding.modleName.text.toString()
        val enableThinking = binding.enableThinking.isChecked
        val enableSearch = binding.enableSearch.isChecked
        val stream = binding.enableStream.isChecked
        if(temperature>1 || temperature<0){
            lastError = "temperature必须是0~1之间的小数。"
            _uiState.value = UiState.Error("temperature必须是0~1之间的小数。")
            return
        }
        _uiState.value = UiState.Working(true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                GlobalVariables.current_Chat.argue(
                    rnd,
                    key,
                    modelName,
                    enableThinking,
                    enableSearch,
                    stream,
                    temperature
                ).collect { result ->
                    _uiState.value = UiState.Signal(result)
                }
                //Display_His(binding,ConvertHistory2HTML(binding))
                if (GlobalVariables.is_init == true) {
                    GlobalVariables.is_init = false
                }
                _uiState.value = UiState.Finish(true)
            } catch (e: Exception) {
                lastError = e.message.toString()
                _uiState.value = UiState.Error(e.message.toString())
                //Display_His(binding,Html.fromHtml(e.message.toString(),Html.FROM_HTML_MODE_COMPACT))
            }
        }
    }
}