package com.xabber.domain.entity;


/**
 * An XMPP address (JID) which has no {@link Resourcepart}. Either
 * {@link EntityBareJid} or {@link DomainBareJid}.
 * <p>
 * Examples:
 * </p>
 * <ul>
 * <li><code>localpart@domain.part</code></li>
 * <li><code>domain.part</code></li>
 * </ul>
 *
 * @see Jid
 */
public interface BareJid extends Jid {

}
