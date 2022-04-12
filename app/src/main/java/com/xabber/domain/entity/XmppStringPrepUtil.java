package com.xabber.domain.entity;

public class XmppStringPrepUtil {

    private static final Cache<String, String> NODEPREP_CACHE = new LruCache<String, String>(100);
    private static final Cache<String, String> DOMAINPREP_CACHE = new LruCache<String, String>(100);
    private static final Cache<String, String> RESOURCEPREP_CACHE = new LruCache<String, String>(100);
    private static XmppStringprep xmppStringprep;

    static {
        // Ensure that there is always at least the simple XMPP stringprep implementation active
        SimpleXmppStringprep.setup();
    }

    /**
     * Set the XMPP Stringprep implementation to use.
     *
     * @param xmppStringprep the XMPP Stringprep implementation to use.
     */
    public static void setXmppStringprep(XmppStringprep xmppStringprep) {
        com.xabber.domain.entity.XmppStringPrepUtil.xmppStringprep = xmppStringprep;
    }

    /**
     * Perform localprep on the input String.
     *
     * @param string the input String.
     * @return the localpreped String.
     * @throws XmppStringprepException if the input String can not be transformed.
     */
    public static String localprep(String string) throws XmppStringprepException {
        if (xmppStringprep == null) {
            return string;
        }
        // Avoid cache lookup if string is the empty string
        throwIfEmptyString(string);
        String res = NODEPREP_CACHE.lookup(string);
        if (res != null) {
            return res;
        }
        res = xmppStringprep.localprep(string);
        NODEPREP_CACHE.put(string, res);
        return res;
    }

    /**
     * Perform domainprep on the input String.
     *
     * @param string the input String.
     * @return the domainprep String.
     * @throws XmppStringprepException if the input String can not be transformed.
     */
    public static String domainprep(String string) throws XmppStringprepException {
        if (xmppStringprep == null) {
            return string;
        }
        // Avoid cache lookup if string is the empty string
        throwIfEmptyString(string);
        String res = DOMAINPREP_CACHE.lookup(string);
        if (res != null) {
            return res;
        }
        res = xmppStringprep.domainprep(string);
        DOMAINPREP_CACHE.put(string, res);
        return res;
    }

    /**
     * Perform resourceprep on the input String.
     *
     * @param string the input String.
     * @return the resourceprep String.
     * @throws XmppStringprepException if the input String can not be transformed.
     */
    public static String resourceprep(String string) throws XmppStringprepException {
        if (xmppStringprep == null) {
            return string;
        }
        // Avoid cache lookup if string is the empty string
        throwIfEmptyString(string);
        String res = RESOURCEPREP_CACHE.lookup(string);
        if (res != null) {
            return res;
        }
        res = xmppStringprep.resourceprep(string);
        RESOURCEPREP_CACHE.put(string, res);
        return res;
    }

    /**
     * Set the maximum cache sizes.
     *
     * @param size the maximum cache size.
     */
    public static void setMaxCacheSizes(int size) {
        NODEPREP_CACHE.setMaxCacheSize(size);
        DOMAINPREP_CACHE.setMaxCacheSize(size);
        RESOURCEPREP_CACHE.setMaxCacheSize(size);
    }

    /**
     * Throws a XMPP Stringprep exception if string is the empty string.
     *
     * @param string
     * @throws XmppStringprepException
     */
    private static void throwIfEmptyString(String string) throws XmppStringprepException {
        // TODO replace with string.isEmpty() once Smack's min Android SDK level is > 8
        if (string.length() == 0) {
            throw new XmppStringprepException(string, "Argument can't be the empty string");
        }
    }
}

