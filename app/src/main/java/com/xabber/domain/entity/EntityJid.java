package com.xabber.domain.entity;

/**
 * An XMPP address (JID) which has a {@link Localpart}. Either {@link EntityBareJid} or {@link EntityFullJid}.
 * <p>
 * Examples:
 * </p>
 * <ul>
 * <li><code>localpart@domain.part</code></li>
 * <li><code>localpart@domain.part/resourcepart</code></li>
 * </ul>
 *
 * @see Jid
 */
public interface EntityJid extends Jid {

    /**
     * Return the {@link Localpart} of this JID.
     *
     * @return the localpart.
     */
    Localpart getLocalpart();

    /**
     * Return the bare JID of this entity JID.
     *
     * @return the bare JID.
     */
    EntityBareJid asEntityBareJid();

    /**
     * Return the bare JID string of this full JID.
     *
     * @return the bare JID string.
     */
    String asEntityBareJidString();

}
