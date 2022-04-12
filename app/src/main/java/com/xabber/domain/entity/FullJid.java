package com.xabber.domain.entity;


/**
 * An XMPP address (JID) which has a {@link Resourcepart}. Either {@link EntityFullJid} or {@link DomainFullJid}.
 * <p>
 * Examples:
 * </p>
 * <ul>
 * <li><code>localpart@domain.part/resourcepart</code></li>
 * <li><code>domain.part/resourcepart</code></li>
 * </ul>
 *
 * @see Jid
 */
interface FullJid extends Jid {

    /**
     * Return the {@link Resourcepart} of this JID.
     *
     * @return the resourcepart.
     */
    Resourcepart getResourcepart();

}
