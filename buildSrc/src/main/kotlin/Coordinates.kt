import java.io.FileInputStream
import java.util.Properties

/**
* Configuration of project coordinates (App ID, version, etc.)
*/
object Coordinates {
private val properties = Properties().apply {
    val versionFile = java.io.File("../../local.properties")
    if (versionFile.exists()) {
        FileInputStream(versionFile).use { load(it) }
    }
    }

    const val APP_PACKAGE =
        "com.araara.screenapp"  // Full package name for namespace and applicationId

    val APP_VERSION_NAME =
        properties.getProperty("version", "1.0.0") ?: "1.0.0"
    val APP_VERSION_CODE =
        properties.getProperty("code", "1").toInt()

    const val MIN_SDK = 24
    const val TARGET_SDK = 36
    const val COMPILE_SDK = 36
}
