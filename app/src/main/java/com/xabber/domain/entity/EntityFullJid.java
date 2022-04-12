package com.xabber.domain.entity;

/**
 * An XMPP address (JID) consisting of a localpart, domainpart and resourcepart. For example
 * "user@xmpp.org/resource".
 * <p>
 * Examples:
 * </p>
 * <ul>
 * <li><code>localpart@domain.part/resource</code></li>
 * <li><code>juliet@xmpp.org/balcony</code></li>
 * </ul>
 *
 * @see Jid
 */
public interface EntityFullJid extends Jid, com.xabber.domain.entity.FullJid, EntityJid {

}
