package org.apereo.cas.ticket.accesstoken;

import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.configuration.support.Beans;
import org.apereo.cas.ticket.ExpirationPolicy;
import org.apereo.cas.ticket.ExpirationPolicyBuilder;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * This is {@link AccessTokenExpirationPolicyBuilder}.
 *
 * @author Misagh Moayyed
 * @since 6.1.0
 */
@RequiredArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@ToString
@Slf4j
@Getter
public class AccessTokenExpirationPolicyBuilder implements ExpirationPolicyBuilder<AccessToken> {
    private static final long serialVersionUID = -3597980180617072826L;
    /**
     * The Cas properties.
     */
    protected final CasConfigurationProperties casProperties;

    @Override
    public ExpirationPolicy buildTicketExpirationPolicy() {
        return toTicketExpirationPolicy();
    }

    @Override
    public Class<AccessToken> getTicketType() {
        return AccessToken.class;
    }

    /**
     * To ticket expiration policy.
     *
     * @return the expiration policy
     */
    public ExpirationPolicy toTicketExpirationPolicy() {
        val oauth = casProperties.getAuthn().getOauth().getAccessToken();
        if (casProperties.getLogout().isRemoveDescendantTickets()) {
            return new OAuthAccessTokenExpirationPolicy(
                Beans.newDuration(oauth.getMaxTimeToLiveInSeconds()).getSeconds(),
                Beans.newDuration(oauth.getTimeToKillInSeconds()).getSeconds()
            );
        }
        return new OAuthAccessTokenExpirationPolicy.OAuthAccessTokenSovereignExpirationPolicy(
            Beans.newDuration(oauth.getMaxTimeToLiveInSeconds()).getSeconds(),
            Beans.newDuration(oauth.getTimeToKillInSeconds()).getSeconds()
        );
    }
}
