package com.xabber.domain.entity;


/**
 * An XMPP address (JID) consisting of the domainpart. For example "xmpp.org".
 * <p>
 * Examples:
 * </p>
 * <ul>
 * <li><code>xmpp.org</code></li>
 * <li><code>example.net</code></li>
 * </ul>
 *
 * @see Jid
 */
public interface DomainBareJid extends Jid, BareJid, DomainJid {

}
