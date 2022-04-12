package com.xabber.domain.entity;

public class Domainpart extends Part {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private Domainpart(String domain) {
        super(domain);
    }

    /**
     * Get the {@link com.xabber.domain.entity.Domainpart} representing the input String.
     *
     * @param domain the input String.
     * @return the domainpart.
     * @throws XmppStringprepException if an error occurs.
     */
    public static com.xabber.domain.entity.Domainpart from(String domain) throws XmppStringprepException {
        if (domain == null) {
            throw new XmppStringprepException(domain, "Input 'domain' must not be null");
        }
        // TODO cache
        // RFC 6122 § 2.2 "If the domainpart includes a final character considered to be a label
        // separator (dot) by [IDNA2003] or [DNS], this character MUST be stripped …"
        if (domain.length() > 0 && domain.charAt(domain.length() - 1) == '.') {
            domain = domain.substring(0, domain.length() - 1);
        }
        domain = XmppStringPrepUtil.domainprep(domain);
        // First prep the String, then assure the limits of the *result*
        assertNotLongerThan1023BytesOrEmpty(domain);
        return new com.xabber.domain.entity.Domainpart(domain);
    }
}

