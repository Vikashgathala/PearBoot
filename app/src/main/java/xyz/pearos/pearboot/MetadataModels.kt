package xyz.pearos.pearboot.data

data class Metadata(
    val project: String,
    val distro: Distro,
    val version: Version,
    val file: FileInfo,
    val download: DownloadInfo,
    val release: Release
)

data class Distro(
    val name: String,
    val arch: String
)

data class Version(
    val code: Int,
    val name: String,
    val mandatory: Boolean
)

data class FileInfo(
    val name: String,
    val size_bytes: Long,
    val sha256: String
)

data class DownloadInfo(
    val url: String,
    val resume: Boolean
)

data class Release(
    val date: String,
    val changelog: String
)
