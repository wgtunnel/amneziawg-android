import java.io.File

fun getLocalProperty(key: String, file: String = "local.properties"): String? {
    return try {
        val properties = java.util.Properties()
        val localProperties = java.io.File(file)
        if (!localProperties.isFile) return null

        java.io.InputStreamReader(java.io.FileInputStream(localProperties), Charsets.UTF_8).use { reader ->
            properties.load(reader)
        }

        properties.getProperty(key)
    } catch (e: Exception) {
        null
    }
}
