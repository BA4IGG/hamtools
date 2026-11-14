package com.ham.tools.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ham.tools.data.remote.UserProfile
import com.ham.tools.data.remote.UserSession
import com.ham.tools.data.update.UpdateChannel
import com.ham.tools.ui.theme.DEFAULT_ACCENT_SEED
import com.ham.tools.ui.theme.M3Variant
import com.ham.tools.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val SETTINGS_DATASTORE = "ham_settings"

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_DATASTORE
)

/** 用户偏好设置的读写入口。 */
class UserPreferences(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ACCENT_SEED = intPreferencesKey("accent_seed")
        val ACCENT_VARIANT = intPreferencesKey("accent_variant")
        val REFRESH_BOOST = booleanPreferencesKey("refresh_boost")
        val SESSION_TOKEN = stringPreferencesKey("session_token")
        val SESSION_CALLSIGN = stringPreferencesKey("session_callsign")
        val SESSION_NICKNAME = stringPreferencesKey("session_nickname")
        val SESSION_REGION = stringPreferencesKey("session_region")
        val SESSION_CREATED_AT = stringPreferencesKey("session_created_at")
        // QSL 交换通知去重：记录上一次已提醒过的最大 incoming 申请 ID
        val QSL_LAST_INCOMING_ID = longPreferencesKey("qsl_last_incoming_id")
        // 检测更新：自动检测节流时间、用户跳过过的 tag、上次看到的最新版本号、发布通道
        val UPDATE_LAST_CHECKED_AT = longPreferencesKey("update_last_checked_at")
        val UPDATE_SKIPPED_TAG = stringPreferencesKey("update_skipped_tag")
        val UPDATE_LATEST_VERSION = stringPreferencesKey("update_latest_version")
        val UPDATE_CHANNEL = stringPreferencesKey("update_channel")
    }

    val themeMode: Flow<ThemeMode> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            preferences[Keys.THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM
        }

    val dynamicColor: Flow<Boolean> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences[Keys.DYNAMIC_COLOR] ?: true }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences -> preferences[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.DYNAMIC_COLOR] = enabled }
    }

    /** 自定义主题色的种子色（ARGB），由它派生整套 MD3 配色。 */
    val accentSeed: Flow<Int> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences[Keys.ACCENT_SEED] ?: DEFAULT_ACCENT_SEED }

    suspend fun setAccentSeed(seed: Int) {
        dataStore.edit { preferences -> preferences[Keys.ACCENT_SEED] = seed }
    }

    /** 配色方案变体在 [M3Variant] 中的序号。 */
    val accentVariant: Flow<M3Variant> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            M3Variant.entries.getOrNull(preferences[Keys.ACCENT_VARIANT] ?: 0)
                ?: M3Variant.TONAL_SPOT
        }

    suspend fun setAccentVariant(variant: M3Variant) {
        dataStore.edit { preferences -> preferences[Keys.ACCENT_VARIANT] = variant.ordinal }
    }

    /** 一次性写入种子色与方案变体，避免两次磁盘写入。 */
    suspend fun setAccent(seed: Int, variant: M3Variant) {
        dataStore.edit { preferences ->
            preferences[Keys.ACCENT_SEED] = seed
            preferences[Keys.ACCENT_VARIANT] = variant.ordinal
        }
    }

    /**
     * 用户是否允许本 App 改系统刷新率策略。
     *
     * 默认关闭：改 min_refresh_rate 会影响整机的耗电与发热，
     * 而且需要 WRITE_SETTINGS 授权，不该替用户做这个决定。
     */
    val refreshBoost: Flow<Boolean> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences[Keys.REFRESH_BOOST] ?: false }

    suspend fun setRefreshBoost(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.REFRESH_BOOST] = enabled }
    }

    /** 本地登录态。令牌为空表示未登录。 */
    val session: Flow<UserSession?> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            val token = preferences[Keys.SESSION_TOKEN]
            val callsign = preferences[Keys.SESSION_CALLSIGN]
            if (token.isNullOrBlank() || callsign.isNullOrBlank()) {
                null
            } else {
                UserSession(
                    token = token,
                    profile = UserProfile(
                        callsign = callsign,
                        nickname = preferences[Keys.SESSION_NICKNAME] ?: "",
                        region = preferences[Keys.SESSION_REGION]?.takeIf { it.isNotBlank() },
                        createdAt = preferences[Keys.SESSION_CREATED_AT] ?: ""
                    )
                )
            }
        }

    suspend fun saveSession(session: UserSession) {
        dataStore.edit { preferences ->
            preferences[Keys.SESSION_TOKEN] = session.token
            preferences[Keys.SESSION_CALLSIGN] = session.profile.callsign
            preferences[Keys.SESSION_NICKNAME] = session.profile.nickname
            preferences[Keys.SESSION_REGION] = session.profile.region ?: ""
            preferences[Keys.SESSION_CREATED_AT] = session.profile.createdAt
        }
    }

    suspend fun clearSession() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.SESSION_TOKEN)
            preferences.remove(Keys.SESSION_CALLSIGN)
            preferences.remove(Keys.SESSION_NICKNAME)
            preferences.remove(Keys.SESSION_REGION)
            preferences.remove(Keys.SESSION_CREATED_AT)
        }
    }

    /** 上一次已提醒过的 QSL 申请 ID，用于避免同一条申请反复弹通知。 */
    val qslLastIncomingId: Flow<Long> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences[Keys.QSL_LAST_INCOMING_ID] ?: 0L }

    suspend fun setQslLastIncomingId(id: Long) {
        dataStore.edit { preferences -> preferences[Keys.QSL_LAST_INCOMING_ID] = id }
    }

    /** 上次检测更新的时间戳（毫秒）。自动检测距此不足 12 小时就不再请求 GitHub。 */
    val updateLastCheckedAt: Flow<Long> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences[Keys.UPDATE_LAST_CHECKED_AT] ?: 0L }

    suspend fun setUpdateLastCheckedAt(millis: Long) {
        dataStore.edit { preferences -> preferences[Keys.UPDATE_LAST_CHECKED_AT] = millis }
    }

    /** 用户点过「跳过此版本」的 tag；只抑制自动弹窗，手动检测仍会显示结果。 */
    val updateSkippedTag: Flow<String> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences[Keys.UPDATE_SKIPPED_TAG] ?: "" }

    suspend fun setUpdateSkippedTag(tag: String) {
        dataStore.edit { preferences -> preferences[Keys.UPDATE_SKIPPED_TAG] = tag }
    }

    /** 上次成功检测到的最新版本号，网络失败时关于页仍可显示「上次看到 vX.Y」。 */
    val updateLatestVersion: Flow<String> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences[Keys.UPDATE_LATEST_VERSION] ?: "" }

    suspend fun setUpdateLatestVersion(version: String) {
        dataStore.edit { preferences -> preferences[Keys.UPDATE_LATEST_VERSION] = version }
    }

    /**
     * 发布通道偏好，默认正式通道。老版本没有这个键，读出来也自然回落到 STABLE，
     * 不会把历史 beta 用户莫名其妙切到测试通道。
     */
    val updateChannel: Flow<UpdateChannel> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> UpdateChannel.fromKey(preferences[Keys.UPDATE_CHANNEL]) }

    suspend fun setUpdateChannel(channel: UpdateChannel) {
        dataStore.edit { preferences -> preferences[Keys.UPDATE_CHANNEL] = channel.key }
    }
}
