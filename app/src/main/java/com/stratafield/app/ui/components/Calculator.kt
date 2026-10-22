package com.stratafield.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stratafield.app.ui.theme.*
import java.text.DecimalFormat

@Composable
fun Calculator(
    modifier: Modifier = Modifier,
    onResult: (String) -> Unit = {}
) {
    var display by remember { mutableStateOf("0") }
    var previousValue by remember { mutableStateOf<Double?>(null) }
    var operation by remember { mutableStateOf<String?>(null) }
    var waitingForOperand by remember { mutableStateOf(false) }

    val format = remember { DecimalFormat("#.##") }

    fun calculate() {
        val current = display.toDoubleOrNull() ?: 0.0
        val prev = previousValue ?: return
        val result = when (operation) {
            "+" -> prev + current
            "-" -> prev - current
            "×" -> prev * current
            "÷" -> if (current != 0.0) prev / current else 0.0
            "%" -> prev % current
            else -> current
        }
        display = format.format(result)
        previousValue = null
        operation = null
        waitingForOperand = true
        onResult(display)
    }

    fun inputDigit(digit: String) {
        if (waitingForOperand) {
            display = digit
            waitingForOperand = false
        } else {
            display = if (display == "0") digit else display + digit
        }
    }

    fun inputDecimal() {
        if (waitingForOperand) {
            display = "0."
            waitingForOperand = false
        } else if (!display.contains(".")) {
            display += "."
        }
    }

    fun setOperation(op: String) {
        val current = display.toDoubleOrNull() ?: 0.0
        if (previousValue != null && !waitingForOperand) {
            calculate()
        }
        previousValue = current
        operation = op
        waitingForOperand = true
    }

    fun clear() {
        display = "0"
        previousValue = null
        operation = null
        waitingForOperand = false
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // Display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(16.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = display,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Buttons grid
        val buttons = listOf(
            listOf("C", "±", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "-"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "⌫", "=")
        )

        buttons.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { label ->
                    val isOperator = label in listOf("+", "-", "×", "÷", "=", "%")
                    val isFunction = label in listOf("C", "±", "⌫")
                    val isEquals = label == "="

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .background(
                                when {
                                    isEquals -> NothingRed
                                    isOperator -> MaterialTheme.colorScheme.primary
                                    isFunction -> MaterialTheme.colorScheme.outlineVariant
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                when (label) {
                                    "C" -> clear()
                                    "±" -> {
                                        val value = display.toDoubleOrNull() ?: 0.0
                                        display = format.format(-value)
                                    }
                                    "⌫" -> {
                                        if (display.length > 1) {
                                            display = display.dropLast(1)
                                        } else {
                                            display = "0"
                                        }
                                    }
                                    "=" -> calculate()
                                    "." -> inputDecimal()
                                    in listOf("+", "-", "×", "÷", "%") -> setOperation(label)
                                    else -> inputDigit(label)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isEquals -> NothingWhite
                                isOperator -> MaterialTheme.colorScheme.onPrimary
                                isFunction -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
    }
}