package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fantasyidler.ui.theme.ScaledSheetContent

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DebugModifyAmountSheet(
    initialId: String = "",
    initialAmount: String = "",
    onAddAmount: (name: String, amount: Long) -> Unit,
    onSetAmount: (name: String, amount: Long) -> Unit,
    onRemoveAmount: (name: String, amount: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var draftID by remember { mutableStateOf(initialId) }
    var draftAmountText by remember { mutableStateOf(initialAmount) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        dragHandle       = { BottomSheetDefaults.DragHandle() },
    ) {
        ScaledSheetContent {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text  = "Debug: Modify Item Count",
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value         = draftID,
                onValueChange = { draftID = it },
                label         = { Text("Item ID") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value         = draftAmountText,
                onValueChange = { new ->
                    draftAmountText = new.filter { it.isDigit() }
                },
                label         = { Text("Item Amount") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
       

            HorizontalDivider()

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onRemoveAmount(draftID.trim(), draftAmountText.toLong()) },
                    enabled = draftID.isNotBlank() && draftAmountText.isNotBlank() && draftAmountText.toLong() > 0,
                ) {
                    Text("Remove")
                }
                Button(
                    onClick  = { onAddAmount(draftID.trim(), draftAmountText.toLong()) },
                    enabled  = draftID.isNotBlank() && draftAmountText.isNotBlank() && draftAmountText.toLong() > 0,
                ) {
                    Text("Add")
                }
                Button(
                    onClick = { onSetAmount(draftID.trim(), draftAmountText.toLong()) },
                    enabled = draftID.isNotBlank() && draftAmountText.isNotBlank() && draftAmountText.toLong() > 0
                ) {
                    Text("Set")
                }
            }
        }
        }
    }
}
