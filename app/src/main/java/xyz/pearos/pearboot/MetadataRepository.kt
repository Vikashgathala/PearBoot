package xyz.pearos.pearboot.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object MetadataRepository {

    private const val METADATA_URL =
        "http://140.245.22.155:6969/metadata/latest.json"

    suspend fun fetch(): Metadata? = withContext(Dispatchers.IO) {
        try {
            val json = URL(METADATA_URL).readText()
            val root = JSONObject(json)

            Metadata(
                project = root.getString("project"),
                distro = root.getJSONObject("distro").run {
                    Distro(
                        name = getString("name"),
                        arch = getString("arch")
                    )
                },
                version = root.getJSONObject("version").run {
                    Version(
                        code = getInt("code"),
                        name = getString("name"),
                        mandatory = getBoolean("mandatory")
                    )
                },
                file = root.getJSONObject("file").run {
                    FileInfo(
                        name = getString("name"),
                        size_bytes = getLong("size_bytes"),
                        sha256 = getString("sha256")
                    )
                },
                download = root.getJSONObject("download").run {
                    DownloadInfo(
                        url = getString("url"),
                        resume = getBoolean("resume")
                    )
                },
                release = root.getJSONObject("release").run {
                    Release(
                        date = getString("date"),
                        changelog = getString("changelog")
                    )
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
