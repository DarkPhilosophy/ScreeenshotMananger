import java.io.FileInputStream
import java.util.Properties

/**
* Configuration of project coordinates (App ID, version, etc.)
*/
object Coordinates {
private val properties = Properties().apply {
    val versionFile = java.io.File("local.properties")
    if (versionFile.exists()) {
        FileInputStream(versionFile).use { load(it) }
    }
    }

    val APP_PACKAGE =
        properties.getProperty("app.package.name", "ro.snapify")
            ?: "ro.snapify"  // Full package name for namespace and applicationId

    val APP_VERSION_NAME =
        properties.getProperty("app.version", "1.0.0") ?: "1.0.0"
    val APP_VERSION_CODE =
        properties.getProperty("app.code", "1").toInt()

    val MIN_SDK =
        properties.getProperty("app.sdk.min", "24").toInt()
    val TARGET_SDK =
        properties.getProperty("app.sdk.target", "36").toInt()
    val COMPILE_SDK =
        properties.getProperty("app.sdk.compile", "36").toInt()
}
