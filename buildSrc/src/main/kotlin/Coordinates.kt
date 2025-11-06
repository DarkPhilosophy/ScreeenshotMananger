import java.util.Properties
import java.io.FileInputStream

/**
* Configuration of project coordinates (App ID, version, etc.)
*/
object Coordinates {
private val properties = Properties().apply {
    val versionFile = java.io.File("../../version.properties")
    if (versionFile.exists()) {
        FileInputStream(versionFile).use { load(it) }
    }
    }

    const val APP_ID = "com.ko.app"

    val APP_VERSION_NAME = properties.getProperty("version", "1.0.0")
    const val APP_VERSION_CODE = 1

    const val MIN_SDK = 24
    const val TARGET_SDK = 36
    const val COMPILE_SDK = 36
}
