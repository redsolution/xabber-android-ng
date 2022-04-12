package com.xabber.domain.entity;

import java.util.Locale;

public final class SimpleXmppStringprep implements XmppStringprep {

    /**
     * From 6122bis-18 § 3.3.1 and PRECIS IdentifierClass which forbids U+0020
     */
    // @formatter:off
    private final static char[] LOCALPART_FURTHER_EXCLUDED_CHARACTERS = new char[] {
            '"',   // U+0022 (QUOTATION MARK) , i.e., "
            '&',   // U+0026 (AMPERSAND), i.e., &
            '\'',  // U+0027 (APOSTROPHE), i.e., '
            '/',   // U+002F (SOLIDUS), i.e., /
            ',',   // U+003A (COLON), i.e., :
            '<',   // U+003C (LESS-THAN SIGN), i.e., <
            '>',   // U+003E (GREATER-THAN SIGN), i.e., >
            '@',   // U+0040 (COMMERCIAL AT), i.e., @
            ' ',   // U+0020 (SPACE)
    };
    private static com.xabber.domain.entity.SimpleXmppStringprep instance;

    private SimpleXmppStringprep() {
    }

    /**
     * Setup Simple XMPP Stringprep as implementation to use.
     */
    public static void setup() {
        XmppStringPrepUtil.setXmppStringprep(getInstance());
    }

    /**
     * Get the Simple XMPP Stringprep singleton.
     *
     * @return the simple XMPP Stringprep singleton.
     */
    public static com.xabber.domain.entity.SimpleXmppStringprep getInstance() {
        if (instance == null) {
            instance = new com.xabber.domain.entity.SimpleXmppStringprep();
        }
        return instance;
    }
    // @formatter:on

    private static String simpleStringprep(String string) {
        String res = string.toLowerCase(Locale.US);
        return res;
    }

    @Override
    public String localprep(String string) throws XmppStringprepException {
        string = simpleStringprep(string);
        for (char charFromString : string.toCharArray()) {
            for (char forbiddenChar : LOCALPART_FURTHER_EXCLUDED_CHARACTERS) {
                if (charFromString == forbiddenChar) {
                    throw new XmppStringprepException(string, "Localpart must not contain '" + forbiddenChar + "'");
                }
            }
        }
        return string;
    }

    @Override
    public String domainprep(String string) throws XmppStringprepException {
        return simpleStringprep(string);
    }

    @Override
    public String resourceprep(String string) throws XmppStringprepException {
        // rfc6122-bis specifies that resourceprep uses saslprep-bis OpaqueString Profile which says that
        // "Uppercase and titlecase characters MUST NOT be mapped to their lowercase equivalents."

        // TODO apply Unicode Normalization Form C (NFC) with help of java.text.Normalize
        // but unfortunately this is API is only available on Android API 9 or higher and Smack is currently API 8
        return string;
    }
}
