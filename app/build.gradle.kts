import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 正式签名配置。keystore.properties 与密钥库都不入库（见 .gitignore），
// 没配的人照样能编 debug，只是 release 包会是未签名的。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasSigningConfig = keystorePropertiesFile.exists()

android {

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = keystoreProperties["storeFile"]?.let { rootProject.file(it as String) }
                storePassword = keystoreProperties["storePassword"] as String?
                keyAlias = keystoreProperties["keyAlias"] as String?
                keyPassword = keystoreProperties["keyPassword"] as String?
            }
        }
    }
    namespace = "com.ham.tools"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.ham.tools"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            // 这里**不要**打开 optimization（R8 优化）！！
            //
            // 曾经的 release 配置是 optimization { enable = true; packageScope = ... }，
            // 结果装包后一启动就崩：packageScope 只把 androidx/kotlin/kotlinx 划进优化域，
            // 而本 App 的 com.ham.tools 代码被排除在外，形成「库侧字节码被 R8 改写、
            // 调用方代码却不受 R8 管辖」的混合状态。编出的 dex 里
            // androidx.compose.runtime.Composer、ui.graphics.Shape、ui.text.AnnotatedString
            // 等核心类型已无类定义，只剩悬空引用，运行时直接 NoClassDefFoundError。
            // debug 构建不走这一步，所以一切正常——这也正是「debug 好、release 崩」的全部差异。
            //
            // 本 App 体积本就只有几 MB，省下的那点空间远不值得冒启动崩溃的风险。
            // 保持 release 与 debug 走同一套字节码，行为一致。
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
