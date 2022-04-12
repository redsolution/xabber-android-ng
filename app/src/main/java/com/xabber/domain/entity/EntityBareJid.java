package com.xabber.domain.entity;

/**
 * An XMPP address (JID) consisting of a localpart and a domainpart. For example
 * "user@xmpp.org".
 * <p>
 * Examples:
 * </p>
 * <ul>
 * <li><code>localpart@domain.part</code></li>
 * <li><code>user@example.net</code></li>
 * </ul>
 *
 * @see Jid
 */
public interface EntityBareJid extends Jid, EntityJid, BareJid {

}
