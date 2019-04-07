package ch.keepcalm.demo.oauth2.auth

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer
import org.springframework.security.oauth2.provider.ClientDetailsService
import org.springframework.security.oauth2.provider.token.DefaultTokenServices
import org.springframework.security.oauth2.provider.token.TokenStore
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore
import org.springframework.security.oauth2.provider.token.store.KeyStoreKeyFactory

import javax.sql.DataSource
import java.security.KeyPair

@Configuration
@EnableAuthorizationServer
@EnableConfigurationProperties(SecurityProperties::class)
class AuthorizationServerConfiguration(private val dataSource: DataSource, private val passwordEncoder: PasswordEncoder,
                                       private val authenticationManager: AuthenticationManager, private val securityProperties: SecurityProperties) : AuthorizationServerConfigurerAdapter() {

    private var jwtAccessTokenConverter: JwtAccessTokenConverter? = null
    private var tokenStore: TokenStore? = null

    @Bean
    fun tokenStore(): TokenStore {
        if (tokenStore == null) {
            tokenStore = JwtTokenStore(jwtAccessTokenConverter())
        }
        return tokenStore as TokenStore
    }

    @Bean
    fun tokenServices(tokenStore: TokenStore,
                      clientDetailsService: ClientDetailsService): DefaultTokenServices {
        val tokenServices = DefaultTokenServices()
        tokenServices.setSupportRefreshToken(true)
        tokenServices.setTokenStore(tokenStore)
        tokenServices.setClientDetailsService(clientDetailsService)
        tokenServices.setAuthenticationManager(this.authenticationManager)
        return tokenServices
    }

    @Bean
    fun jwtAccessTokenConverter(): JwtAccessTokenConverter {
        if (jwtAccessTokenConverter != null) {
            return jwtAccessTokenConverter as JwtAccessTokenConverter
        }

        val jwtProperties = securityProperties.jwt
        val keyPair = keyPair(jwtProperties!!, keyStoreKeyFactory(jwtProperties))

        jwtAccessTokenConverter = JwtAccessTokenConverter()
        jwtAccessTokenConverter!!.setKeyPair(keyPair)
        return jwtAccessTokenConverter as JwtAccessTokenConverter
    }

    @Throws(Exception::class)
    override fun configure(clients: ClientDetailsServiceConfigurer) {
        clients.jdbc(this.dataSource)
    }

    override fun configure(endpoints: AuthorizationServerEndpointsConfigurer) {
        endpoints.authenticationManager(this.authenticationManager)
                .accessTokenConverter(jwtAccessTokenConverter())
                .tokenStore(tokenStore())
    }

    override fun configure(oauthServer: AuthorizationServerSecurityConfigurer) {
        oauthServer.passwordEncoder(this.passwordEncoder).tokenKeyAccess("permitAll()")
                .checkTokenAccess("isAuthenticated()")
    }

    private fun keyPair(jwtProperties: SecurityProperties.JwtProperties, keyStoreKeyFactory: KeyStoreKeyFactory): KeyPair {
        return keyStoreKeyFactory.getKeyPair(jwtProperties.keyPairAlias, jwtProperties.keyPairPassword!!.toCharArray())
    }

    private fun keyStoreKeyFactory(jwtProperties: SecurityProperties.JwtProperties): KeyStoreKeyFactory {
        return KeyStoreKeyFactory(jwtProperties.keyStore, jwtProperties.keyStorePassword!!.toCharArray())
    }
}
