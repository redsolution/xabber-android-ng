package com.xabber.domain.entity;

public interface XmppStringprep {

    /**
     * Performs String preparation on the localpart String of a JID. In RFC 6122 terms this means applying the
     * <i>nodeprep</i> profile of Stringprep.
     *
     * @param string the String to transform.
     * @return the prepared String.
     * @throws XmppStringprepException if there is an error.
     */
    String localprep(String string) throws XmppStringprepException;

    /**
     * Performs String preparation on the domainpart String of a JID. In RFC 61ss terms, this means applying the
     * <i>nameprep</i> profile of Stringprep.
     *
     * @param string the String to transform.
     * @return the prepared String.
     * @throws XmppStringprepException if there is an error.
     */
    String domainprep(String string) throws XmppStringprepException;

    /**
     * Performs String preparation on the resourcepart String of a JID. In RFC 6122 terms this means applying the <i>resourceprep</i> profile of Stringprep.
     * @param string the String to transform.
     * @return the prepared String.
     * @throws XmppStringprepException if there is an error.
     */
    String resourceprep(String string) throws XmppStringprepException;
}
