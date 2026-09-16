package com.example.nodechain.ui.common

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction

/**
 * 编辑即保存的输入框。
 *
 * 文本的真实来源是本地 state，而不是 store 里的值——store 是通过 StateFlow 回流的，
 * 慢一帧，直接双向绑定会在快速输入时丢字符、跳光标。这里本地先行、顺带通知 store。
 *
 * [identity] 变化时重置内容，用来区分"换了一个节点/选项"和"同一个框里改字"。
 */
@Composable
fun AutoSaveTextField(
    initial: String,
    identity: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    imeAction: ImeAction = ImeAction.Default,
) {
    var text by rememberSaveable(identity) { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        modifier = modifier,
    )
}
