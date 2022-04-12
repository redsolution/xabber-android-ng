package com.xabber.domain.entity;


import java.io.Serializable;

/**
 * An <b>XMPP address</b>, also known as JID (formerly for "Jabber Identifier"), which acts as globally unique address
 * within the XMPP network.
 * <p>
 * JIDs are created from {@link String} or {@link CharSequence} with the {@link JidCreate} utility.
 * </p>
 *
 * <pre>
 * Jid jid = JidCreate.from("juliet@capulet.org/balcony");
 * EntityBareJid bareJid = JidCreate.entityBareFrom("romeo@montague.net");
 * </pre>
 * <p>
 * This is the super interface for all JID types, which are constructed from two dimensions: Bare/Full and
 * Domain/Entity. Every JID consists at least of a {@link Domainpart}. Bare JID types do not come with a
 * {@link Resourcepart}, full JID types always have a {@link Resourcepart}. Domain JID types do not possess a
 * {@link Localpart}, whereas entity JID types always do.
 * </p>
 * <p>
 * The following table shows a few examples of JID types.
 * </p>
 * <table summary="XMPP Address Types">
 * <tr>
 * <td>Example</td>
 * <td>Type</td>
 * </tr>
 * <tr>
 * <td><code>example.org</code></td>
 * <td>{@link DomainBareJid}</td>
 * </tr>
 * <tr>
 * <td><code>example.org/resource</code></td>
 * <td>{@link DomainFullJid}</td>
 * </tr>
 * <tr>
 * <td><code>user@example.org</code></td>
 * <td>{@link EntityBareJid}</td>
 * </tr>
 * <tr>
 * <td><code>user@example.org/resource</code></td>
 * <td>{@link EntityFullJid}</td>
 * </tr>
 * </table>
 * <p>
 * You can retrieve the escaped String representing the Jid with {@link #toString()} or the unsecaped String of the JID
 * with {@link #asUnescapedString()}.
 * </p>
 *
 * @see <a href="http://xmpp.org/rfcs/rfc6120.html#arch-addresses">RFC 6120 (XMPP: Core) § 2.1 Global Addresses</a>
 * @see <a href="http://xmpp.org/rfcs/rfc6122.html#addressing-fundamentals">RFC 6122 (XMPP: Address Format) § 2.1
 * Fundamentals</a>
 */
public interface Jid extends Comparable<com.xabber.domain.entity.Jid>, CharSequence, Serializable {

    /**
     * Get the {@link Domainpart} of this Jid.
     *
     * @return the domainpart.
     */
    Domainpart getDomain();

    /**
     * Returns the String representation of this JID.
     *
     * @return the String representation of this JID.
     */
    String toString();

    /**
     * Return the <b>unescaped</b> String representation of this JID.
     * <p>
     * Since certain Unicode code points are disallowed in the localpart of a JID by the required stringprep profile,
     * those need to get escaped when used in a real JID. The unescaped representation of the JID is only for
     * presentation to a human user or for gatewaying to a non-XMPP system.
     * </p>
     * For example, if the users inputs {@code 'at&t guy@example.com'}, the escaped real JID created with
     * {@link JidCreate} will be {@code 'at\26t\20guy@example.com'}, which is what
     * {@link com.xabber.domain.entity.Jid#toString()} will return. But {@link com.xabber.domain.entity.Jid#asUnescapedString()} will return again
     * {@code 'at&t guy@example.com'}.
     *
     * @return the unescaped String representation of this JID.
     */
    String asUnescapedString();

    /**
     * Check if this is a {@link EntityBareJid} or {@link EntityFullJid}.
     *
     * @return true if this is an instance of BareJid or FullJid.
     */
    boolean isEntityJid();

    /**
     * Check if this is an instance of {@link EntityBareJid}.
     *
     * @return true if this is an instance of BareJid
     */
    boolean isEntityBareJid();

    /**
     * Check if this is an instance of {@link EntityFullJid}.
     *
     * @return true if this is an instance of FullJid
     */
    boolean isEntityFullJid();

    /**
     * Check if this is an instance of {@link DomainBareJid}.
     *
     * @return true if this is an instance of DomainBareJid
     */
    boolean isDomainBareJid();

    /**
     * Check if this is an instance of {@link DomainFullJid}.
     *
     * @return true if this is an instance of DomainFullJid
     */
    boolean isDomainFullJid();

    /**
     * Check if this is an instance of {@link EntityBareJid} or {@link DomainBareJid}.
     *
     * @return true if this is an instance of BareJid or DomainBareJid
     */
    boolean hasNoResource();

    /**
     * Check if this is a Jid with a {@link Resourcepart}.
     *
     * @return true if this Jid has a resourcepart.
     */
    boolean hasResource();

    /**
     * Check if this is a Jid with a {@link Localpart}.
     *
     * @return true if this Jid has a localpart.
     */
    boolean hasLocalpart();

    /**
     * Return a JID created by removing the Resourcepart from this JID.
     *
     * @return this Jid without a Resourcepart.
     */
    BareJid asBareJid();

    /**
     * Convert this Jid to a {@link EntityBareJid} if possible.
     *
     * @return the corresponding {@link EntityBareJid} or null.
     */
    EntityBareJid asEntityBareJidIfPossible();

    /**
     * Convert this Jid to a {@link EntityBareJid} or throw an {@code IllegalStateException} if this is not possible.
     *
     * @return the corresponding {@link EntityBareJid}.
     */
    EntityBareJid asEntityBareJidOrThrow();

    /**
     * Convert this Jid to a {@link EntityFullJid} if possible.
     *
     * @return the corresponding {@link EntityFullJid} or null.
     */
    EntityFullJid asEntityFullJidIfPossible();

    /**
     * Convert this Jid to a {@link EntityFullJid} or throw an {@code IllegalStateException} if this is not possible.
     *
     * @return the corresponding {@link EntityFullJid}.
     */
    EntityFullJid asEntityFullJidOrThrow();

    /**
     * Convert this Jid to a {@link EntityJid} if possible.
     *
     * @return the corresponding {@link EntityJid} or null.
     */
    EntityJid asEntityJidIfPossible();

    /**
     * Convert this Jid to a {@link EntityJid} or throw an {@code IllegalStateException} if this is not possible.
     *
     * @return the corresponding {@link EntityJid}.
     */
    EntityJid asEntityJidOrThrow();

    /**
     * Convert this Jid to a {@link FullJid} if possible.
     *
     * @return the corresponding {@link FullJid} or null.
     */
    FullJid asFullJidIfPossible();

    /**
     * Convert this Jid to a {@link FullJid} or throw an {@code IllegalStateException} if this is not possible.
     *
     * @return the corresponding {@link FullJid}.
     */
    EntityFullJid asFullJidOrThrow();

    /**
     * Convert this Jid to a {@link DomainBareJid}.
     * <p>
     * Note that it is always possible to convert a Jid to a DomainBareJid, since every Jid has a domain part.
     * </p>
     *
     * @return the corresponding DomainBareJid.
     */
    DomainBareJid asDomainBareJid();

    /**
     * Convert this Jid to a {@link DomainFullJid} if possible.
     *
     * @return the corresponding DomainFullJid or null.
     */
    DomainFullJid asDomainFullJidIfPossible();

    /**
     * Convert this Jid to a {@link DomainFullJid} or throw an {@code IllegalStateException} if this is not possible.
     *
     * @return the corresponding {@link DomainFullJid}.
     */
    DomainFullJid asDomainFullJidOrThrow();

    /**
     * Get the resourcepart of this JID or null.
     * <p>
     * If the JID is of form {@code <localpart@domain.example/resource>} then this method returns 'resource'. If the JID no
     * resourcepart, then <code>null</code> is returned.
     * </p>
     *
     * @return the resource of this JID or null.
     */
    Resourcepart getResourceOrNull();

    /**
     * Get the resourcepart of this JID or return the empty resourcepart.
     * <p>
     * If the JID is of form {@code <localpart@domain.example/resource>} then this method returns 'resource'. If the JID no
     * resourcepart, then {@link Resourcepart#EMPTY} is returned.
     * </p>
     *
     * @return the resource of this JID or the empty resourcepart.
     */
    Resourcepart getResourceOrEmpty();

    /**
     * Get the resourcepart of this JID or throw an {@code IllegalStateException}.
     * <p>
     * If the JID is of form {@code <localpart@domain.example/resource>} then this method returns 'resource'. If the JID no
     * resourcepart, then an {@code IllegalStateException} is thrown.
     * </p>
     *
     * @return the resource of this JID.
     */
    Resourcepart getResourceOrThrow();

    /**
     * Get the localpart of this JID or null.
     * <p>
     * If the JID is of form {@code <localpart@domain.example>} then this method returns 'localpart'. If the JID has no
     * localpart, then <code>null</code> is returned.
     * </p>
     *
     * @return the localpart of this JID or null.
     */
    Localpart getLocalpartOrNull();

    /**
     * Get the localpart of this JID or throw an {@code IllegalStateException}.
     * <p>
     * If the JID is of form {@code <localpart@domain.example>} then this method returns 'localpart'. If the JID has no
     * localpart, then <code>null</code> is returned.
     * </p>
     *
     * @return the localpart of this JID.
     */
    Localpart getLocalpartOrThrow();

    /**
     * Check if this JID is the parent of another JID. The <b>parent of</b> relation is defined, under the
     * precondition that the JID parts (localpart, domainpart and resourcepart) are equal, as follows:
     * <pre>
     * | this JID (parentOf) | other JID           | result |
     * |---------------------+---------------------+--------|
     * | dom.example         | dom.example         | true   |
     * | dom.example         | dom.example/res     | true   |
     * | dom.example         | loc@dom.example     | true   |
     * | dom.example         | loc@dom.example/res | true   |
     * | dom.example/res     | dom.exmple          | false  |
     * | dom.example/res     | dom.example/res     | true   |
     * | dom.example/res     | loc@dom.example     | false  |
     * | dom.example/res     | loc@dom.example/res | false  |
     * | loc@dom.example     | dom.example         | false  |
     * | loc@dom.example     | dom.example/res     | false  |
     * | loc@dom.example     | loc@dom.example     | true   |
     * | loc@dom.example     | loc@dom.example/res | true   |
     * | loc@dom.example/res | dom.example         | false  |
     * | loc@dom.example/res | dom.example/res     | false  |
     * | loc@dom.example/res | loc@dom.example     | false  |
     * | loc@dom.example/res | loc@dom.example/res | true   |
     * </pre>
     *
     * @param jid
     *            the other JID to compare with
     * @return true if this JID is a parent of the given JID.
     */
    boolean isParentOf(com.xabber.domain.entity.Jid jid);

    /**
     * See {@link #isParentOf(com.xabber.domain.entity.Jid)}.
     *
     * @param bareJid the bare JID.
     * @return true if this JID is a parent of the given JID.
     */
    boolean isParentOf(EntityBareJid bareJid);

    /**
     * See {@link #isParentOf(com.xabber.domain.entity.Jid)}.
     *
     * @param fullJid the full JID.
     * @return true if this JID is a parent of the given JID.
     */
    boolean isParentOf(EntityFullJid fullJid);

    /**
     * See {@link #isParentOf(com.xabber.domain.entity.Jid)}.
     *
     * @param domainBareJid the domain bare JID.
     * @return true if this JID is a parent of the given JID.
     */
    boolean isParentOf(DomainBareJid domainBareJid);

    /**
     * See {@link #isParentOf(com.xabber.domain.entity.Jid)}.
     *
     * @param domainFullJid the domain full JID.
     * @return true if this JID is a parent of the given JID.
     */
    boolean isParentOf(DomainFullJid domainFullJid);

    /**
     * Return the downcasted instance of this Jid. This method is unsafe, make sure to check that this is actually of the type of are casting to.
     *
     * @param <T> the Jid type to downcast to.
     * @return the downcasted instanced of this
     */
    <T extends com.xabber.domain.entity.Jid> T downcast();

    /**
     * Compares the given CharSequence with this JID. Returns true if {@code equals(charSequence.toString()} would
     * return true.
     *
     * @param charSequence the CharSequence to compare this JID with.
     * @return true if if {@code equals(charSequence.toString()} would return true.
     * @see #equals(String)
     */
    boolean equals(CharSequence charSequence);

    /**
     * Compares the given String wit this JID.
     * <p>
     * Returns true if {@code toString().equals(string)}, that is if the String representation of this JID matches the given string.
     * </p>
     *
     * @param string the String to compare this JID with.
     * @return true if {@code toString().equals(string)}.
     */
    boolean equals(String string);

    /**
     * Returns the canonical String representation of this JID. See {@link String#intern} for details.
     *
     * @return the canonical String representation.
     */
    String intern();
}

