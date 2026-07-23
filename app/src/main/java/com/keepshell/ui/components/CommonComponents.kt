package com.keepshell.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepshell.ui.theme.Ink
import com.keepshell.ui.theme.Line
import com.keepshell.ui.theme.Muted
import com.keepshell.ui.theme.Online
import com.keepshell.ui.theme.Signal
import com.keepshell.ui.theme.SignalSoft
import com.keepshell.ui.theme.SurfaceSoft

@Composable
fun KeepShellLogo(modifier: Modifier = Modifier, compact: Boolean = false) {
    val size = if (compact) 32.dp else 38.dp
    Box(
        modifier = modifier
            .size(size)
            .background(Ink, RoundedCornerShape(if (compact) 7.dp else 9.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = ">_",
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 13.sp else 15.sp
        )
    }
}

@Composable
fun StatusDot(
    color: Color = Online,
    modifier: Modifier = Modifier,
    pulse: Boolean = true
) {
    Box(
        modifier = modifier
            .size(if (pulse) 10.dp else 8.dp)
            .background(color.copy(alpha = if (pulse) 0.16f else 1f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (pulse) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(color, CircleShape)
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = Muted, fontSize = 12.sp)
    }
}

@Composable
fun FormSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        color = Muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    loading: Boolean = false,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Signal,
            contentColor = Color.White,
            disabledContainerColor = Signal.copy(alpha = 0.55f)
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    loading: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(50.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        enabled = !loading,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Signal,
                strokeWidth = 2.dp
            )
        } else if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun InfoStrip(
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    tint: Color = Signal,
    background: Color = SignalSoft
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

@Composable
fun SoftIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = Muted
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .background(SurfaceSoft, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
    }
}

@Composable
fun LinedSection(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        HorizontalDivider(color = Line)
        content()
        HorizontalDivider(color = Line)
    }
}
