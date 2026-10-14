package com.vastutalks.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vastutalks.app.data.ai.AgentLanguage
import com.vastutalks.app.data.ai.AgentLanguagePref
import com.vastutalks.app.ui.theme.VastuPrimary

/**
 * "EN | हिं" switch for the language Ananya replies in (and the agent
 * screens use). Takes effect from her next reply.
 */
@Composable
fun LanguageSwitch(modifier: Modifier = Modifier, onChanged: (AgentLanguage) -> Unit = {}) {
    val current = AgentLanguagePref.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(CallTones.Surface)
            .border(1.dp, CallTones.Hairline, RoundedCornerShape(18.dp))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(AgentLanguage.ENGLISH to "EN", AgentLanguage.HINDI to "हिं").forEach { (language, label) ->
            val selected = current == language
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 32.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (selected) VastuPrimary else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton) {
                        if (!selected) {
                            AgentLanguagePref.set(language)
                            onChanged(language)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (selected) Color.White else CallTones.TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
