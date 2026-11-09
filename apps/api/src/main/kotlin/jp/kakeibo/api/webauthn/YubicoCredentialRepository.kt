package jp.kakeibo.api.webauthn

import com.yubico.webauthn.CredentialRepository
import com.yubico.webauthn.RegisteredCredential
import com.yubico.webauthn.data.ByteArray
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor
import jp.kakeibo.api.db.PasskeyCredentialRow
import jp.kakeibo.api.db.PasskeyRepository
import jp.kakeibo.api.db.UserRepository
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.Optional

/**
 * Yubico ライブラリの CredentialRepository を自前 DB (passkey_credentials) に接続する。
 * ユーザー名はメールアドレス、userHandle はユーザー ID の文字列バイト列を使う。
 */
@Component
class YubicoCredentialRepository(
    private val users: UserRepository,
    private val passkeys: PasskeyRepository,
) : CredentialRepository {

    override fun getCredentialIdsForUsername(username: String): Set<PublicKeyCredentialDescriptor> {
        val user = users.findByEmail(username) ?: return emptySet()
        return passkeys.listByUser(user.id).map {
            PublicKeyCredentialDescriptor.builder().id(ByteArray.fromBase64Url(it.credentialId)).build()
        }.toSet()
    }

    override fun getUserHandleForUsername(username: String): Optional<ByteArray> =
        Optional.ofNullable(users.findByEmail(username)?.let { handleOf(it.id) })

    override fun getUsernameForUserHandle(userHandle: ByteArray): Optional<String> =
        Optional.ofNullable(userIdOf(userHandle)?.let { users.findById(it)?.email })

    override fun lookup(credentialId: ByteArray, userHandle: ByteArray): Optional<RegisteredCredential> {
        val row = passkeys.findByCredentialId(credentialId.base64Url) ?: return Optional.empty()
        val uid = userIdOf(userHandle) ?: return Optional.empty()
        if (row.userId != uid) return Optional.empty()
        return Optional.of(row.toRegisteredCredential())
    }

    override fun lookupAll(credentialId: ByteArray): Set<RegisteredCredential> =
        listOfNotNull(passkeys.findByCredentialId(credentialId.base64Url)?.toRegisteredCredential()).toSet()

    private fun PasskeyCredentialRow.toRegisteredCredential(): RegisteredCredential =
        RegisteredCredential.builder()
            .credentialId(ByteArray.fromBase64Url(credentialId))
            .userHandle(handleOf(userId))
            .publicKeyCose(ByteArray.fromBase64Url(publicKeyCose))
            .signatureCount(signCount)
            .build()

    companion object {
        fun handleOf(userId: Long): ByteArray = ByteArray(userId.toString().toByteArray(StandardCharsets.UTF_8))

        fun userIdOf(handle: ByteArray): Long? =
            String(handle.bytes, StandardCharsets.UTF_8).toLongOrNull()
    }
}
