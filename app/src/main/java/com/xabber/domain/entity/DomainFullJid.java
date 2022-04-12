package com.xabber.domain.entity;


/**
 * An XMPP address (JID) consisting of a domainpart and a resourcepart. For example
 * "xmpp.org/resource".
 * <p>
 * Examples:
 * </p>
 * <ul>
 * <li><code>domain.part/resource</code></li>
 * <li><code>example.net/8c6def89</code></li>
 * </ul>
 *
 * @see Jid
 */
public interface DomainFullJid extends Jid, com.xabber.domain.entity.FullJid, DomainJid {

}