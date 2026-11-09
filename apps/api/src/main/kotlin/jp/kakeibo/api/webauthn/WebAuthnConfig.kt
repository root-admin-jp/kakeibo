package jp.kakeibo.api.webauthn

import com.yubico.webauthn.CredentialRepository
import com.yubico.webauthn.RelyingParty
import com.yubico.webauthn.data.RelyingPartyIdentity
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * パスキー (WebAuthn) の RP 設定 (ADR-0014)。
 * rp-id / origins は環境変数で差し替える（application.yml の kakeibo.webauthn）。
 * origins はポート付きの完全なオリジンで照合する (allowOriginPort=true)。
 */
@Configuration
class WebAuthnConfig(
    private val credentialRepository: CredentialRepository,
    @Value("\${kakeibo.webauthn.rp-id}") private val rpId: String,
    @Value("\${kakeibo.webauthn.rp-name}") private val rpName: String,
    @Value("\${kakeibo.webauthn.origins}") private val originsRaw: String,
) {
    @Bean
    fun relyingParty(): RelyingParty = RelyingParty.builder()
        .identity(RelyingPartyIdentity.builder().id(rpId).name(rpName).build())
        .credentialRepository(credentialRepository)
        .origins(originsRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet())
        .allowOriginPort(true)
        .build()
}
