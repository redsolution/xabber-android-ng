package com.xabber.domain.entity;

/**
 * An XMPP address (JID) which has no {@link Localpart}. Either
 * {@link DomainBareJid} or {@link DomainFullJid}.
 * <p>
 * Examples:
 * </p>
 * <ul>
 * <li><code>domain.part</code></li>
 * <li><code>domain.part/resourcepart</code></li>
 * </ul>
 *
 * @see Jid
 */
public interface DomainJid extends Jid {

}
