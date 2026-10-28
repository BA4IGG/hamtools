package com.ham.tools.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ham.tools.BuildConfig
import com.ham.tools.R
import com.ham.tools.data.AppConfig
import com.ham.tools.data.remote.UserSession
import com.ham.tools.ui.theme.HamToolsTheme
import com.ham.tools.ui.theme.ThemeMode
import com.ham.tools.util.Callsign

/**
 * 「我的」页：账号、作者信息与应用信息。
 *
 * 未登录时给出登录入口；已登录时展示呼号名片并可退出登录。
 * 账号服务器地址固定在 AppConfig.SERVER_BASE_URL，界面上不提供修改入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    session: UserSession?,
    onLoginClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var logoutDialogVisible by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.profile_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (session == null) {
                GuestCard(onLoginClick = onLoginClick)
            } else {
                AccountCard(
                    session = session,
                    onLogoutClick = { logoutDialogVisible = true }
                )
            }

            AuthorCard()

            AppInfoCard(onSettingsClick = onSettingsClick)
        }
    }

    if (logoutDialogVisible) {
        AlertDialog(
            onDismissRequest = { logoutDialogVisible = false },
            title = { Text(text = stringResource(R.string.profile_logout_title)) },
            text = { Text(text = stringResource(R.string.profile_logout_body)) },
            confirmButton = {
                TextButton(onClick = {
                    logoutDialogVisible = false
                    onLogout()
                }) {
                    Text(text = stringResource(R.string.profile_action_logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { logoutDialogVisible = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun GuestCard(onLoginClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.PersonOutline,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primaryContainer
                )
            }
            Text(
                text = stringResource(R.string.profile_guest_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = stringResource(R.string.profile_guest_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onLoginClick,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Text(text = stringResource(R.string.profile_action_login))
            }
        }
    }
}

@Composable
private fun AccountCard(
    session: UserSession,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = session.profile
    val region = profile.region ?: Callsign.regionOf(profile.callsign)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = profile.callsign.take(2),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primaryContainer
                    )
                }
                Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(
                        text = profile.callsign,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = profile.nickname.ifBlank {
                            region ?: stringResource(R.string.profile_label_callsign)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    DetailRow(
                        label = stringResource(R.string.profile_label_callsign),
                        value = profile.callsign
                    )
                    DetailRow(
                        label = stringResource(R.string.profile_label_nickname),
                        value = profile.nickname.ifBlank {
                            stringResource(R.string.profile_label_nickname_unset)
                        }
                    )
                    DetailRow(
                        label = stringResource(R.string.profile_label_region),
                        value = region ?: "—"
                    )
                    DetailRow(
                        label = stringResource(R.string.profile_label_joined),
                        value = profile.createdAt.take(10).ifBlank { "—" }
                    )
                }
            }

            FilledTonalButton(
                onClick = onLogoutClick,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(
                    imageVector = Icons.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.profile_action_logout),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun AuthorCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Badge,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.profile_section_author),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(
                text = AppConfig.AUTHOR_NAME,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = AppConfig.AUTHOR_BIO,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (AppConfig.AUTHOR_CONTACT.isNotBlank()) {
                Text(
                    text = stringResource(
                        R.string.profile_author_contact,
                        AppConfig.AUTHOR_CONTACT
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AppInfoCard(
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            DetailRow(
                label = stringResource(R.string.settings_version),
                value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            )
            DetailRow(
                label = stringResource(R.string.settings_package),
                value = BuildConfig.APPLICATION_ID
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSettingsClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(
                        text = stringResource(R.string.profile_item_appearance),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.profile_item_appearance_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileGuestPreview() {
    HamToolsTheme(themeMode = ThemeMode.DARK) {
        ProfileScreen(
            session = null,
            onLoginClick = {},
            onSettingsClick = {},
            onLogout = {}
        )
    }
}
