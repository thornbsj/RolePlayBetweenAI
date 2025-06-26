package com.example.roleplaybetweenai_android
import android.app.Activity
import android.os.Bundle
import android.provider.Settings.Global
import android.text.Html
import android.text.Spanned
import android.text.method.ScrollingMovementMethod
import android.view.Menu
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.tooling.preview.Preview
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.example.roleplaybetweenai_android.MainActivity.GlobalVariables
import com.example.roleplaybetweenai_android.databinding.ActivityMainBinding
import com.example.roleplaybetweenai_android.ui.theme.RolePlayBetweenAI_AndroidTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    object GlobalVariables {
        var is_init: Boolean = true;
        var current_Chat: ChatBetweenAI = ChatBetweenAI();
    }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)//val binding = ActivityMainBinding.inflate(layoutInflater)
        val viewModel: ChatView by viewModels()
        setContentView(binding.root)
        binding.viewModel=viewModel
        binding.lifecycleOwner = this

        binding.addSystemPrompt.setOnClickListener {
            add_system_msg(binding)
        }
        binding.resetButton.setOnClickListener {
            Reset_with_Toolip(this,binding)
        }
        binding.chatButton.setOnClickListener {
            lifecycleScope.launch {
                viewModel.startRequest(binding)
            }
        }
        lifecycleScope.launch{
            repeatOnLifecycle(Lifecycle.State.STARTED){
                viewModel.uiState.collect{
                    state->when(state){
                        is UiState.Finish -> {
                            viewModel.EnableWidgets()
                        }
                        is UiState.Error -> {
                            viewModel.EnableWidgets()
                            Display_His(binding,Html.fromHtml(viewModel.lastError,Html.FROM_HTML_MODE_COMPACT))
                        }
                        is UiState.Signal ->{
                            Display_His(binding,ConvertHistory2HTML(binding))
                            binding.UsedToken.setText("已耗费Token数："+GlobalVariables.current_Chat.getUsedToken().toString())
                        }
                        is UiState.Working ->{
                            viewModel.DisbleWidgets()
                        }
                        UiState.Idle ->{}
                    }
                }
            }
        }

        binding.savefile.setOnClickListener{
            saveFile(binding)
        }
        binding.loadfile.setOnClickListener{
            loadFile(binding)
        }
        binding.truncateButton.setOnClickListener{
            truncate(binding)
        }
    }
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    fun add_system_msg(binding:ActivityMainBinding){
        try{
            if(GlobalVariables.is_init){
                Reset(binding)
                if(binding.roleNameA.text.toString().isEmpty() || binding.roleNameB.text.toString().isEmpty() || binding.TextAPrompt.text.toString().isEmpty() || binding.TextBPrompt.text.toString().isEmpty()){
                    Toast.makeText(this, "请填写角色以及对应的角色提示词。", Toast.LENGTH_SHORT).show();
                    return;
                }
                //后续还要加个initial和token数的控件
                if(!((binding.initialA.text.toString().isEmpty() && !binding.initialB.text.toString().isEmpty()) || (!binding.initialA.text.toString().isEmpty() && binding.initialB.text.toString().isEmpty()))){
                    Toast.makeText(this, "必须有且只有1个角色有Initial词。", Toast.LENGTH_SHORT).show();
                    return;
                }
                binding.roleNameA.isEnabled = false
                binding.roleNameB.isEnabled = false
                var RoleA = Character(binding.roleNameA.text.toString(),binding.TextAPrompt.text.toString(),binding.initialA.text.toString())
                var RoleB = Character(binding.roleNameB.text.toString(),binding.TextBPrompt.text.toString(),binding.initialB.text.toString())
                GlobalVariables.current_Chat.npcs += RoleA.name to RoleA
                GlobalVariables.current_Chat.npcs += RoleB.name to RoleB
                if(binding.initialA.text.toString().isEmpty()){
                    GlobalVariables.current_Chat.initialNpc = RoleB.name
                }else{
                    GlobalVariables.current_Chat.initialNpc = RoleA.name
                }

                GlobalVariables.current_Chat.history.add(mapOf(
                    "role" to "system",
                    "content" to RoleA.name+":"+binding.TextAPrompt.text.toString(),
                    "role_seq" to RoleA?.currentSeq)
                )
                GlobalVariables.current_Chat.history.add(mapOf(
                    "role" to "system",
                    "content" to RoleB.name+":"+binding.TextBPrompt.text.toString(),
                    "role_seq" to RoleB?.currentSeq)
                )
                //在MainChat里设置 GlobalVariables.is_init=false
                //后续应该在里面做提示，而非直接限制不能点击
            }else{
                var current_Prompt = mutableMapOf<String, String>()
                current_Prompt += binding.roleNameA.text.toString() to binding.TextAPrompt.text.toString()
                current_Prompt += binding.roleNameB.text.toString() to binding.TextBPrompt.text.toString()
                GlobalVariables.current_Chat.addSystemPrompt(current_Prompt)
            }
        }catch(e: Exception){
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show();
        }

    }




    fun Display_His(binding:ActivityMainBinding,txt:Spanned?){
        binding.textViewRoles.setText(txt)
    }

    fun ConvertHistory2HTML(binding:ActivityMainBinding): Spanned? {
        val history = GlobalVariables.current_Chat.history
        val htmlBuilder = StringBuilder()
        htmlBuilder.append("<div>")
        
        history.forEachIndexed { index, item ->
            val roleSeq = item["role_seq"]?.toString() ?: ""
            val content = item["content"]?.toString() ?: ""
            val role = item["role"]
            var color = ""

            when(role){
                "sytem"->{
                    color="gray"
                }
                GlobalVariables.current_Chat.npcs.keys.firstOrNull()->{color="red"}
                GlobalVariables.current_Chat.npcs.keys.lastOrNull()->{color="blue"}
            }

            htmlBuilder.append("""
                <div style='margin-bottom: 10px;'>
                    <span style='color: black; font-weight: bold;'>$roleSeq</span> 
                    <span style='color: $color;'><br>$content<br></span>
                </div>
            """.trimIndent())
        }
        
        htmlBuilder.append("</div>")
        return Html.fromHtml(htmlBuilder.toString(),Html.FROM_HTML_MODE_COMPACT)
    }

    fun Reset(binding:ActivityMainBinding){
        GlobalVariables.current_Chat = ChatBetweenAI()
        GlobalVariables.is_init = true
    }

    fun ResetAll(binding: ActivityMainBinding){
        GlobalVariables.current_Chat = ChatBetweenAI()
        GlobalVariables.is_init = true
        binding.roleNameA.setText("")
        binding.roleNameB.setText("")
        binding.TextAPrompt.setText("")
        binding.TextBPrompt.setText("")
        binding.initialA.setText("")
        binding.initialB.setText("")
        binding.textViewRoles.setText("")
        binding.truncateNum.setText("")
    }

    fun Reset_with_Toolip(activity: Activity,binding:ActivityMainBinding){
        val builder = AlertDialog.Builder(activity)
        val alert = builder.setTitle("确定重置")
            .setMessage("是否重置对话？未保存的对话会丢失")
            .setPositiveButton("是"){
                _,_->ResetAll(binding)
            }.setNegativeButton("否"){
                dialog,_->dialog.dismiss()
            }.setCancelable(false)
            .create()
        alert.show()
    }

    fun saveFile(binding: ActivityMainBinding){
        try
        {
            if(GlobalVariables.is_init){
                Toast.makeText(this,"尚未对话，不能保存！", Toast.LENGTH_SHORT).show()
                return
            }
            GlobalVariables.current_Chat.export(this,binding.saveFilename.text.toString())
        }
        catch(e:Exception){
            Display_His(binding,Html.fromHtml(e.message.toString(),Html.FROM_HTML_MODE_COMPACT))
        }
    }
    fun loadFile(binding: ActivityMainBinding){
        try
        {
            GlobalVariables.current_Chat.import(this,binding.saveFilename.text.toString())
            Display_His(binding,ConvertHistory2HTML(binding))
            val npcA = GlobalVariables.current_Chat.npcs.entries.elementAtOrNull(0)
            binding.roleNameA.setText(npcA?.key)
            binding.TextAPrompt.setText(npcA?.value?.prompt)
            binding.initialA.setText(npcA?.value?.initial)
            val npcB = GlobalVariables.current_Chat.npcs.entries.elementAtOrNull(1)
            binding.roleNameB.setText(npcB?.key)
            binding.TextBPrompt.setText(npcB?.value?.prompt)
            binding.initialB.setText(npcB?.value?.initial)
            GlobalVariables.is_init=false
            binding.roleNameA.isEnabled = false
            binding.roleNameB.isEnabled = false
        }catch(e:Exception){
            Display_His(binding,Html.fromHtml(e.message.toString(),Html.FROM_HTML_MODE_COMPACT))
        }
    }
    fun truncate(binding: ActivityMainBinding){
        try{
            binding.truncateNum.text.toString().toInt()
        }catch(e:Exception){
            Toast.makeText(this, "截取的最后标号必须是一个整数。", Toast.LENGTH_SHORT).show();
            return
        }
        try{
            GlobalVariables.current_Chat.truncate(binding.truncateNum.text.toString().toInt())
            binding.truncateNum.setText("")
            Display_His(binding,ConvertHistory2HTML(binding))
        }catch(e:Exception){
            Display_His(binding,Html.fromHtml(e.message.toString(),Html.FROM_HTML_MODE_COMPACT))
        }
    }
}
