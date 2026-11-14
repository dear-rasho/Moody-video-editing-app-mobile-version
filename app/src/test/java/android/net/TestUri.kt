package android.net

import android.os.Parcel

internal class TestUri : Uri() {
    override fun buildUpon(): Uri.Builder = Uri.Builder()
    override fun getAuthority(): String? = null
    override fun getEncodedAuthority(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getEncodedPath(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getEncodedSchemeSpecificPart(): String = toString()
    override fun getEncodedUserInfo(): String? = null
    override fun getFragment(): String? = null
    override fun getHost(): String? = null
    override fun getLastPathSegment(): String? = null
    override fun getPath(): String? = null
    override fun getPathSegments(): List<String> = emptyList()
    override fun getPort(): Int = -1
    override fun getQuery(): String? = null
    override fun getScheme(): String = "test"
    override fun getSchemeSpecificPart(): String = "media"
    override fun getUserInfo(): String? = null
    override fun isHierarchical(): Boolean = true
    override fun isRelative(): Boolean = false
    override fun toString(): String = "test://media"
    override fun describeContents(): Int = 0
    override fun writeToParcel(destination: Parcel, flags: Int) = Unit
}
