package com.xabber.domain.entity;

import java.io.IOException;

/**
 * XMPP Stringprep exceptions signal an error when performing a particular Stringprep profile on a String.
 */
public class XmppStringprepException extends IOException {

    /**
     *
     */
    private static final long serialVersionUID = -8491853210107124624L;

    private final String causingString;

    /**
     * Construct a new XMPP Stringprep exception with the given causing String and exception.
     *
     * @param causingString the String causing the exception.
     * @param exception the exception.
     */
    public XmppStringprepException(String causingString, Exception exception) {
        super("XmppStringprepException caused by '" + causingString + "': " + exception);
        initCause(exception);
        this.causingString = causingString;
    }

    /**
     * Construct a new XMPP Stringprep exception with the given causing String and exception message.
     *
     * @param causingString the String causing the exception.
     * @param message the message of the exception.
     */
    public XmppStringprepException(String causingString, String message) {
        super(message);
        this.causingString = causingString;
    }

    /**
     * Get the String causing the XMPP Stringprep exception.
     *
     * @return the causing String.
     */
    public String getCausingString() {
        return causingString;
    }
}

