package com.xabber.domain.entity;

/**
 * A <i>resourcepart</i> of an XMPP address (JID).
 * <p>
 * You can create instances of this class from Strings using {@link #from(String)}.
 * </p>
 *
 * @see <a href="http://xmpp.org/rfcs/rfc6122.html#addressing-resource">RFC 6122 § 2.4. Resourcepart</a>
 */
public class Resourcepart extends Part {

    /**
     * The empty resource part.
     * <p>
     * This empty resource part is the part that is represented by the empty String. This is useful in cases where you
     * have a collection of Resourceparts that does not allow <code>null</code> values, but you want to deal with the
     * "no resource" case.
     * </p>
     */
    public static final com.xabber.domain.entity.Resourcepart EMPTY = new com.xabber.domain.entity.Resourcepart("");
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private Resourcepart(String resource) {
        super(resource);
    }

    /**
     * Get the {@link com.xabber.domain.entity.Resourcepart} representing the input String.
     *
     * @param resource the input String.
     * @return the resource part.
     * @throws XmppStringprepException if an error occurs.
     */
    public static com.xabber.domain.entity.Resourcepart from(String resource) throws XmppStringprepException {
        resource = XmppStringPrepUtil.resourceprep(resource);
        // First prep the String, then assure the limits of the *result*
        assertNotLongerThan1023BytesOrEmpty(resource);
        return new com.xabber.domain.entity.Resourcepart(resource);
    }
}

