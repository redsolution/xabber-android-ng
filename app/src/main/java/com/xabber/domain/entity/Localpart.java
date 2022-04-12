package com.xabber.domain.entity;

/**
 * A <i>localpart</i> of an XMPP address (JID). The localpart is the part before the
 * first @ sign in an XMPP address and usually identifies the user (or the XMPP
 * entity) within an XMPP service. It is also often referred to as "username",
 * but note that the actual username used to login may be different from the
 * resulting localpart of the user's JID.
 * <p>
 * You can create instances of this class from Strings using {@link #from(String)}.
 * </p>
 *
 * @see <a href="http://xmpp.org/rfcs/rfc6122.html#addressing-localpart">RFC
 *      6122 § 2.3. Localpart</a>
 */
public class Localpart extends Part {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private Localpart(String localpart) {
        super(localpart);
    }

    /**
     * Get the {@link com.xabber.domain.entity.Localpart} representing the input String.
     *
     * @param localpart the input String.
     * @return the localpart.
     * @throws XmppStringprepException if an error occurs.
     */
    public static com.xabber.domain.entity.Localpart from(String localpart) throws XmppStringprepException {
        localpart = XmppStringPrepUtil.localprep(localpart);
        // First prep the String, then assure the limits of the *result*
        assertNotLongerThan1023BytesOrEmpty(localpart);
        return new com.xabber.domain.entity.Localpart(localpart);
    }
}
